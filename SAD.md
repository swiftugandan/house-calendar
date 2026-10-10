# Software Architecture Document

This document turns the requirements in `SPEC.md` (tag `spec/v0.1.0`) into components, contracts and decisions. Requirement IDs (FR-x, NFR-x) refer to that specification.

---

## 1. The 30,000-ft view: context

```
            ┌──────────────────────────── trust boundary: the family ─────────────────────────────┐
            │                                                                                       │
  Parent ──▶│  House Calendar (phone A) ◀── home Wi-Fi: mDNS + TCP (or Bluetooth via Nearby) ──▶ House Calendar (display D) │◀── anyone in the kitchen
            │        ▲                                                                    ▲         │
            │        └───────────────────────── the same, phone ⇄ phone ──────────────────┘         │
            └────────┼─────────────────────────────────────────────────────────────────────────────┘
                     │
                     ├──▶ Other apps on the phone: "Add to calendar" intents, .ics files, shared text  [R21]  (in only)
                     ├──▶ The phone's calendar provider (READ_CALENDAR, one-off copies)                 [R21]  (in only)
                     ├──▶ AlarmManager (exact reminders), WorkManager (background sync)                 [R6, R11]
                     ├──▶ Android Auto Backup (user-enabled; E2E-encrypted on API 28+ with a screen lock) [R4]
                     └──▶ Google Play Services (Nearby Connections, code scanner): on-device system services
```

**External actors.**
- **Parents** operate phones.
- **Anyone in the kitchen** reads and edits the display. They are never authenticated users.
- **Other apps** hand drafts in. They are untrusted sources of partial data.
- **Peer devices** run House Calendar in the same family.

**Trust boundaries.**
1. **Between devices:** anything arriving over the network or Nearby is untrusted until the session has proved the peer holds the family key (NFR-SEC-1).
2. **From other apps:** intents, files and shared text are untrusted input. They are parsed by a pure module that never throws and has size bounds (FR-56). Nothing from them is recorded without a person confirming it (FR-49 to FR-55).
3. **Display mode:** its settings are gated in the app only (FR-31, NFR-SEC-4).
4. **The app sandbox:** data at rest is protected by file-based encryption [R14]. The family key is additionally wrapped by an Android Keystore key.

There are no servers, no accounts and no analytics (NG-1, NFR-SEC-2).

---

## 2. The 10,000-ft view: components

```
  :app  (Android application: Compose UI for phone and display modes, ViewModels, platform services)
    │
    ├──▶ :data      (Android library: SQLite op log, device identity, family key vault, local device settings, export)
    ├──▶ :lan       (Android library: mDNS discovery + TCP: the default PeerLink)
    ├──▶ :nearby    (Android library: Nearby Connections PeerLink, the fallback)
    │
    ├──▶ :sync      (pure JVM: sync protocol, crypto, version vectors, OpLog / Transport / PeerLink ports, dial rule)
    ├──▶ :schedule  (pure JVM: ops → CalendarState; repeat rules; agenda; instants; clashes; reminders; edit planning)
    ├──▶ :intake    (pure JVM: .ics reader, insert-intent extras, shared text → drafts)
    │
    └──▶ :contracts (pure JVM: ids, value types, op envelope, payload schema, codec): the frozen contract
```

**Dependency rule.** Arrows point at what a module depends on.
- `:contracts` depends on nothing in the project.
- `:schedule`, `:intake` and `:sync` depend only on `:contracts`.
- `:data` depends on `:contracts` and `:sync`, because it implements `OpLog`.
- `:lan` and `:nearby` depend on `:sync`, because they implement `PeerLink` and `Transport`.
- `:app` depends on all of them and is the only module that wires them together.

Everything that decides what is on which day, at what instant, for whom, lives in pure-JVM modules, so the spec's examples run on a laptop in seconds (NFR-DET-3).

