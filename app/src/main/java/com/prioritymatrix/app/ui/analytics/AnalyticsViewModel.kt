package com.prioritymatrix.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prioritymatrix.app.data.repository.TaskRepository
import com.prioritymatrix.app.data.repository.TimeRepository
import com.prioritymatrix.app.domain.logic.AnalyticsCalculator
import com.prioritymatrix.app.domain.logic.AnalyticsReport
import com.prioritymatrix.app.domain.logic.Deadlines
import com.prioritymatrix.app.domain.logic.Insight
import com.prioritymatrix.app.domain.logic.RecommendationEngine
import com.prioritymatrix.app.domain.model.DateRange
import com.prioritymatrix.app.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class RangeKind { TODAY, WEEK, MONTH, CUSTOM }

data class AnalyticsUi(
    val kind: RangeKind = RangeKind.WEEK,
    val report: AnalyticsReport? = null,
    val insights: List<Insight> = emptyList()
)

fun rangeFor(kind: RangeKind, today: LocalDate, custom: DateRange?): DateRange = when (kind) {
    RangeKind.TODAY -> DateRange(today, today)
    RangeKind.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DateRange(it, it.plusDays(6)) }
    RangeKind.MONTH -> DateRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
    RangeKind.CUSTOM -> custom ?: DateRange(today.minusDays(29), today)
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    tasks: TaskRepository,
    time: TimeRepository,
    private val clock: Clock
) : ViewModel() {
    private val kind = MutableStateFlow(RangeKind.WEEK)
    private val custom = MutableStateFlow<DateRange?>(null)

    val state: StateFlow<AnalyticsUi> = combine(tasks.tasks, time.entries, kind, custom) { all, entries, k, c ->
        val now = LocalDateTime.now(clock)
        val range = rangeFor(k, now.toLocalDate(), c)
        val report = AnalyticsCalculator.calculate(all, entries, range, now, clock.zone)
        AnalyticsUi(k, report, RecommendationEngine.insights(report, all.filter { it.isActive }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUi())

    fun setKind(k: RangeKind) { kind.value = k }
    fun setCustom(start: LocalDate, end: LocalDate) {
        custom.value = if (end.isBefore(start)) DateRange(end, start) else DateRange(start, end)
        kind.value = RangeKind.CUSTOM
    }
}

data class WeeklyReviewUi(
    val weekOffset: Int = 0,
    val report: AnalyticsReport? = null,
    val completed: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now()
)

@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    tasks: TaskRepository,
    time: TimeRepository,
    private val clock: Clock
) : ViewModel() {
    private val offset = MutableStateFlow(0)

    val state: StateFlow<WeeklyReviewUi> = combine(tasks.tasks, time.entries, offset) { all, entries, off ->
        val now = LocalDateTime.now(clock)
        val start = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(off.toLong())
        val range = DateRange(start, start.plusDays(6))
        val report = AnalyticsCalculator.calculate(all, entries, range, now, clock.zone)
        val zone = clock.zone
        WeeklyReviewUi(
            weekOffset = off,
            report = report,
            completed = all.filter { t -> t.isCompleted && t.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() in range } == true }
                .sortedByDescending { it.completedAt },
            overdue = all.filter { Deadlines.isOverdue(it, now) }.sortedBy { it.dueDate },
            insights = RecommendationEngine.insights(report, all.filter { it.isActive }),
            now = now
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyReviewUi())

    fun shift(step: Int) { offset.value = (offset.value + step).coerceAtMost(0) }
}
