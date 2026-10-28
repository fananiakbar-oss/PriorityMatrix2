package com.prioritymatrix.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.prioritymatrix.app.data.prefs.AppLanguage
import com.prioritymatrix.app.data.prefs.ThemeMode
import com.prioritymatrix.app.domain.logic.Insight
import com.prioritymatrix.app.domain.logic.InsightType
import com.prioritymatrix.app.domain.logic.ProposedChange
import com.prioritymatrix.app.domain.logic.Reason
import com.prioritymatrix.app.domain.logic.RecommendationEngine
import com.prioritymatrix.app.domain.logic.SmartPrioritizer
import com.prioritymatrix.app.domain.logic.SuggestedAction
import com.prioritymatrix.app.domain.logic.ValidationError
import com.prioritymatrix.app.domain.model.Priority
import com.prioritymatrix.app.domain.model.Quadrant
import com.prioritymatrix.app.domain.model.Recurrence
import com.prioritymatrix.app.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * All user-facing text. Bahasa Indonesia is the default; English is optional.
 * Both languages live side by side so a missing translation is impossible.
 */
class AppStrings(val language: AppLanguage) {
    private val en = language == AppLanguage.EN
    private fun t(id: String, en: String) = if (this.en) en else id

    val locale: Locale = if (en) Locale.ENGLISH else Locale.forLanguageTag("id-ID")

    // ---------- Navigation ----------
    val navDashboard = t("Beranda", "Dashboard")
    val navTasks = t("Tugas", "Tasks")
    val navCalendar = t("Kalender", "Calendar")
    val navAnalytics = t("Analitik", "Analytics")
    val navSettings = t("Pengaturan", "Settings")
    val focusTimer = t("Timer Fokus", "Focus Timer")
    val projects = t("Proyek & Kategori", "Projects & Categories")
    val matrixBoard = t("Papan Matriks", "Matrix Board")
    val dailyPlanning = t("Rencana Harian", "Daily Planning")
    val weeklyReview = t("Tinjauan Mingguan", "Weekly Review")
    val back = t("Kembali", "Back")

    // ---------- Common ----------
    val addTask = t("Tambah Tugas", "Add Task")
    val save = t("Simpan", "Save")
    val cancel = t("Batal", "Cancel")
    val delete = t("Hapus", "Delete")
    val edit = t("Ubah", "Edit")
    val done = t("Selesai", "Done")
    val undo = t("Urungkan", "Undo")
    val close = t("Tutup", "Close")
    val confirm = t("Konfirmasi", "Confirm")
    val apply = t("Terapkan", "Apply")
    val seeAll = t("Lihat semua", "See all")
    val search = t("Cari tugas…", "Search tasks…")
    val none = t("Tidak ada", "None")
    val all = t("Semua", "All")
    val today = t("Hari ini", "Today")
    val tomorrow = t("Besok", "Tomorrow")
    val yesterday = t("Kemarin", "Yesterday")
    val noDeadline = t("Tanpa tenggat", "No deadline")
    val pickDate = t("Pilih tanggal", "Pick date")
    val pickTime = t("Pilih jam", "Pick time")
    val clear = t("Hapus pilihan", "Clear")
    val demoBadge = "DEMO"
    val minutesShort = t("mnt", "min")
    val select = t("Pilih", "Select")
    val selected = t("dipilih", "selected")
    val more = t("Lainnya", "More")
    val important = t("Penting", "Important")
    val urgent = t("Mendesak", "Urgent")
    val notImportant = t("Tidak penting", "Not important")
    val notUrgent = t("Tidak mendesak", "Not urgent")

    // ---------- Quadrants ----------
    fun quadrantTitle(q: Quadrant) = when (q) {
        Quadrant.DO_FIRST -> t("Penting & Mendesak", "Important & Urgent")
        Quadrant.SCHEDULE -> t("Penting, Tidak Mendesak", "Important, Not Urgent")
        Quadrant.DELEGATE -> t("Tidak Penting, Mendesak", "Not Important, Urgent")
        Quadrant.ELIMINATE -> t("Tidak Penting & Tidak Mendesak", "Not Important & Not Urgent")
    }

    fun quadrantAction(q: Quadrant) = when (q) {
        Quadrant.DO_FIRST -> t("Kerjakan Sekarang", "Do First")
        Quadrant.SCHEDULE -> t("Jadwalkan", "Schedule")
        Quadrant.DELEGATE -> t("Delegasikan", "Delegate")
        Quadrant.ELIMINATE -> t("Hilangkan / Kurangi", "Eliminate / Minimize")
    }

    fun quadrantHint(q: Quadrant) = when (q) {
        Quadrant.DO_FIRST -> t("Krisis, tenggat dekat, masalah mendesak.", "Crises, close deadlines, pressing problems.")
        Quadrant.SCHEDULE -> t("Perencanaan, pencegahan risiko, belajar, relasi.", "Planning, risk prevention, learning, relationships.")
        Quadrant.DELEGATE -> t("Interupsi, sebagian rapat & email.", "Interruptions, some meetings & emails.")
        Quadrant.ELIMINATE -> t("Pengalih perhatian dan aktivitas pembuang waktu.", "Distractions and time wasters.")
    }

    fun quadrantShort(q: Quadrant) = "Q${q.number}"

    val unlinkedTime = t("Tanpa tugas", "No task")

