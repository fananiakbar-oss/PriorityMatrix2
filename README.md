# Priority Matrix

An offline Android task manager built around the **Eisenhower Matrix** — Kotlin, Jetpack Compose,
Material 3, MVVM, Room, DataStore, Hilt, Coroutines/Flow and WorkManager. Default language
**Bahasa Indonesia**, optional **English** (Settings → Appearance → Language).

| Q | Criteria | Action | Colour |
|---|---|---|---|
| 1 | Important + Urgent | Kerjakan Sekarang / Do First | Amber |
| 2 | Important, not Urgent | Jadwalkan / Schedule | Coral |
| 3 | Not Important, Urgent | Delegasikan / Delegate | Green |
| 4 | Neither | Hilangkan / Eliminate | Blue |

See `docs/DESIGN.md` for the UI/UX concept, information architecture, palette and architecture.

## Features

* **Dashboard** – greeting, date, stats (active / due today / overdue / done today), interactive 2×2 matrix
  (active count, overdue badge, completion progress, top task previews), Top 3 today, Today's priorities,
  Upcoming deadlines, Strategic (Q2) activities, Recently completed, `+ Tambah Tugas` FAB.
* **Matrix Board** – long-press and drag tasks between quadrants (or use the ⇄ button), with undo.
* **Tasks** – search, filters (quadrant, deadline, project, status, priority, assignee), sort,
  Active/Completed/Archived tabs, swipe right = complete, swipe left = delete (undo), long-press for bulk
  complete / move / archive / delete, per-task menu (move, reschedule, duplicate, archive, delete).
* **Quick add sheet** – title + two switches (Penting / Mendesak) with live quadrant preview, deadline chips,
  project chip, "Full details" expands into the editor.
* **Editor** – description, start/due date, due time, recurrence, multiple reminders, priority, status,
  project, estimate, assignee, tags, subtasks, notes, tracked time (focus + manual logs), validation,
  Smart Suggestion card (rule-based; changes only after you confirm).
* **Calendar** – month / week / day timeline, quadrant colour dots, move deadlines, add tasks on a date,
  **time blocks** for protected Q2 work.
* **Analytics** – today / week / month / custom range; task counts per quadrant (donut), completion rates,
  completed per day/week (bars), 6-month trend (line), estimate vs actual, **tracked hours per quadrant**,
  Q2 vs Q1 comparison, transparent rule-based insights (each shows its rule).
* **Daily Planning** (Top 3) and **Weekly Review**.
* **Focus Timer** – Pomodoro 25/5/15 (customisable), linked to a task, pause/resume/skip/stop, accurate in the
  background (timestamp-based + foreground service + alarm), sessions recorded for analytics.
* **Projects & categories** – Work, Personal, Family, Health, Learning by default; custom colours/icons;
  project dashboard with completion rate and matrix distribution.
* **Notifications** – deadline reminders (exact alarms when allowed), overdue alerts, daily planning and
  weekly review reminders, snooze/done actions, rescheduled after reboot.
* **Data** – fully offline; CSV export/import; full JSON backup/restore (validated, transactional);
  optional demo data flagged with a DEMO badge and removable in one tap.

---

## Build the APK from an Android phone (no laptop)

GitHub Actions builds the APK in the cloud for free. You only need a browser.

### One-time setup (≈5 minutes)

1. **Create a GitHub account** at <https://github.com/signup> (Chrome on your phone works fine).
2. **Create a repository**: tap your avatar → *Your repositories* → **New**. Name it `PriorityMatrix`,
   choose *Private* or *Public*, and tick **Add a README file** (the repository must not be empty).
   Tap **Create repository**.
3. **Add the build workflow** (GitHub cannot unpack this file from a zip for security reasons, so it is
   created by hand once):
   * In the repository tap **Add file → Create new file**.
   * File name: type exactly `.github/workflows/build.yml`
     (typing the `/` creates the folders).
   * Paste the full content of `build.yml` (provided next to the zip, identical to
     `.github/workflows/build.yml` inside it).
   * Tap **Commit changes…** → **Commit changes**.
   * The first run will finish quickly with a "Project files not found yet" warning — that is expected.