| Component | Responsibility | Produces | Consumes |
|---|---|---|---|
| `:contracts` | Vocabulary: identifiers, calendar value types, `Op` envelope, payloads, codec | All shared types | — |
| `:schedule` | `(ops) → CalendarState`; `(state, window, zone) → Agenda`; clashes; needs-someone; reminder plan; edit plans and recording checks | `CalendarState`, `Agenda`, `ReminderPlan`, `EditPlan`, `Verdict` | `Op`, payloads |
| `:intake` | Untrusted input → `Draft`s | `Draft`, `IntakeResult` | `:contracts` value types |
| `:sync` | Lamport clock, version vectors, authenticated encrypted sessions, op creation, who dials | `SyncSession`, `OpFactory`, `Dialing`, ports | `Op` |
| `:data` | Durable atomic op storage, identity, key custody, local settings, export/import | `SqliteOpLog`, `DeviceIdentityStore`, `FamilyKeyVault`, `DeviceSettingsStore`, `SyncHistoryStore`, `ExportCodec` | `:sync` ports |
| `:lan` | NSD discovery and TCP | `LanLink : PeerLink`, `SocketTransport` | `:sync` ports |
| `:nearby` | Nearby Connections | `NearbyLink : PeerLink`, framed transport | `:sync` ports |
| `:app` | Screens from `DESIGN.md`, display mode, reminders, background sync, intake activities, pairing | The APK | everything |