    // ---------- Dashboard ----------
    fun greeting(hour: Int, name: String): String {
        val g = when (hour) {
            in 4..10 -> t("Selamat pagi", "Good morning")
            in 11..14 -> t("Selamat siang", "Good afternoon")
            in 15..17 -> t("Selamat sore", "Good afternoon")
            else -> t("Selamat malam", "Good evening")
        }
        return if (name.isBlank()) g else "$g, ${name.trim()}"
    }

    val statActive = t("Aktif", "Active")
    val statDueToday = t("Hari ini", "Due today")
    val statOverdue = t("Terlambat", "Overdue")
    val statCompleted = t("Selesai hari ini", "Done today")
    val eisenhowerMatrix = t("Matriks Eisenhower", "Eisenhower Matrix")
    val dragHint = t("Tahan & seret tugas ke kuadran lain", "Long-press and drag a task to another quadrant")
    val top3Today = t("Top 3 Hari Ini", "Today's Top 3")
    val planYourDay = t("Rencanakan hari Anda", "Plan your day")
    val planYourDayBody = t("Pilih tiga tugas terpenting untuk diselesaikan hari ini.", "Pick the three most important tasks to finish today.")
    val todaysPriorities = t("Prioritas Hari Ini", "Today's Priorities")
    val upcomingDeadlines = t("Tenggat Mendatang", "Upcoming Deadlines")
    val strategicActivities = t("Aktivitas Strategis (Q2)", "Strategic Activities (Q2)")
    val recentlyCompleted = t("Baru Selesai", "Recently Completed")
    val nothingToday = t("Tidak ada tugas jatuh tempo hari ini. Waktu yang baik untuk aktivitas Q2.", "Nothing due today. A good moment for Q2 work.")
    val nothingUpcoming = t("Tidak ada tenggat dalam 7 hari ke depan.", "No deadlines in the next 7 days.")
    val nothingStrategic = t("Belum ada tugas strategis. Tambahkan perencanaan, belajar, atau pencegahan risiko.", "No strategic tasks yet. Add planning, learning or risk prevention.")
    val nothingCompleted = t("Belum ada tugas yang selesai.", "No completed tasks yet.")
    fun overdueCount(n: Int) = t("$n terlambat", "$n overdue")
    fun activeCount(n: Int) = t("$n aktif", "$n active")
    fun doneOf(done: Int, total: Int) = t("$done/$total selesai", "$done/$total done")
    val emptyQuadrant = t("Kosong", "Empty")
    val welcomeTitle = t("Selamat datang di Priority Matrix", "Welcome to Priority Matrix")
    val welcomeBody = t("Mulai dengan menambahkan tugas pertama, atau muat data contoh untuk melihat cara kerjanya.", "Start by adding your first task, or load sample data to see how it works.")
    val loadDemo = t("Muat data contoh", "Load sample data")

    // ---------- Tasks list ----------
    val tabActive = t("Aktif", "Active")
    val tabCompleted = t("Selesai", "Completed")
    val tabArchived = t("Arsip", "Archived")
    val filters = t("Filter", "Filters")
    val sort = t("Urutkan", "Sort")
    val sortDue = t("Tenggat", "Deadline")
    val sortPriority = t("Prioritas", "Priority")
    val sortQuadrant = t("Kuadran", "Quadrant")
    val sortCreated = t("Terbaru dibuat", "Newest")
    val sortTitle = t("Judul (A–Z)", "Title (A–Z)")
    val sortSmart = t("Skor perhatian", "Attention score")
    val filterQuadrant = t("Kuadran", "Quadrant")
    val filterDeadline = t("Tenggat", "Deadline")
    val filterProject = t("Proyek", "Project")
    val filterStatus = t("Status", "Status")
    val filterPriority = t("Prioritas", "Priority")
    val filterAssignee = t("Penanggung jawab", "Assignee")
    val deadlineOverdue = t("Terlambat", "Overdue")
    val deadlineToday = t("Hari ini", "Today")
    val deadlineWeek = t("7 hari ke depan", "Next 7 days")
    val deadlineNone = t("Tanpa tenggat", "No deadline")
    val resetFilters = t("Atur ulang filter", "Reset filters")
    val noTasksTitle = t("Belum ada tugas", "No tasks yet")
    val noTasksBody = t("Ketuk + Tambah Tugas untuk mulai mengatur prioritas.", "Tap + Add Task to start prioritising.")
    val noResultsTitle = t("Tidak ada hasil", "No results")
    val noResultsBody = t("Coba ubah kata kunci atau filter.", "Try a different keyword or filter.")
    val markComplete = t("Tandai selesai", "Mark complete")
    val markIncomplete = t("Tandai belum selesai", "Mark incomplete")
    val moveTo = t("Pindahkan ke…", "Move to…")
    val archive = t("Arsipkan", "Archive")
    val unarchive = t("Kembalikan dari arsip", "Unarchive")
    val duplicate = t("Duplikat", "Duplicate")
    val reschedule = t("Jadwal ulang", "Reschedule")
    val copySuffix = t(" (salinan)", " (copy)")
    fun nSelected(n: Int) = t("$n dipilih", "$n selected")
    val selectAll = t("Pilih semua", "Select all")