4. **Upload the project**: tap **Add file → Upload files** → choose `PriorityMatrix.zip` from your Downloads
   → **Commit changes**. Keep the file name `PriorityMatrix.zip`.

The workflow now unpacks the zip, commits the project files to your repository, builds the APK and runs the
tests. It takes roughly 8–15 minutes the first time (later builds are faster thanks to caching).

> Tip: if the GitHub page looks cramped, use Chrome's menu → **Desktop site**.

### Download and install the APK

* **Easiest:** open the repository's **Releases** (on the main page, or `https://github.com/<you>/PriorityMatrix/releases`)
  → *Priority Matrix – latest debug build* → tap **PriorityMatrix-debug.apk**.
* **Alternative:** **Actions** tab → latest *Build APK* run → **Artifacts** → `PriorityMatrix-debug-apk`
  (a zip; open it with the Files app and tap the APK inside).

When installing, Android asks to allow installs from your browser/Files app ("Install unknown apps") —
allow it once. Updates install over the previous version and keep your data, because every build is signed
with the same key (`keystore/debug.keystore`).

### Rebuilding after changes

* Edit any file on github.com (open the file → ✏️ → *Commit changes*): every commit triggers a new build.
* Or trigger manually: **Actions → Build APK → Run workflow**.
* If a build fails, open the run, tap the red step and copy the error lines — paste them into a chat and I
  can fix them.

### Option B: Termux (full git on the phone)

```sh
pkg install git unzip
termux-setup-storage
cd ~ && unzip ~/storage/downloads/PriorityMatrix.zip -d PriorityMatrix && cd PriorityMatrix
git init -b main && git add . && git commit -m "Priority Matrix"
git remote add origin https://github.com/<you>/PriorityMatrix.git
git push -u origin main   # use a GitHub personal access token as the password
```

---

## Build on a computer (optional)

* Android Studio Ladybug (2024.2) or newer, JDK 17.
* Open the folder, let Gradle sync, press **Run** — or from a terminal:

```sh
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit + Robolectric tests
```

Toolchain: AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21, Compose BOM 2024.12.01, compileSdk/targetSdk 35,
minSdk 26 (Android 8.0).

## Tests

`app/src/test` (JVM, no device needed):

* `QuadrantClassificationTest` – two independent criteria → quadrant.
* `DeadlineTest` – overdue rules, reminder trigger times, recurrence, validation.
* `AnalyticsCalculatorTest` – counts vs tracked hours, completion rates, buckets, trends, insight rules.
* `SmartPrioritizerTest` – suggestions never mutate tasks, transparent score.
* `CsvTest`, `TaskFilteringTest`.
* `TaskPersistenceTest` (Robolectric + in-memory Room) – save/update/delete/restore, recurring completion,
  quadrant moves, tracked time aggregation, JSON backup/restore and CSV round-trips.

## Notes

* **Permissions**: notifications (Android 13+ asks at first use), exact alarms (Android 12+; if not allowed,
  reminders still arrive but may be a few minutes late), foreground service for the focus timer.
* Some phone brands (Xiaomi, Oppo, Vivo, Samsung "deep sleep") stop background apps aggressively. If reminders
  are late, set Priority Matrix to *Unrestricted* battery usage.
* The release build is also signed with the bundled debug key so it is installable for personal use.
  Create your own keystore before publishing on Google Play.
* Database schema exports go to `app/schemas/`. Future schema changes must add a `Migration` in
  `data/db/AppDatabase.kt`; destructive migration is intentionally disabled.

## Project structure

```
app/src/main/java/com/prioritymatrix/app
├── domain/        models + pure logic (Deadlines, AnalyticsCalculator, RecommendationEngine,
│                  SmartPrioritizer, TaskValidator, CsvCodec, TaskCsvMapper)
├── data/          db (Room), repository, prefs (DataStore), backup, demo
├── notifications/ alarms, receivers, WorkManager workers
├── focus/         FocusTimerManager, FocusTimerService
├── di/            Hilt modules
├── i18n/          AppStrings (Indonesian + English)
├── navigation/    routes, navigator, root scaffold
└── ui/            theme, components, dashboard, matrix, tasks, task, calendar, analytics,
                   planning, focus, projects, settings
```
