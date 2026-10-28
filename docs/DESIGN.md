# Priority Matrix — Design & Architecture (Phase 1)

## 1. Product concept

Priority Matrix is an offline-first Android task manager built around the Eisenhower Matrix.
Every task is classified by **two independent switches** — *Penting (Important)* and
*Mendesak (Urgent)* — and the quadrant is derived from them:

| Quadrant | Important | Urgent | Action | Color |
|---|---|---|---|---|
| Q1 | ✔ | ✔ | Kerjakan Sekarang / Do First | Amber `#F59E0B` |
| Q2 | ✔ | ✘ | Jadwalkan / Schedule | Coral `#EF5B5B` |
| Q3 | ✘ | ✔ | Delegasikan / Delegate | Green `#22A06B` |
| Q4 | ✘ | ✘ | Hilangkan / Eliminate or Minimize | Blue `#3B82F6` |

Urgent never implies important: a task can be urgent and unimportant (Q3).
The app never changes these switches on its own — Smart Prioritization only *suggests*
and every reclassification needs the user's confirmation.

## 2. Design principles

* **Matrix is the identity.** The 2×2 grid is the first thing on the dashboard, and quadrant
  colors are reused everywhere a task appears (left color bar, dots in the calendar,
  chart segments).
* **Neutral canvas, colored signals.** Backgrounds are near-white / near-black; color is
  reserved for meaning (quadrant, overdue = error red text, done = muted).
* **Two taps to capture.** `+ Tambah Tugas` → type title → toggle Penting/Mendesak → Save.
  Everything else is optional and lives behind "Detail lengkap".
* **Honest numbers.** Task counts and tracked hours are always labelled separately.
  Time charts use only recorded focus sessions and manual time logs.
* Material 3, rounded 20–24 dp cards, 16 dp gutters, 1 dp tonal elevation,
  spring animations for quadrant changes and completion.

## 3. Information architecture

```
Bottom navigation
├── Beranda (Dashboard)
│   ├── Greeting · date · 4 summary stats
│   ├── Eisenhower Matrix 2×2  ──► Quadrant task list
│   │                         └─► Matrix Board (drag & drop between quadrants)
│   ├── Top 3 Hari Ini (Daily Planning) ──► Planning screen
│   ├── Prioritas Hari Ini · Tenggat Mendatang · Aktivitas Strategis (Q2) · Baru Selesai
│   └── FAB "+ Tambah Tugas" ──► Quick-add bottom sheet ──► Full editor
├── Tugas (Tasks): search · filters · sort · Active/Completed/Archived · swipe · bulk
├── Kalender: Month / Week / Day timeline · time blocks · add task on a date
├── Analitik: range filter · counts · completion rates · trends · time per quadrant · Q2 vs Q1
│   └── Weekly Review
└── Pengaturan: profile · language · theme · notifications · Pomodoro · data (CSV/backup/demo)

Secondary (top-bar actions on Dashboard)
├── Focus Timer (Pomodoro, linked to a task)
├── Projects & categories ──► Project dashboard
└── Matrix Board
```

## 4. Main screen layouts

**Dashboard** — `LargeTopAppBar` style header ("Selamat pagi, <nama>" + date),
a row of four stat pills, then the matrix: four equal cards (aspect ≈ 1:1.05), each with
quadrant number badge, action label, active count (large numeral), overdue badge,
thin progress bar (completed ÷ total in quadrant) and up to two task previews.
Below: Top-3 card, then compact sections with "Lihat semua" links.

**Quick add sheet** — title field (auto-focus, IME action = save), two large toggle
cards (Penting / Mendesak) side by side, live quadrant chip that animates color,
deadline chips (Tanpa / Hari ini / Besok / Pilih…), project chip, "Detail lengkap" and "Simpan".

**Task editor** — sections: Klasifikasi, Jadwal (start, due date, due time, recurrence,
reminders), Detail (priority, project, status, assignee, tags, estimate), Subtugas,
Catatan, Waktu tercatat, Saran cerdas.

**Calendar** — segmented Month/Week/Day. Month cells show up to 3 quadrant dots.
Day view is a 06:00–23:00 timeline with time blocks (tinted by quadrant) and timed tasks.

**Analytics** — range chips (Hari ini / Minggu ini / Bulan ini / Kustom), then cards:
task counts per quadrant (donut, labelled "jumlah tugas"), completion rate,
completed per day (bar), 6-month trend (line), estimate vs actual, tracked time per
quadrant (donut, labelled "jam tercatat"), Q2 vs Q1 comparison bar, rule-based insights.

## 5. Color system

| Token | Light | Dark |
|---|---|---|
| Background | `#F6F7F9` | `#101216` |
| Surface | `#FFFFFF` | `#1A1D23` |
| Primary (ink indigo) | `#3B4A86` | `#B8C3FF` |
| Q1 / container | `#F59E0B` / `#FEF1D6` | `#FBBF24` / `#3A2A0A` |
| Q2 / container | `#EF5B5B` / `#FDE3E3` | `#FF8A80` / `#3D1A1A` |
| Q3 / container | `#22A06B` / `#DDF4E8` | `#4ADE80` / `#11301F` |
| Q4 / container | `#3B82F6` / `#DFEBFE` | `#7AB0FF` / `#13233F` |

Text on containers uses the dark ink of each hue (≥ 4.5:1 contrast).

## 6. Technical architecture

```
app/src/main/java/com/prioritymatrix/app
├── domain/          pure Kotlin: models, Quadrant rules, deadlines, recurrence,
│                    AnalyticsCalculator, RecommendationEngine, SmartPrioritizer, CsvCodec
├── data/
│   ├── db/          Room entities, DAOs, converters, AppDatabase, migrations
│   ├── repository/  TaskRepository, ProjectRepository, TimeRepository, PlanningRepository
│   ├── prefs/       DataStore SettingsRepository
│   ├── backup/      JSON backup/restore, CSV import/export
│   └── demo/        DemoDataSeeder (all rows flagged isDemo)
├── notifications/   channels, ReminderScheduler (AlarmManager), receivers, WorkManager workers
├── focus/           FocusTimerManager (timestamp based), FocusTimerService (foreground)
├── di/              Hilt modules
├── navigation/      routes + NavHost + bottom bar
└── ui/              theme, i18n, components, one package per screen (MVVM ViewModels)
```

* **MVVM + unidirectional data flow:** Room `Flow` → repository → `ViewModel` (`StateFlow`
  via `stateIn`) → Compose.
* **Reminders:** exact alarms when permitted (`SCHEDULE_EXACT_ALARM`), otherwise
  inexact `setAndAllowWhileIdle`; rescheduled after reboot / app update.
* **Periodic jobs:** WorkManager for daily planning, weekly review and overdue checks.
* **Focus timer:** end time stored as wall-clock timestamp (persisted), so the remaining
  time is always computed, never counted down — accurate in background and after
  process death. A foreground service keeps the notification live and an alarm guarantees
  the end-of-session alert.
* **Safety of data:** no destructive migrations, schema exported, validation in the
  editor, restore runs in a single transaction after confirmation.