    // ---------- Snackbars ----------
    val taskCompleted = t("Tugas selesai 🎉", "Task completed 🎉")
    val taskReopened = t("Tugas dibuka kembali", "Task reopened")
    fun tasksDeleted(n: Int) = t("$n tugas dihapus", "$n task(s) deleted")
    fun tasksMoved(n: Int, q: Quadrant) = t("$n tugas dipindah ke ${quadrantShort(q)} · ${quadrantAction(q)}", "$n task(s) moved to ${quadrantShort(q)} · ${quadrantAction(q)}")
    fun tasksArchived(n: Int) = t("$n tugas diarsipkan", "$n task(s) archived")
    val taskSaved = t("Tugas disimpan", "Task saved")
    val taskDuplicated = t("Tugas diduplikat", "Task duplicated")
    val rescheduled = t("Tenggat diperbarui", "Deadline updated")
    fun nextRecurring(date: String) = t("Berulang: tugas berikutnya dibuat untuk $date", "Recurring: next task created for $date")
    val errorGeneric = t("Terjadi kesalahan. Data Anda tetap aman.", "Something went wrong. Your data is safe.")

    // ---------- Confirm dialogs ----------
    fun confirmDeleteTitle(n: Int) = t("Hapus $n tugas?", "Delete $n task(s)?")
    val confirmDeleteBody = t("Tugas dan subtugasnya akan dihapus. Anda bisa mengurungkan sesaat setelahnya.", "Tasks and their subtasks will be deleted. You can undo right after.")
    val confirmReclassTitle = t("Ubah klasifikasi?", "Change classification?")

    // ---------- Quick add / editor ----------
    val quickAddTitle = t("Tugas baru", "New task")
    val titleHint = t("Apa yang perlu dikerjakan?", "What needs to be done?")
    val moreDetails = t("Detail lengkap", "Full details")
    val newTask = t("Tugas Baru", "New Task")
    val editTask = t("Ubah Tugas", "Edit Task")
    val classification = t("Klasifikasi", "Classification")
    val importantDesc = t("Berkontribusi pada tujuan jangka panjang", "Contributes to long-term goals")
    val urgentDesc = t("Butuh perhatian segera / tenggat dekat", "Needs attention now / close deadline")
    val schedule = t("Jadwal", "Schedule")
    val details = t("Detail", "Details")
    val description = t("Deskripsi (opsional)", "Description (optional)")
    val startDate = t("Tanggal mulai", "Start date")
    val dueDate = t("Tanggal tenggat", "Due date")
    val dueTime = t("Jam tenggat", "Due time")
    val estimate = t("Estimasi durasi (menit)", "Estimated duration (min)")
    val priority = t("Prioritas", "Priority")
    val status = t("Status", "Status")
    val project = t("Proyek / kategori", "Project / category")
    val noProject = t("Tanpa proyek", "No project")
    val tags = t("Tag (pisahkan dengan koma)", "Tags (comma separated)")
    val assignee = t("Penanggung jawab (opsional)", "Assigned to (optional)")
    val notes = t("Catatan", "Notes")
    val recurrence = t("Pengulangan", "Repeat")
    val reminders = t("Pengingat", "Reminders")
    val subtasks = t("Subtugas", "Subtasks")
    val addSubtask = t("Tambah subtugas", "Add subtask")
    val trackedTime = t("Waktu tercatat", "Tracked time")
    val logTime = t("Catat waktu manual", "Log time manually")
    val logTimeMinutes = t("Menit dikerjakan", "Minutes worked")
    val startFocus = t("Mulai fokus", "Start focus")
    val smartSuggestion = t("Saran Cerdas", "Smart Suggestion")
    val smartNote = t("Saran berdasarkan aturan sederhana. Klasifikasi tidak pernah diubah tanpa persetujuan Anda.", "Rule-based suggestion. Your classification is never changed without your approval.")
    val unsavedChanges = t("Buang perubahan?", "Discard changes?")
    val discard = t("Buang", "Discard")
    val keepEditing = t("Lanjut mengubah", "Keep editing")
    val noTimeLogged = t("Belum ada waktu tercatat. Gunakan Timer Fokus atau catat manual.", "No time recorded yet. Use the Focus Timer or log it manually.")
    fun estimateVsActual(est: Int?, act: Int) = if (est != null) t("Estimasi ${formatMinutes(est)} · Aktual ${formatMinutes(act)}", "Estimate ${formatMinutes(est)} · Actual ${formatMinutes(act)}") else t("Aktual ${formatMinutes(act)}", "Actual ${formatMinutes(act)}")

    fun validation(e: ValidationError) = when (e) {
        ValidationError.TITLE_EMPTY -> t("Judul tugas wajib diisi.", "A title is required.")
        ValidationError.TITLE_TOO_LONG -> t("Judul maksimal 200 karakter.", "Title can be at most 200 characters.")
        ValidationError.START_AFTER_DUE -> t("Tanggal mulai tidak boleh setelah tenggat.", "Start date can't be after the due date.")
        ValidationError.TIME_WITHOUT_DATE -> t("Pilih tanggal tenggat sebelum jam.", "Pick a due date before setting a time.")
        ValidationError.ESTIMATE_INVALID -> t("Estimasi harus angka positif.", "Estimate must be a positive number.")
        ValidationError.REMINDER_WITHOUT_DATE -> t("Pengingat membutuhkan tanggal tenggat.", "Reminders need a due date.")
    }

    fun priorityLabel(p: Priority) = when (p) {
        Priority.LOW -> t("Rendah", "Low")
        Priority.MEDIUM -> t("Sedang", "Medium")
        Priority.HIGH -> t("Tinggi", "High")
        Priority.CRITICAL -> t("Kritis", "Critical")
    }

