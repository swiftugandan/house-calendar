# Review: decisions taken while you were away

You asked me to set up emulators and carry on while you were away with the phone. This file lists every decision you didn't make yourself, so you can approve or reverse each one. Each entry says what was decided and why, and points to where it lives.

Status at hand-over (2026-10-10): all eight modules are built. Seven are verified on the emulators, and `:nearby` waits for two physical devices. Every app milestone in [PLAN.md](PLAN.md) §5 is done, and every acceptance item there is checked on the emulators. Screenshots are in [docs/screens/](docs/screens/).

## Decisions to review

### Specification

1. **Legibility tiers at 2 m and 0.6 m** (SPEC NFR-ACC, `e714d69`).
   - Display text has two tiers, sized by physical x-height rather than sp.
   - The **glance** tier (date, time, each person's now and next) is readable from the kitchen table: ≥ 7.0 mm.
   - The **detail** tier is readable standing at the wall: ≥ 2.1 mm.
   - Sizing everything for 2 m only fitted about two days on a 10" tablet.
2. **Edits are keyed registers, never series splits** (FR-15, FR-25, Example 5, `b65d80f`).
   - "This and following" appends a change-from record keyed `(event, from date)`. "Only this one" appends an occurrence edit keyed `(event, date)`.
   - Intervals are half-open.
   - A record missing a field it needs has no occurrences, and is never rejected (FR-25a).
   - Result: two phones editing the same series while apart can't create a duplicate series.
3. **Known limitation: "who" is one register.**
   - Per FR-42 it is a single field. If two phones, while apart, add *different* people to the same event, the later write wins rather than both being added.
   - I judged this rare enough for v1. The fix would be a per-member add/remove set, which is a contract amendment.
4. **Adding from other apps** (FR-49 to FR-56, Examples 13 and 14). This is your mid-task request, specified as:
   - "Add to calendar" insert intents, `.ics` files (viewed or shared), shared text, and a one-way copy of the next 8 weeks from the phone's calendars.
   - **Nothing is recorded without a person confirming it.**
   - An event's UID gives it a deterministic id, so importing the same file twice updates rather than duplicates.
   - An all-day file can go in as **holidays**, which is how a school's term-dates file becomes term time.
   - `RECURRENCE-ID` overrides and rules the calendar can't express (e.g. `BYSETPOS`) are not brought over, and the person is told so.

### Implementation

5. **The `:nearby` connect fix** (`82bb67b`).
   - House Points requests a connection only from the lower device id, which matched its dial rule.
   - Here the device *role* decides who dials (a phone dials the display), so the dialling side now always requests. Otherwise a phone dialling a display with a higher id would wait forever.
6. **Two sync bugs that also exist in House Points v0.2.3** (`f94d9b4`, `3ec883a`). Both were found by restarting the display emulator. **Consider porting both to House Points.**
   - **Sync session hang:** a blocking socket read ignored cancellation, so a dead session held the sync lock. Receive now closes the socket when cancelled.
   - **mDNS ordering:** a re-registered service's "found" can arrive before the old "lost", and discovery then dropped the live peer. A lost name is now re-resolved before the peer is dropped.
7. **`androidx.fragment` pinned to 1.8.5** (`gradle/libs.versions.toml`).
   - `biometric` 1.1.0 pulls in fragment 1.2.5, which crashed when it was given a result request code from activity 1.9 ("Can only use lower 16 bits for requestCode").
8. **Times use tabular Atkinson Hyperlegible Next, not Mono** (`5cdeeaa`, DESIGN.md updated).
   - In Mono, 12-hour times ("12:00 PM") were too wide and collided with the people discs.
9. **The phone's time column is measured, not fixed** (`1d8830a`).
   - It fits the widest time in the device's clock format at the current font size.
   - At 200% text the times move to a line above the entry (WCAG 1.4.10 reflow) instead of being clipped. The 200% screenshot caught the clipping.
10. **The display layout on a 10" tablet** (`938d8e6`):
    - one-line day labels
    - the header date measured, falling back to a short form
    - today's row weighted ×1.6
    - entries shrink to one line before "+N more" appears.
11. **Restore while joining.** The joining screen also offers "Restore from an export file", which brings a passphrase-locked export into a device that holds the family key but nothing else. This is built but **not yet exercised on a device**. Only export and re-import on an existing family was.
12. **Phone-calendar copies.**
    - Each occurrence comes in as a one-off. Repeating events aren't followed as series.
    - The id comes from the event's UID plus its day, so copying twice doesn't duplicate.
    - The calendar permission is requested only when you tap "Copy from this phone's calendars".

### Process

13. **`main` received the app commits early.** The socket fix branch was started from `module/app`, so merging it carried the app's first commits into `main`. Nothing was lost or rewritten. `module/app` was merged normally afterwards.
14. **`module/intake/refactored` sits on the green commit.** The refactor pass landed afterwards as `refactor(app,intake)` on `module/app`.

## What is verified where

| What | Where | How |
|---|---|---|
| `:contracts`, `:schedule`, `:intake`, `:sync` | JVM | Unit, property and spec-example suites. `:schedule` and `:intake` are pure, so their suites are their live boundary |
| `:data` | `hc_phone` emulator | Instrumented suite, 14 tests |
| `:lan` and `:sync`, live | Both emulators (shared virtual Wi-Fi) | Display joined by pairing code. A phone edit reached the display in 9.9 s (NFR-PERF-3 ≤ 15 s). Holiday and term-time edits converge on both |
| `:app` phone | `hc_phone` | Onboarding, agenda, add sheet, detail, each edit scope, needs someone and "I'll take", holidays with term time, intake (insert intent, shared text, `.ics` as holidays), export and re-import, a reminder firing on time with the next one rescheduled, dark mode, 200% text |
| `:app` display | `hc_tablet` | Week organiser, Today, Month, and the night screen (by setting the tablet's clock to 23:00) |
| `:nearby` | — | Framing and in-process session tests only |

## Waiting for hardware

- **Phone to display over your real Wi-Fi**, and OQ-1 (background discovery from the periodic worker on Android 12+).
- **Bluetooth fallback** (`:nearby`), which needs two physical devices.
- **OQ-2:** whether the kitchen tablet stays on and dims acceptably at night.
- **Performance on a real phone.** The emulator's cold start is about 12 s on a software GPU, which says nothing about a phone.
- **Exact alarms on your phone's OEM build**, which can differ from stock Android.
