# Implementation plan

Modules are listed in dependency order. Each one follows the cycle in `ENGINEERING_DISCIPLINE.md`: red → green → refactor → e2e-mock → live, with tags `module/<name>/<phase>`.

```
                      :contracts  (frozen 0.1.0)
                 ▲        ▲          ▲
           :schedule   :intake     :sync
                 ▲        ▲       ▲   ▲   ▲
                 │        │   :data :lan :nearby
                 │        │       ▲   ▲   ▲
                 └──────── :app ──┴───┴───┘
```

There are no cycles. `:schedule`, `:intake` and `:sync` are independent of each other, and only `:app` combines them.

**Order as built (recorded 2026-10-10).** `:schedule`, `:sync`, `:data`, `:lan` and `:nearby` were built in DAG order. `:intake` comes after `:app` milestones 1 to 6 and before milestone 7, which is the first one that consumes it. The DAG allows this, and it gets a running app onto the emulators sooner.

## 1. `:schedule` (pure JVM)

- **Depends on:** `:contracts`.
- **Consumes:** `List<Op>`, a window of `LocalDate`s, a `ZoneId`, and "now" as a parameter.
- **Produces:** `CalendarState`, `Agenda`, `Clashes`, `NeedsSomeone`, `ReminderPlan`, `Suggestions`, `EditPlanner`, `Recording`.
- **Acceptance (live e2e at its boundary):**
  - SPEC Examples 1 to 12 pass as tests that feed ops into `Projection` and read `Agenda`.
  - **Property tests:**
    - Any permutation or duplication of the op list gives an identical state.
    - `fold(ops) == fold(prefix).plus(rest)` (ADR-12).
    - Splitting a window never changes the agenda (NFR-DET-4).
    - A log with injected `task.*`/`meal.*` ops gives the same agenda (NFR-EXT-1).
  - **Benchmark:** the sizing log (≈ 60k ops) projects and resolves within budget on the JVM. It is reported in CI, not gating.
- **Live peers:** none. It is a pure function, so e2e-mock and live are the same suite.

## 2. `:intake` (pure JVM)

- **Depends on:** `:contracts`.
- **Consumes:** bytes, intent-extra maps and text, plus "now" and the family zone as parameters.
- **Produces:** `IntakeResult` with `Draft`s and skip counts.
- **Acceptance:**
  - The intake halves of SPEC Examples 13 and 14: drafts, the holiday offer, UID-derived ids, and timezone conversion.
  - Fixture `.ics` files: a school term file, a Google export, an Outlook invite, a folded and escaped file, an RRULE with `COUNT`, an unmappable RRULE, and one with `EXDATE`.
  - Every prefix of every fixture parses without throwing (FR-56).
  - The size and count bounds are enforced.
  - The `DateTimeHints` table of phrases resolves as listed.

## 3. `:sync` (pure JVM, copied)

- **Depends on:** `:contracts`.
- **Produces:** `OpFactory`, `VersionVector`, `SyncSession`, `Dialing`, the ports.
- **Acceptance:**
  - House Points' sync tests pass under the new package and label.
  - **Dialing truth table:** display/phone, phone/phone and display/display.
  - Three in-memory devices with random calendar ops and random pairwise syncs end with identical op sets *and* identical `CalendarState`s (NFR-DET-2, using `:schedule` in the test only).

## 4. `:data` (Android library, copied plus `DeviceSettingsStore`)

- **Depends on:** `:contracts`, `:sync`.
- **Acceptance (instrumented, on the `hc_phone` emulator):**
  - The copied `SqliteOpLog`, vault and export tests pass.
  - `DeviceSettingsStore` round-trips and survives a corrupt file.
  - An export imported again gives the same op set.

## 4a. `:lan` (Android library, copied)

- **Depends on:** `:sync`.
- **Acceptance:**
  - Socket framing tests.
  - The address rule.
  - The role is carried in TXT.
  - **Live:** the two emulators share a virtual Wi-Fi network (10.0.2.16 and 10.0.2.17), so mDNS and TCP between them work. Phone-to-display sync was verified that way. The desktop peer over `adb forward` turned out to be unnecessary.
  - **Real Wi-Fi:** your router's multicast behaviour still needs real devices.

## 4b. `:nearby` (Android library, copied)

- **Acceptance:** framing tests, plus a session over the paired in-process transport. Live verification needs two physical devices (deferred).

## 5. `:app` (Android application)

- **Depends on:** everything.
- **Milestones**, each red-first for its pure parts (view models, formatters, mappers):
  1. **Foundations:**
     - theme tokens (with `TokensContrastTest`), fonts, icons and keyword suggestions
     - `CalendarRepository`
     - onboarding: create a family, add members, choose this device's role.
  2. **Phone core:** the Agenda, the add sheet with suggestions and clash preview, event detail, and edit and delete with the scope question.
  3. **Phone views:** Week and Month, the member filter, Needs someone, holidays, Recently deleted.
  4. **Display mode:**
     - the Week organiser, Today board, Month and the night screen
     - idle return, day rollover, physical type and the size check
     - settings gate and burn-in shift.
  5. **Sync:** pairing by QR or code, the Sync screen, `SyncController` with roles, and `SyncWorker`.
  6. **Reminders:** `ReminderScheduler`, receivers, notifications and permissions.
  7. **Intake:** `IntakeActivity` for every intent filter, the import review, and phone calendars.
  8. **Data:** export and import, diagnostics, and family and device settings.
- **Acceptance:**
  - Installed on the emulators, a family is created on the phone and a tablet joins as a display.
  - Swimming is added with term time and a holiday, and the agenda and organiser match `:schedule`.
  - Each edit scope is exercised.
  - A `.ics` file and an "Add to calendar" intent come in through intake.
  - A reminder fires.
  - Screenshots are taken of the phone (light, dark, 200% text) and the tablet (Week, Today, night).

## Verification environment

- **Emulators:** `hc_phone` (Pixel 7, API 35) and `hc_tablet` (10.1" WXGA 1280×800, mdpi, API 35).
  - They run **headless** (`-no-window -gpu swiftshader_indirect`), because the host's WindowServer has a crash history.
  - They are driven with `adb` (`input`, `am`, `screencap`).
- **Live sync:** emulator to emulator over their shared virtual Wi-Fi (see §4a).

## Deferred (recorded, not forgotten)

- Phone-to-display sync over real Wi-Fi, and OQ-1 (background NSD), both need physical devices on one network. The protocol itself is verified emulator to emulator.
- `:nearby` live verification needs two physical devices.
- On-device performance timing needs a physical phone.

---

## Confidence gate

```
Confidence: high
Why: the DAG is acyclic and matches SAD §2. Every module has a boundary acceptance test. The pure modules
  carry all the date, instant and person logic, and the platform modules are copied, verified adapters
  behind ports. The emulators make every :app milestone checkable without the user's phone.
Residual risks:
  - The emulators can't exercise multicast between devices or Nearby, which are tracked above.
  - Exact-alarm and background-work behaviour on OEM builds differs from the emulator.
Known gaps: none blocking implementation.
```