    fun statusLabel(s: TaskStatus) = when (s) {
        TaskStatus.NOT_STARTED -> t("Belum mulai", "Not started")
        TaskStatus.IN_PROGRESS -> t("Sedang dikerjakan", "In progress")
        TaskStatus.ON_HOLD -> t("Ditunda", "On hold")
        TaskStatus.COMPLETED -> t("Selesai", "Completed")
    }

    fun recurrenceLabel(r: Recurrence) = when (r) {
        Recurrence.NONE -> t("Tidak berulang", "Does not repeat")
        Recurrence.DAILY -> t("Setiap hari", "Daily")
        Recurrence.WEEKDAYS -> t("Hari kerja (Sen–Jum)", "Weekdays (Mon–Fri)")
        Recurrence.WEEKLY -> t("Setiap minggu", "Weekly")
        Recurrence.MONTHLY -> t("Setiap bulan", "Monthly")
        Recurrence.YEARLY -> t("Setiap tahun", "Yearly")
    }

    fun reminderLabel(offset: Int) = when {
        offset == 0 -> t("Saat tenggat", "At deadline")
        offset < 60 -> t("$offset menit sebelum", "$offset min before")
        offset < 1440 -> t("${offset / 60} jam sebelum", "${offset / 60} h before")
        else -> t("${offset / 1440} hari sebelum", "${offset / 1440} day(s) before")
    }

    val reminderNoTimeNote = t("Tanpa jam tenggat, pengingat dihitung dari pukul 09.00.", "Without a due time, reminders are counted from 09:00.")

    // ---------- Smart prioritization ----------
    fun suggestedAction(a: SuggestedAction) = when (a) {
        SuggestedAction.DO_NOW -> t("Kerjakan sekarang", "Do it now")
        SuggestedAction.SCHEDULE -> t("Jadwalkan waktu khusus", "Schedule dedicated time")
        SuggestedAction.DELEGATE -> t("Delegasikan", "Delegate it")
        SuggestedAction.MINIMIZE -> t("Kurangi atau hilangkan", "Minimise or drop it")
        SuggestedAction.NONE -> t("Tidak ada tindakan", "No action needed")
    }

    fun reason(r: Reason, days: Long?) = when (r) {
        Reason.COMPLETED -> t("Tugas sudah selesai.", "Task is already done.")
        Reason.OVERDUE_IMPORTANT -> t("Melewati tenggat dan ditandai penting.", "Past its deadline and marked important.")
        Reason.OVERDUE_NOT_IMPORTANT -> t("Melewati tenggat tetapi tidak penting — pertimbangkan delegasi atau hapus.", "Past its deadline but not important — consider delegating or dropping it.")
        Reason.IMPORTANT_AND_URGENT -> t("Penting dan mendesak.", "Important and urgent.")
        Reason.IMPORTANT_DUE_SOON_NOT_MARKED_URGENT -> t("Penting dan tenggat tinggal ${days ?: 0} hari (≤ ${SmartPrioritizer.DUE_SOON_DAYS}), tetapi belum ditandai mendesak.", "Important and due in ${days ?: 0} day(s) (≤ ${SmartPrioritizer.DUE_SOON_DAYS}), but not marked urgent.")
        Reason.IMPORTANT_NOT_URGENT -> t("Penting tetapi tidak mendesak — lindungi waktunya di kalender.", "Important but not urgent — protect time for it in your calendar.")
        Reason.Q2_WITHOUT_DATE -> t("Belum punya tanggal mulai atau tenggat.", "Has no start or due date yet.")
        Reason.URGENT_NOT_IMPORTANT -> t("Mendesak tetapi tidak penting bagi tujuan Anda.", "Urgent but not important for your goals.")
        Reason.Q3_WITHOUT_ASSIGNEE -> t("Belum ada penanggung jawab.", "No one is assigned yet.")
        Reason.NEITHER -> t("Tidak penting dan tidak mendesak.", "Neither important nor urgent.")
        Reason.Q4_STALE -> t("Tanpa tenggat dan dibuat ≥ ${SmartPrioritizer.STALE_DAYS} hari lalu.", "No deadline and created ≥ ${SmartPrioritizer.STALE_DAYS} days ago.")
        Reason.URGENT_BUT_FAR_DEADLINE -> t("Ditandai mendesak, padahal tenggat masih ${days ?: 0} hari lagi (> ${SmartPrioritizer.FAR_DEADLINE_DAYS}).", "Marked urgent while the deadline is ${days ?: 0} days away (> ${SmartPrioritizer.FAR_DEADLINE_DAYS}).")
    }

    fun proposedChange(c: ProposedChange) = when (c) {
        ProposedChange.MARK_URGENT -> t("Tandai sebagai mendesak", "Mark as urgent")
        ProposedChange.MARK_NOT_URGENT -> t("Tandai tidak mendesak", "Mark as not urgent")
        ProposedChange.ARCHIVE -> t("Arsipkan tugas ini", "Archive this task")
    }

    fun proposedChangeConfirm(c: ProposedChange) = when (c) {
        ProposedChange.MARK_URGENT -> t("Tugas akan ditandai mendesak dan pindah ke kuadran baru. Lanjutkan?", "The task will be marked urgent and move to a new quadrant. Continue?")
        ProposedChange.MARK_NOT_URGENT -> t("Tugas akan ditandai tidak mendesak dan pindah ke kuadran baru. Lanjutkan?", "The task will be marked not urgent and move to a new quadrant. Continue?")
        ProposedChange.ARCHIVE -> t("Tugas akan diarsipkan. Anda bisa mengembalikannya dari tab Arsip.", "The task will be archived. You can restore it from the Archived tab.")
    }