**Provenance.** `:sync`, `:data`, `:lan`, `:nearby` and the envelope half of `:contracts` (`Op`, `OpCodec`, `Uuids`, `Values`' clock types, the id serializer) are **copied** from House Points v0.2.3 by the user's decision (ADR-5). They are renamed to `dev.housecalendar`, and only the changes listed in §3 are made.

---

## 3. Per-component zoom

### 3.1 `:contracts`

**Structure.**
- `Ids.kt`: value classes over `UUID`:
  - copied: `FamilyId`, `DeviceId`, `OpId`
  - new: `MemberId`, `EventId`, `HolidayId`
  - `EventIds`: `random(now)`, a v7 id, and `external(uid)`, a v5 id under the calendar namespace (FR-54).
- `Uuids.kt`, `Values.kt` (`InstantMs`, `Lamport`, `Seq`), `Serializers.kt`: copied.
- `Calendar.kt`: the calendar value types:
  - `Slot`: `AllDay(days)` or `Timed(start, end, endDayOffset)`
  - `Repeat`: `Never`, `Daily`, `Weekly`, `MonthlyOnDay`, `MonthlyOnWeekday` or `Yearly`, each with `interval` and `until`
  - `Who`: `Everyone` or `Members(set)`
  - `Taking`: `NotNeeded`, `NeedsSomeone` or `Person(member)`
  - `Reminder`: `MinutesBefore` or `DaysBeforeAt`
  - `IconKey`, `Title`, `Place` and `Notes`, each with its limits (FR-8)
  - `DeviceRole`, `MemberKind`, `ColorIndex`.
- `Payloads.kt`: the sealed `Payload` hierarchy (§4.2).
- `OpCodec.kt`: copied envelope, plus the calendar payload table. Unknown types come back as `UnknownPayload`, and unreadable ones as `MalformedPayload`.

**State.** None.

**Failure modes.** `decode` and `decodePayload` never throw (copied behaviour). Constructors of limited value types (`Title.of`) return `null` rather than throwing. Deserialisation of an over-long title is not an error, because merging never rejects (FR-43). Lengths are enforced at recording time (FR-19) and truncated at intake (FR-56).

### 3.2 `:schedule`

The heart of the app, mirroring House Points' `:ledger`.

**Structure.**
- **`CalendarState`**: an immutable **map of per-field LWW registers**. Each field holds `(value, stamp)`, where `Stamp = (lamport, deviceId)`. It holds:
  - the family settings
  - `members: Map<MemberId, MemberRecord>`
  - `devices: Map<DeviceId, DeviceRecord>`
  - `events: Map<EventId, EventRecord>`
  - `changes: Map<EventId, SortedMap<LocalDate, ChangeRecord>>`
  - `edits: Map<EventId, Map<LocalDate, EditRecord>>`
  - `holidays: Map<HolidayId, HolidayRecord>`
  - `removedDevices`
  - `unknown`: counts by type and the highest schema seen.

  Each event record also keeps `created = min(stamp)` and `changed = max(stamp)` for FR-18, and the stamp of its `deleted` field for FR-17.
- **`Projection`**:
  - `fold(ops): CalendarState` and `CalendarState.plus(ops)`.
  - Because every field merge is a max over a total order, `plus` is commutative, associative and idempotent. So **incremental projection equals full projection**, and the repository applies only the ops that just arrived (ADR-12).
  - Unknown and malformed payloads only increment counters (FR-46, NFR-EXT-1).
- **`Rules`**: the occurrence test from FR-20, FR-21 and FR-24, written as a **predicate** `occurs(event, date)`. It is not an iterator. Membership is evaluated per date with integer arithmetic on `LocalDate` (`daysBetween`, `weeksBetween` of Mondays, `monthsBetween`), plus the ordinal-weekday test (ADR-10).
- **`Series`**: builds the overlay for a date: the event's fields, overlaid by the change-from records up to that date (FR-23). Lookups use `SortedMap.headMap`, so it costs O(log k) per date.
- **`Agenda`**: `resolve(state, window, zone): List<AgendaEntry>`, per FR-25 and FR-26.
  - It tests candidate dates `[from − 31, to]` for each live event, and adds occurrence edits that move into the window.
  - It applies the overlays, drops cancelled and dormant occurrences, and sorts by the total order in FR-26.
  - "Everyone" is resolved against non-archived members.
- **`Instants`**: `ZonedDateTime.of(localDateTime, zone)`, which implements RFC 5545 §3.3.5's rules exactly (verified in the spec examples). It only produces start and end instants for timed entries.
- **`Clashes`**: FR-34, a sweep over timed entries sorted by start instant, per person (involved or taking).
- **`NeedsSomeone`**: FR-35, filtering a 14-day agenda.
- **`ReminderPlan`**: `due(state, zone, owner, scope, after, until)`. It lists reminder instants in `(after, until]`, with the occurrence and the person's role (involved or taking), for FR-36 to FR-39.
- **`Suggestions`**: FR-13. Titles are prefix-matched case-insensitively with `Locale.ROOT`, most recent by `changed` stamp, and capped at 5.
- **`EditPlanner`**: turns `(occurrence, scope, changes)` into the payloads FR-15 and FR-16 prescribe. That includes the "all" scope's propagation into change-from records, and the "this and following" weekday shift.
- **`Recording`**: the FR-19 checks, plus "after N times" → `until` (FR-22). Each returns a `Verdict`.

**State.** None outside the immutable values.

**Performance (NFR-PERF-1).**
- **Projection** is O(ops) once. After that, each arriving batch is O(batch).
- **An agenda** costs O(live events × (window + 31) × log k), plus O(edits in window). For the sizing log, a 6-week window has about 13,000 events but only about 600 that can occur in it. Events whose `start > to`, or whose `until` or `ended` is before `from − 31`, are skipped by an interval index built once per state.
- A JVM benchmark guards both budgets.

**Failure modes.** None are recoverable. Everything is total, and malformed records simply don't contribute. A rule with `interval < 1` or empty `days` (only possible from a hostile peer) produces no occurrences.

### 3.3 `:intake`

**Structure.**
- `IcsReader`:
  - unfolds lines (RFC 5545 §3.1), parses content lines and parameters, and unescapes TEXT (§3.3.11)
  - reads the `VEVENT` properties in FR-50 into `RawEvent`
  - bounds: 1 MiB of input and 500 events (FR-56).
- `RruleMapper`: RRULE text → `Repeat`, or `Unmappable(text)` (FR-52). `COUNT` is returned for the app to convert, because conversion needs the occurrence predicate and holidays from `:schedule`.
- `IntentExtras`: a plain map of the insert-intent extras → `Draft` (FR-49, FR-53).
- `SharedText`: the first line becomes the title. `DateTimeHints` is a small English grammar: weekday names, "today", "tomorrow", "tonight", `d Mon`, `Mon d`, `h[:mm][am|pm]` and `HH:mm`. It resolves relative to `(now, zone)` (FR-51).
- `Draft`: title, slot, start date, repeat (or pending count), place, notes, external UID, and a list of `IntakeNote`s for anything dropped. `IntakeResult` is `Drafts(list, skipped)` or `Refused(reason)`.

**Failure modes.** Never throws. Anything unreadable becomes a counted skip.

### 3.4 `:sync` (copied)

As in House Points (SAD §3.3 there), with three changes:
1. The HKDF info label is `"hc/sync/v1"` (NG-5).
2. `LinkPeer` gains `role: DeviceRole`.
3. New `Dialing.shouldDial(selfId, selfRole, peerId, peerRole)` (FR-44):
   - a display never dials a phone
   - a phone always dials a display
   - two devices of the same role use the lower `DeviceId`.

**Observability.** `SyncOutcome` records, the last 20 kept (NFR-OPS-3).

### 3.5 `:data` (copied, plus one store)

- `SqliteOpLog`, `DeviceIdentityStore`, `FamilyKeyVault`, `SyncHistoryStore`, `ExportCodec` and `AtomicFiles` are copied.
  - The database file is `calendar.db`.
  - The export magic is `HCX1`.
- **New: `DeviceSettingsStore`** holds settings that are **local to one device and never synced**:
  - the reminder scope (FR-37)
  - display day hours (FR-31)
  - the display's home view and size-check scale
  - the last reminder instant fired (FR-38)
  - the last-shown agenda version.

  It is a small JSON file written atomically and kept out of backup, because it describes this device.
- `backup_rules.xml` and `data_extraction_rules.xml` include the database and exclude `noBackupFilesDir` [R4].

### 3.6 `:lan` (copied)

- The service type is `_housecal._tcp.` and the instance prefix is `hc-`.
- A fourth TXT attribute `r` carries the device role.
- The display collects `peers()` whenever it runs, so it always listens.
- Phones collect it in the foreground, and during background work (§3.8).

**Failure modes.** A network without multicast means no peers are found. Phones then offer Bluetooth on the Sync screen.

### 3.7 `:nearby` (copied)

- Service ID: package name plus family ID, as in House Points.
- The endpoint name carries `role|deviceId|name`.

It is only started from the Sync screen. The permission table is as in House Points (SAD §3.5 there).

### 3.8 `:app`

**Structure.**

```
app/
  AppGraph.kt              manual constructor injection; one graph per process (ADR-3)
  calendar/CalendarRepository   OpLog + OpFactory + Projection → StateFlow<CalendarSnapshot>; record(plan)
  calendar/Clock           the only wall-clock reader; a minute ticker flow for "now" and day rollover
  ui/theme/                tokens from DESIGN.md; PhysicalType (mm → px from xdpi, NFR-A11Y-4)
  ui/components/           PersonDisc, EntryLine, OrganiserGrid, AgendaList, NowLine, ScopeSheet, HcIcons …
  ui/phone/                Agenda, Week, Month, EventDetail, AddSheet, NeedsSomeone, Recently deleted
  ui/display/              DisplayRoot (Week organiser, Today board, Month, Night), idle return, burn-in shift
  ui/settings/             Family, Members, Holidays, Devices & sync, Reminders, Display, Export, Diagnostics
  ui/onboarding/           create family / join (QR) / role and owner
  intake/IntakeActivity    the intent filters of FR-49 to FR-51; hands drafts to MainActivity
  intake/DeviceCalendars   CalendarContract.Instances query (READ_CALENDAR) → drafts (FR-55)
  sync/SyncController      copied, with Dialing and roles; startAutomatic for the foreground and the display
  sync/SyncWorker          periodic WorkManager job, UNMETERED, ≤ 40 s of discovery + sessions (FR-44)
  reminders/ReminderScheduler  one exact alarm at the next due instant; ReminderReceiver posts due ones
  reminders/BootReceiver   BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED → reschedule
```

**State flow.** Unidirectional.
1. `CalendarRepository` exposes `StateFlow<CalendarSnapshot>` (state, device role and owner, unknown-version notice).
2. ViewModels combine it with `Clock.minutes` and the window being viewed. They call `Agenda.resolve` on `Dispatchers.Default`.
3. Commands go through `EditPlanner` or `Recording`, then `OpFactory.record`, which appends atomically. The repository then applies the new ops with `state.plus(ops)`.
4. The UI never expands a rule or computes an instant itself.

**Display mode.** `MainActivity` chooses `PhoneRoot` or `DisplayRoot` from this device's role. The role is in the log, so a change of role takes effect live. In display mode:
- `FLAG_KEEP_SCREEN_ON` is set during day hours [R5], with immersive system bars and `screenBrightness` overridden at night.
- An idle timer restarts on every pointer event in the root.
- Pinning is offered once (`startLockTask`, [R15]).

`PhysicalType` converts the tiers to px from `DisplayMetrics.xdpi`. Implausible values fall back to `densityDpi`, and the size check's scale is applied.

**Reminders (FR-36 to FR-39).**
- **Scheduling:** `ReminderScheduler.reschedule()` runs after every state change (debounced 2 s), at boot, and on time or zone change. It asks `ReminderPlan.due` for the next 14 days and sets **one** exact alarm (`setExactAndAllowWhileIdle`) at the earliest instant after the last one fired [R6].
- **Firing:** `ReminderReceiver`:
  - recomputes what is due in `(lastFired, now]`
  - posts entries at most 60 minutes late (late ones are labelled) and drops older ones
  - stores `lastFired`
  - reschedules.

  Recomputing at fire time means a sync that moved or cancelled an event between scheduling and firing is respected.
- **Permissions:** `USE_EXACT_ALARM` (API 33+), and `SCHEDULE_EXACT_ALARM` with `maxSdkVersion=32`, which is granted at install on 31 and 32. API 28 to 30 need no permission. `POST_NOTIFICATIONS` is requested on 33+ when a phone first has a reminder to post.

**Background sync (FR-44, OQ-1).** `SyncWorker` is unique periodic work (15 min, `NetworkType.UNMETERED`) [R11], enqueued on phones only.
- It opens `LanLink`, collects peers for up to 20 s, and dials per `Dialing`.
- It runs at most one session per peer, and returns `success` whatever the outcome. Failures are recorded in the sync history, and the next period retries.
- **Fallback if OQ-1 fails on a device:** the foreground controller is unchanged, so correctness is unaffected.

**Intake.** `IntakeActivity` is exported, `noHistory` and translucent. Its intent filters:
- `INSERT` and `EDIT` with `mimeType vnd.android.cursor.dir/event` and `vnd.android.cursor.item/event`
- `VIEW` with `text/calendar`
- `SEND` with `text/calendar` and `text/plain`.

It reads at most 1 MiB from content URIs, calls `:intake`, and starts `MainActivity` with the drafts parcelled through an in-process holder in `AppGraph`. It never writes. The add sheet or the import review records only after confirmation. Draft IDs from UIDs are checked against the state for "Already on the calendar" (FR-54).

**Pairing.** As House Points (ADR-8), with the QR prefix `hc1:`. Joining asks for the role, then the owner (FR-5).

---

## 4. Contracts in detail

### 4.1 Op envelope

Copied unchanged from House Points (its SAD §4.1): format byte `0x01`, then UUIDs as 16 bytes, big-endian integers, and length-prefixed type and body, with at most 64 KiB per op. The body is UTF-8 JSON exactly as first written.

### 4.2 Payload types (schemaVersion 1)

`CONTRACTS.md` has the full catalogue. In summary:
- one `family.created`
- per-field LWW upserts: `family.settings`, `device.upsert`, `member.upsert`, `event.upsert`, `event.change` (keyed by `eventId` + `from`), `event.edit` (keyed by `eventId` + `day`) and `holiday.upsert`
- `device.removed`.

Every field except a record's key is optional, and absent means "not written" (FR-42).

**Future kinds** (NFR-EXT-1) will be new types under their own prefixes (`task.*`, `meal.*`, `list.*`), projected by new components beside `:schedule`'s. A v1 device counts them as unknown and forwards them.

### 4.3 Sync protocol v1

House Points' protocol (its SAD §4.3) with the HKDF info `"hc/sync/v1"`. Everything else is byte-identical, including HELLO/AUTH/VECTOR/OPS/DONE, AES-256-GCM framing and batches of ≤ 256.

### 4.4 Ports

`OpLog`, `Transport` and `PeerLink` are copied, with `LinkPeer` extended by `role`:

```kotlin
data class LinkPeer(val address: String, val device: DeviceId, val name: String, val role: DeviceRole)
```

---

## 5. Architecture decisions

- **ADR-1: Event-sourced op log with derived state.**
  - Same as House Points.
  - Convergence needs a grow-only set of ops [R10]. The calendar is derived from it, never stored.
- **ADR-2: Framework SQLite, not Room or SQLDelight.**
  - One append-only table with three queries.
  - Copied and already verified on a device.
- **ADR-3: Manual dependency injection.**
  - About 15 objects. The constructors are the documentation.
- **ADR-4: The protocol authenticates and encrypts on top of the link.**
  - The family key is the trust anchor, whatever the link (NFR-SEC-1).
- **ADR-5: Copy, don't share, the sync stack.**
  - *User decision (2026-10-10).*
  - *Alternative considered:* extracting a shared `family-sync` library consumed by both apps. That would keep one audited copy, but it couples their release cycles and needs House Points refactored first.
  - *Mitigation:* the copy is recorded here and in `CLAUDE.md`, and fixes are considered for both repos.
  - *Isolation:* the service type, HKDF label, QR prefix, export magic and UUIDv5 namespace all differ, so the apps can never cross-sync (NG-5).
- **ADR-6: Pinned toolchain.**
  - Identical to House Points: AGP 8.7.3, Gradle 8.11.1, Kotlin 2.1.0, compile and target SDK 35, JDK 17, min SDK 28 (NFR-OPS-1).
  - New dependency: WorkManager `2.10.0` (a stable release compatible with SDK 35).
- **ADR-7: Fonts bundled** (OFL), with no downloadable-fonts provider. They are the same files as House Points'.
- **ADR-8: QR pairing.**
  - ZXing generates the code. The Google code scanner reads it, needing no camera permission. A manual code is the fallback.
  - Payload: `hc1:` followed by base64url of `familyId(16) ‖ familyKey(32)`.
- **ADR-9: The local network by default, Nearby as the fallback, with a role-based dial rule.**
  - The always-listening display turns the household's devices into a star with no server.
  - Phones dial it, so a phone in the background can bring it up to date.
  - The two-phone case keeps House Points' rule.
- **ADR-10: A hand-written RFC 5545 subset, evaluated as a membership predicate.**
  - *Alternatives considered:* ical4j, and dmfs lib-recur.
  - *Rejected because:*
    - Both iterate instances from `DTSTART`, so the cost of a far-future window grows with the series' age.
    - Both bring `COUNT`/`BYSETPOS` semantics the spec deliberately excludes (NG-8).
    - ical4j depends on `java.util.Calendar` and its own timezone registry, which is a second source of tz rules beside `java.time`.
  - A per-date predicate is O(1) per date, obviously total, and testable with property tests (NFR-DET-4). The subset is small enough that the predicate fits on a screen.
- **ADR-11: "This and following" is a keyed change-from register, not a series split.**
  - FR-15 and Example 5: two devices splitting the same series at different dates while apart would create overlapping series, which is unrepairable without a merge rule that the person can't see.
  - Overlaying registers keyed by `(eventId, fromDate)` cannot duplicate, by construction.
- **ADR-12: Calendar state is itself a CRDT, so projection is incremental.**
  - Every record field is an LWW register merged by `max(stamp)`, and every derived set is keyed. So `fold(A ∪ B) = fold(A).plus(B)`.
  - The repository never re-reads the whole log after startup, which removes the House Points fallback plan (a snapshot cache) before it's needed.
  - A property test asserts `fold(ops) == fold(prefix).plus(rest)` for random splits and orders.
- **ADR-13: Exact alarms, one at a time, recomputed at fire time.**
  - Calendar reminders are the use Android names for exact alarms [R6].
  - Keeping one alarm (not one per reminder) avoids stale alarms after a sync, and the recompute at fire time makes the log the only truth.
- **ADR-14: One APK and one activity for both roles.**
  - The role is data (FR-5), so a tablet can become a phone-role device or back without reinstalling.
  - Two activities or two flavours would duplicate navigation and wiring.
- **ADR-15: Physical type in display mode.**
  - `sp` is calibrated for hand-held reading distance, so a kitchen display must size by millimetres (NFR-A11Y-4).
  - Phones keep `sp`, so system font scaling still works.
- **ADR-16: Intake is a pure-JVM module with a hand-written iCalendar subset reader.**
  - The inputs are untrusted and varied, so exhaustive JVM tests (fixture files, fuzzed truncations) are worth more than a library.
  - ical4j's size and its `java.util.Calendar` dependency (ADR-10) apply here too.

---

## 6. Quality attribute traceability

| NFR | Where it's met | How it's verified |
|---|---|---|
| DET-1 | `:schedule` (pure JVM, "now" as a parameter) | Spec Examples 1 to 12 as tests; a lint-like test that `:schedule` has no `Clock`/`System` references |
| DET-2 | `:sync` + `:schedule` | 3-device property test over a loopback transport; final states equal |
| DET-3 | `:schedule`, `:intake` | `./gradlew test` with no Android runtime |
| DET-4 | `Agenda` | Property test: window split invariance |
| PERF-1 | `Projection`, `Agenda` interval index | JVM benchmark on the sizing log; on-device check when a phone is available |
| PERF-2/3 | `SyncSession`, `SyncController` | Desktop peer over `adb forward` against the emulator; two-emulator test via redirected ports |
| DUR-1 | `SqliteOpLog` | Instrumented tests (copied) on the emulator |
| SEC-1 | `SyncSession` | Copied tests: a wrong key is rejected, tampering aborts |
| SEC-2 | `LanAddresses`, manifest | Address-rule tests; code review |
| A11Y-1 | `ui/theme` | `TokensContrastTest` recomputes every ratio in DESIGN.md |
| A11Y-2/3 | components | Screenshots at 200% font scale on the emulator; the animator scale respected |
| A11Y-4 | `PhysicalType` | Unit tests of the mm → px conversion; tablet emulator screenshots |
| EXT-1 | `Projection` unknown handling | Permanent test: the projection of a log with injected `task.*`/`meal.*` ops equals the projection without them |

**Reference devices.** The emulators (`hc_phone`: Pixel 7, API 35; `hc_tablet`: 10.1" WXGA 1280×800 mdpi, API 35) are used for functional and visual verification. Performance budgets are guarded by the JVM benchmark. On-device timing is pending a physical phone, because the user's phone is away and the emulator on this Intel host isn't representative.

---

## Confidence gate

```
Confidence: high
Why: every SPEC requirement maps to a component. Everything that decides a date, instant or person is in
  pure-JVM modules with named tests. The sync, storage and crypto are copied from a live-verified
  implementation with only the listed, minimal changes. The two decisions that are new relative to House
  Points (the change-from register and incremental projection) are each guarded by a property test.
Residual risks:
  - OQ-1 (background NSD from a worker). The foreground fallback keeps correctness.
  - Exact alarms on OEM builds with aggressive battery managers (outside our control; FR-38's late window
    plus recompute-at-fire limits the damage).
  - The two emulators sit behind separate NATs, so mDNS between them doesn't work. Sync is verified
    through a desktop peer and redirected ports instead (PLAN.md).
Known gaps: physical-device performance and Nearby live tests wait for real hardware.
```