    // ---------- Calendar ----------
    val month = t("Bulan", "Month")
    val week = t("Minggu", "Week")
    val day = t("Hari", "Day")
    val timeBlock = t("Blok waktu", "Time block")
    val addTimeBlock = t("Blok waktu", "Time block")
    val newTimeBlock = t("Blok waktu baru", "New time block")
    val blockTitle = t("Judul blok", "Block title")
    val blockStart = t("Mulai", "Start")
    val blockEnd = t("Selesai", "End")
    val blockLinkTask = t("Kaitkan dengan tugas (opsional)", "Link to a task (optional)")
    val blockColor = t("Warna kuadran", "Quadrant colour")
    val blockHint = t("Lindungi waktu untuk aktivitas Q2: perencanaan, belajar, pencegahan risiko.", "Protect time for Q2 work: planning, learning, risk prevention.")
    val blockInvalid = t("Jam selesai harus setelah jam mulai.", "End time must be after start time.")
    val noTasksOnDay = t("Tidak ada tugas atau blok waktu pada tanggal ini.", "No tasks or time blocks on this date.")
    val moveDeadline = t("Pindahkan tenggat", "Move deadline")
    val allDay = t("Sepanjang hari", "All day")
    val goToday = t("Ke hari ini", "Go to today")

    // ---------- Analytics ----------
    val rangeToday = t("Hari ini", "Today")
    val rangeWeek = t("Minggu ini", "This week")
    val rangeMonth = t("Bulan ini", "This month")
    val rangeCustom = t("Kustom", "Custom")
    val taskCountsTitle = t("Jumlah tugas aktif per kuadran", "Active task count per quadrant")
    val taskCountsNote = t("Satuan: jumlah tugas (bukan jam).", "Unit: number of tasks (not hours).")
    val completedInRange = t("Selesai dalam periode", "Completed in period")
    val overdueNow = t("Terlambat saat ini", "Overdue now")
    val completionRate = t("Tingkat penyelesaian", "Completion rate")
    val completionRateNote = t("Tugas yang tenggatnya jatuh dalam periode dan sudah selesai ÷ semua tugas yang jatuh tempo pada periode itu.", "Tasks due in the period that are done ÷ all tasks due in that period.")
    val todayRate = t("Hari ini", "Today")
    val weekRate = t("Minggu ini", "This week")
    val noDueData = t("Tidak ada tugas jatuh tempo", "Nothing was due")
    val completedPerDay = t("Tugas selesai per hari", "Tasks completed per day")
    val completedPerWeek = t("Tugas selesai per minggu", "Tasks completed per week")
    val monthlyTrend = t("Tren 6 bulan", "6-month trend")
    val trendCompleted = t("Tugas selesai", "Tasks completed")
    val trendHours = t("Jam tercatat", "Tracked hours")
    val estimateVsActualTitle = t("Estimasi vs aktual", "Estimated vs actual")
    fun estimateBasis(n: Int) = t("Berdasarkan $n tugas selesai yang punya estimasi & waktu tercatat.", "Based on $n completed task(s) with an estimate.")
    val estimated = t("Estimasi", "Estimated")
    val actual = t("Aktual", "Actual")
    val timeAllocation = t("Alokasi waktu tercatat per kuadran", "Tracked time allocation per quadrant")
    val timeAllocationNote = t("Satuan: jam tercatat dari Timer Fokus & log manual.", "Unit: hours recorded via Focus Timer & manual logs.")
    val strategicVsUrgent = t("Strategis (Q2) vs Mendesak (Q1)", "Strategic (Q2) vs Urgent (Q1)")
    val insights = t("Wawasan & Rekomendasi", "Insights & Recommendations")
    val noTrackedYet = t("Belum ada waktu tercatat pada periode ini.", "No time recorded in this period.")
    fun focusSessions(n: Int) = t("$n sesi fokus", "$n focus session(s)")
    val totalTracked = t("Total tercatat", "Total tracked")

    fun insightTitle(i: Insight) = when (i.type) {
        InsightType.NO_TRACKED_TIME -> t("Catat waktu untuk wawasan yang akurat", "Track time for accurate insights")
        InsightType.URGENT_DOMINATES -> t("Waktu Anda didominasi urusan mendesak", "Urgent work dominates your time")
        InsightType.STRONG_STRATEGIC_FOCUS -> t("Fokus strategis yang baik", "Strong strategic focus")
        InsightType.LOW_VALUE_TIME -> t("Banyak waktu untuk aktivitas bernilai rendah", "Lots of time on low-value work")
        InsightType.OVERDUE_TASKS -> t("Ada tugas yang terlambat", "You have overdue tasks")
        InsightType.UNSCHEDULED_Q2 -> t("Tugas Q2 belum dijadwalkan", "Q2 tasks aren't scheduled")
        InsightType.LOW_COMPLETION -> t("Tingkat penyelesaian rendah", "Low completion rate")
        InsightType.ESTIMATE_OVERRUN -> t("Estimasi sering meleset", "Estimates are often exceeded")
    }

    fun insightBody(i: Insight) = when (i.type) {
        InsightType.NO_TRACKED_TIME -> t("Baru ${formatMinutes(i.values[0])} tercatat. Gunakan Timer Fokus agar alokasi waktu per kuadran bisa dihitung.", "Only ${formatMinutes(i.values[0])} recorded. Use the Focus Timer so time per quadrant can be measured.")
        InsightType.URGENT_DOMINATES -> t("${i.values[0]}% waktu tercatat untuk Q1, hanya ${i.values[1]}% untuk Q2. Blok waktu untuk perencanaan dan pencegahan risiko agar krisis berkurang.", "${i.values[0]}% of tracked time went to Q1 and only ${i.values[1]}% to Q2. Block time for planning and risk prevention to reduce crises.")
        InsightType.STRONG_STRATEGIC_FOCUS -> t("${i.values[0]}% waktu tercatat untuk aktivitas Q2. Pertahankan!", "${i.values[0]}% of tracked time went to Q2 work. Keep it up!")
        InsightType.LOW_VALUE_TIME -> t("${i.values[0]}% waktu tercatat untuk Q3 + Q4. Delegasikan atau kurangi aktivitas tersebut.", "${i.values[0]}% of tracked time went to Q3 + Q4. Delegate or reduce them.")
        InsightType.OVERDUE_TASKS -> t("${i.values[0]} tugas melewati tenggat. Selesaikan, jadwal ulang, atau delegasikan.", "${i.values[0]} task(s) are past due. Finish, reschedule or delegate them.")
        InsightType.UNSCHEDULED_Q2 -> t("${i.values[0]} tugas penting tanpa tanggal. Pilih waktu di kalender agar tidak terlupakan.", "${i.values[0]} important task(s) have no date. Put them in your calendar.")
        InsightType.LOW_COMPLETION -> t("Hanya ${i.values[0]}% dari ${i.values[1]} tugas yang jatuh tempo telah selesai. Pertimbangkan mengurangi komitmen.", "Only ${i.values[0]}% of ${i.values[1]} due tasks were completed. Consider committing to less.")
        InsightType.ESTIMATE_OVERRUN -> t("Estimasi ${formatMinutes(i.values[0])}, aktual ${formatMinutes(i.values[1])}. Tambahkan cadangan waktu saat merencanakan.", "Estimated ${formatMinutes(i.values[0])}, actual ${formatMinutes(i.values[1])}. Add buffer when planning.")
    }

    fun insightRule(i: Insight) = when (i.type) {
        InsightType.NO_TRACKED_TIME -> t("Aturan: total waktu tercatat < ${RecommendationEngine.MIN_TRACKED_MINUTES} menit.", "Rule: total tracked time < ${RecommendationEngine.MIN_TRACKED_MINUTES} min.")
        InsightType.URGENT_DOMINATES -> t("Aturan: Q1 > 50% dan Q2 < 25% dari waktu tercatat.", "Rule: Q1 > 50% and Q2 < 25% of tracked time.")
        InsightType.STRONG_STRATEGIC_FOCUS -> t("Aturan: Q2 ≥ 40% dari waktu tercatat.", "Rule: Q2 ≥ 40% of tracked time.")
        InsightType.LOW_VALUE_TIME -> t("Aturan: Q3 + Q4 > 40% dari waktu tercatat.", "Rule: Q3 + Q4 > 40% of tracked time.")
        InsightType.OVERDUE_TASKS -> t("Aturan: ada tugas aktif yang melewati tenggat.", "Rule: at least one active task is past due.")
        InsightType.UNSCHEDULED_Q2 -> t("Aturan: ≥ ${RecommendationEngine.UNSCHEDULED_Q2_MIN} tugas Q2 tanpa tanggal mulai/tenggat.", "Rule: ≥ ${RecommendationEngine.UNSCHEDULED_Q2_MIN} Q2 tasks without start/due date.")
        InsightType.LOW_COMPLETION -> t("Aturan: ≥ ${RecommendationEngine.LOW_COMPLETION_MIN_DUE} tugas jatuh tempo dan < 50% selesai.", "Rule: ≥ ${RecommendationEngine.LOW_COMPLETION_MIN_DUE} tasks due and < 50% completed.")
        InsightType.ESTIMATE_OVERRUN -> t("Aturan: waktu aktual > 125% estimasi (min. 2 tugas).", "Rule: actual time > 125% of estimate (min. 2 tasks).")
    }

    // ---------- Planning & review ----------
    val chooseTop3 = t("Pilih maksimal 3 tugas terpenting hari ini", "Choose up to 3 most important tasks for today")
    val suggestedForYou = t("Disarankan (skor perhatian tertinggi)", "Suggested (highest attention score)")
    val useSuggestion = t("Gunakan saran", "Use suggestion")
    val otherActiveTasks = t("Tugas aktif lainnya", "Other active tasks")
    val scoreExplain = t("Skor = bobot kuadran (Q1 40, Q2 30, Q3 20, Q4 10) + terlambat 30 / hari ini 20 / ≤3 hari 10 + prioritas × 5.", "Score = quadrant weight (Q1 40, Q2 30, Q3 20, Q4 10) + overdue 30 / today 20 / ≤3 days 10 + priority × 5.")
    fun top3Progress(done: Int, total: Int) = t("$done dari $total selesai", "$done of $total done")
    val top3Max = t("Maksimal 3 tugas.", "Up to 3 tasks.")
    val reviewSummary = t("Ringkasan minggu ini", "This week's summary")
    val reviewCompleted = t("Tugas selesai", "Completed tasks")
    val reviewOverdue = t("Aktivitas terlambat", "Overdue activities")
    val reviewTimeAllocation = t("Alokasi waktu", "Time allocation")
    val reviewNext = t("Langkah untuk minggu depan", "Steps for next week")
    val reviewNothingCompleted = t("Belum ada tugas selesai minggu ini.", "No tasks completed this week.")
    val reviewNoOverdue = t("Tidak ada tugas terlambat. Bagus!", "No overdue tasks. Nice!")
    val reviewNoInsights = t("Belum ada rekomendasi — terus catat aktivitas Anda.", "No recommendations yet — keep recording your work.")

    // ---------- Focus ----------
    val phaseFocus = t("Fokus", "Focus")
    val phaseShortBreak = t("Istirahat singkat", "Short break")
    val phaseLongBreak = t("Istirahat panjang", "Long break")
    val start = t("Mulai", "Start")
    val pause = t("Jeda", "Pause")
    val resume = t("Lanjutkan", "Resume")
    val stop = t("Berhenti", "Stop")
    val skip = t("Lewati", "Skip")
    val focusOn = t("Fokus pada", "Focusing on")
    val chooseTask = t("Pilih tugas", "Choose task")
    val noTaskLinked = t("Tanpa tugas (waktu tetap tercatat)", "No task (time still recorded)")
    fun sessionsDone(n: Int) = t("$n sesi fokus selesai hari ini", "$n focus session(s) today")
    val focusBackgroundNote = t("Timer tetap akurat saat aplikasi di latar belakang.", "The timer stays accurate in the background.")
    val stopSaveTitle = t("Hentikan sesi?", "Stop session?")
    fun stopSaveBody(min: Int) = t("$min menit kerja akan dicatat ke analitik.", "$min minute(s) of work will be recorded in analytics.")
    val stopDiscard = t("Hentikan tanpa mencatat", "Stop without recording")
    val stopAndSave = t("Catat & hentikan", "Record & stop")
    val todaysFocus = t("Fokus hari ini", "Focus today")

    // ---------- Projects ----------
    val newProject = t("Proyek baru", "New project")
    val editProject = t("Ubah proyek", "Edit project")
    val projectName = t("Nama proyek / kategori", "Project / category name")
    val color = t("Warna", "Colour")
    val icon = t("Ikon", "Icon")
    val noProjects = t("Belum ada proyek. Buat proyek untuk memisahkan pekerjaan dan urusan pribadi.", "No projects yet. Create one to separate work and personal life.")
    fun projectStats(active: Int, done: Int) = t("$active aktif · $done selesai", "$active active · $done done")
    val deleteProject = t("Hapus proyek?", "Delete project?")
    val deleteProjectBody = t("Tugas di dalamnya tidak dihapus, hanya dilepas dari proyek.", "Its tasks are kept, only unlinked from the project.")
    val distribution = t("Distribusi matriks", "Matrix distribution")
    val projectTasks = t("Tugas dalam proyek", "Tasks in project")
    val defaultCategories: List<Pair<String, String>> = listOf(
        t("Pekerjaan", "Work") to "work",
        t("Pribadi", "Personal") to "person",
        t("Keluarga", "Family") to "family",
        t("Kesehatan", "Health") to "health",
        t("Pembelajaran", "Learning") to "school"
    )

    // ---------- Settings ----------
    val profile = t("Profil", "Profile")
    val yourName = t("Nama Anda", "Your name")
    val appearance = t("Tampilan", "Appearance")
    val languageLabel = t("Bahasa", "Language")
    val theme = t("Tema", "Theme")
    fun themeLabel(m: ThemeMode) = when (m) {
        ThemeMode.SYSTEM -> t("Ikuti sistem", "System")
        ThemeMode.LIGHT -> t("Terang", "Light")
        ThemeMode.DARK -> t("Gelap", "Dark")
    }
    fun languageName(l: AppLanguage) = when (l) {
        AppLanguage.ID -> "Bahasa Indonesia"
        AppLanguage.EN -> "English"
    }
    val notifications = t("Notifikasi", "Notifications")
    val deadlineReminders = t("Pengingat tenggat", "Deadline reminders")
    val defaultReminder = t("Pengingat bawaan untuk tugas baru", "Default reminder for new tasks")
    val overdueAlerts = t("Notifikasi tugas terlambat", "Overdue task notifications")
    val dailyPlanningReminder = t("Pengingat rencana harian", "Daily planning reminder")
    val weeklyReviewReminder = t("Pengingat tinjauan mingguan", "Weekly review reminder")
    val reviewDay = t("Hari tinjauan", "Review day")
    val time = t("Jam", "Time")
    val notificationsDisabled = t("Notifikasi dinonaktifkan untuk aplikasi ini.", "Notifications are disabled for this app.")
    val allow = t("Izinkan", "Allow")
    val exactAlarmOff = t("Pengingat tepat waktu belum diizinkan; pengingat mungkin terlambat beberapa menit.", "Exact reminders aren't allowed; reminders may arrive a few minutes late.")
    val openSettings = t("Buka pengaturan", "Open settings")
    val pomodoro = t("Pomodoro", "Pomodoro")
    val focusLength = t("Durasi fokus", "Focus length")
    val shortBreakLength = t("Istirahat singkat", "Short break")
    val longBreakLength = t("Istirahat panjang", "Long break")
    val sessionsBeforeLong = t("Sesi sebelum istirahat panjang", "Sessions before long break")
    val autoStartNext = t("Mulai fase berikutnya otomatis", "Auto-start next phase")
    val data = t("Data", "Data")
    val exportCsv = t("Ekspor tugas (CSV)", "Export tasks (CSV)")
    val importCsv = t("Impor tugas (CSV)", "Import tasks (CSV)")
    val backup = t("Cadangkan semua data (JSON)", "Back up all data (JSON)")
    val restore = t("Pulihkan dari cadangan", "Restore from backup")
    val restoreConfirmTitle = t("Pulihkan cadangan?", "Restore backup?")
    val restoreConfirmBody = t("Semua data saat ini akan diganti dengan isi file cadangan. Buat cadangan terlebih dahulu jika ragu.", "All current data will be replaced by the backup file. Make a backup first if unsure.")
    val demoData = t("Data contoh", "Sample data")
    val demoLoadedInfo = t("Data contoh aktif dan ditandai dengan lencana DEMO.", "Sample data is loaded and marked with a DEMO badge.")
    val removeDemo = t("Hapus data contoh", "Remove sample data")
    val about = t("Tentang", "About")
    val aboutBody = t("Priority Matrix bekerja sepenuhnya offline. Semua data tersimpan hanya di perangkat ini.", "Priority Matrix works fully offline. All data stays on this device.")
    val exportDone = t("Ekspor berhasil disimpan", "Export saved")
    fun importDone(n: Int, skipped: Int) = t("$n tugas diimpor, $skipped dilewati", "$n task(s) imported, $skipped skipped")
    val restoreDone = t("Data berhasil dipulihkan", "Data restored")
    val restoreFailed = t("File cadangan tidak valid. Tidak ada data yang diubah.", "Invalid backup file. Nothing was changed.")
    val importFailed = t("File CSV tidak valid. Pastikan ada kolom 'title'.", "Invalid CSV. Make sure it has a 'title' column.")
    val demoLoaded = t("Data contoh dimuat", "Sample data loaded")
    val demoRemoved = t("Data contoh dihapus", "Sample data removed")
    fun minutesValue(m: Int) = t("$m menit", "$m min")
    fun sessionsValue(n: Int) = t("$n sesi", "$n sessions")

    // ---------- Notifications ----------
    val channelReminders = t("Pengingat tugas", "Task reminders")
    val channelOverdue = t("Tugas terlambat", "Overdue tasks")
    val channelPlanning = t("Perencanaan & tinjauan", "Planning & review")
    val channelFocus = t("Timer fokus", "Focus timer")
    fun notifDueIn(label: String) = t("Tenggat: $label", "Due: $label")
    val notifOverdueTitle = t("Tugas terlambat", "Task overdue")
    val notifSnooze = t("Tunda 10 mnt", "Snooze 10 min")
    val notifPlanningTitle = t("Rencanakan hari Anda", "Plan your day")
    fun notifPlanningBody(dueToday: Int, overdue: Int) = t("$dueToday tugas jatuh tempo hari ini, $overdue terlambat. Pilih Top 3 Anda.", "$dueToday task(s) due today, $overdue overdue. Pick your Top 3.")
    val notifReviewTitle = t("Waktunya tinjauan mingguan", "Time for your weekly review")
    fun notifReviewBody(done: Int, q2Pct: Int) = t("$done tugas selesai minggu ini · $q2Pct% waktu untuk Q2.", "$done task(s) done this week · $q2Pct% of time on Q2.")
    val notifFocusRunning = t("Sesi fokus berjalan", "Focus session running")
    val notifBreakRunning = t("Istirahat", "Break")
    val notifFocusDone = t("Sesi fokus selesai! Saatnya istirahat.", "Focus session done! Time for a break.")
    val notifBreakDone = t("Istirahat selesai. Siap fokus lagi?", "Break over. Ready to focus again?")
    val notifPaused = t("Dijeda", "Paused")

    // ---------- Formatting ----------
    fun formatMinutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> t("$m mnt", "$m min")
            m == 0 -> t("$h j", "$h h")
            else -> t("$h j $m mnt", "$h h $m min")
        }
    }

    fun formatHours(min: Int): String = String.format(locale, "%.1f %s", min / 60f, t("jam", "h"))

    fun formatDate(date: LocalDate, pattern: String = "EEE, d MMM yyyy"): String =
        date.format(DateTimeFormatter.ofPattern(pattern, locale))

    fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm", locale))

    fun formatMinuteOfDay(minute: Int): String = formatTime(LocalTime.of(minute / 60 % 24, minute % 60))

    fun longDate(date: LocalDate): String = formatDate(date, "EEEE, d MMMM yyyy")

    fun monthTitle(date: LocalDate): String = formatDate(date, "MMMM yyyy")

    fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, locale)

    fun dayFull(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, locale)

    fun dueLabel(date: LocalDate, reference: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(reference, date)
        return when {
            diff == 0L -> this.today
            diff == 1L -> tomorrow
            diff == -1L -> yesterday
            diff in 2..6 -> t("$diff hari lagi", "in $diff days")
            diff < -1 -> t("Terlambat ${-diff} hari", "${-diff} days overdue")
            else -> formatDate(date, "d MMM")
        }
    }

}

val LocalStrings = staticCompositionLocalOf { AppStrings(AppLanguage.ID) }
