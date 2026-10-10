# Specification

> Every non-obvious claim, threshold, or constraint in this document traces to a citation in [References](#references) or to an explicit "chosen because" rationale. The key words MUST, MUST NOT, SHOULD and MAY are used as described in RFC 2119 [R1].

## Problem statement

Families coordinate their week on a paper calendar in the kitchen: who has what on, where, and who is taking them. Paper is visible to everyone and needs no account, but it doesn't come with you, doesn't remind anyone, and has to be rewritten for every repeating club and every school holiday. Digital family calendars fix that by keeping the family's schedule on a company's server, behind an account and usually a subscription [R20]. This product keeps paper's model instead: a calendar that belongs to the household, shown on a kitchen tablet and carried on parents' phones. Devices sync peer-to-peer with no server and no internet. It must suit any family, of any size, in any timezone. See `VISION.md` and the plain-language `CONCEPT.md`.

## Glossary

- **Family:** the unit of sharing. One family per install. It has a name, a timezone and a week start day.
- **Family time:** wall-clock time in the family's timezone. Every time in the calendar is family time.
- **Device:** one installation, with a permanent random identity that is never backed up. It has a **role**:
  - **Phone:** a personal device, normally linked to one member.
  - **Display:** a shared device that lives in the kitchen, normally a tablet, and is linked to no one.
- **Member:** a person who appears on the calendar, either an adult or a child. Members are not user accounts. A child can be a member without having a device.
- **Event:** a calendar item with a title, a slot, the people involved, and optionally a repeat rule. A one-off event is an event without a repeat rule.
- **Slot:** what an event takes up on a day. Either **all day** for `n ≥ 1` days, or **timed**, from a start time to an end time, possibly ending on a later day.
- **Occurrence:** one instance of an event, identified by `(eventId, originalDate)`. The **original date** is the date the repeat rule produced it on. If the occurrence is later moved to another day, its original date doesn't change.
- **Repeat rule:** a subset of the RFC 5545 RRULE [R2] (FR-20).
- **Change from:** an edit that applies to one event's occurrences from a given original date onward ("this and following"). It is keyed by `(eventId, fromDate)`.
- **Occurrence edit:** an edit that applies to exactly one occurrence ("only this one"). It is keyed by `(eventId, originalDate)`.
- **Holiday:** a named range of days, such as a school half-term. A repeating event marked **term time only** has no occurrences on holiday days.
- **Taking:** who takes, collects or accompanies the people involved. It is one of: not needed, needs someone, or a named member.
- **Agenda:** the list of occurrences, fully resolved, that overlap a window of days.
- **Clash:** two timed occurrences that overlap in time and share a person (FR-34).
- **Operation (op):** an immutable record appended to the family log by one device. Everything that changes state is an op.

## Functional requirements

### Family, devices and members

- **FR-1:** A person MUST be able to create a family by choosing a name, a timezone (an IANA tz identifier [R3], defaulting to the device's) and a week start day (defaulting to the locale's). They then add at least one member.
- **FR-2:** A family's name, timezone and week start day are **last-writer-wins settings** (FR-42) and MAY be changed at any time.
  - Changing the timezone keeps every event at the same family time. Swimming at 17:00 stays at 17:00 in the new zone. This is the meaning a family that moves country expects.
  - The week start only changes how weeks are drawn. It never changes which occurrences a rule produces (FR-21).
  - *Rationale:* unlike a ledger, nothing stored depends on where the weeks fall or on UTC offsets, so neither needs freezing.
- **FR-3:** A device MUST generate a random identity on first launch and store it where Android Auto Backup never includes it (`noBackupFilesDir`) [R4].
  - *Rationale:* if a backup is restored onto a second device while the first is still in use, two devices would share an identity and their sequence numbers would collide.
- **FR-4:** A family MUST have a 256-bit family key, generated when the family is created. A new device joins by scanning a QR code, shown by any device already in the family, that carries the family identity and key. A pairing code shown under the QR is the manual fallback. Any number of devices MAY join.
- **FR-5:** When a device joins, the person MUST choose its role:
  - **Phone:** they also say whose phone it is, by picking a member or adding a new one.
  - **Display:** it isn't linked to anyone.

  The role and owner MAY be changed later.
- **FR-6:** A person MUST be able to remove a device, for example a lost phone. Removal generates a new family key, and the remaining devices re-pair by scanning the new QR code. Ops the removed device created before removal stay in the log.
- **FR-7:** A person MUST be able to add, rename, recolour and archive members, and mark each one as an adult or a child.
  - Archiving hides a member from pickers and from the display's columns. It MUST NOT remove them from any past or future occurrence.
  - There MUST be at most 12 non-archived members.
  - Each member has one of 8 identity colours, assigned in order and reused cyclically after that.
  - *Chosen because* 12 covers large and blended households, and it bounds the people pickers and the display layouts. 8 colours stay distinguishable from each other and from the interface's functional colours (see `DESIGN.md`).

### Events

- **FR-8:** An event has:
  - a **title** (1 to 80 characters)
  - an optional **icon** from the built-in set
  - a **start date**
  - a **slot** (FR-9)
  - a **repeat rule**, or none (FR-20)
  - a **term time only** flag, which only has an effect when there is a repeat rule
  - **who** it involves (FR-10)
  - **taking** (FR-11)
  - an optional **place** (up to 120 characters)
  - optional **notes** (up to 2,000 characters)
  - **reminders** (FR-36)
  - a **deleted** flag.

  *Chosen because* 80 characters is about as much as fits on two lines of the display's columns. The place and notes limits keep every op far below the 64 KiB envelope limit (`CONTRACTS.md`).
- **FR-9:** A slot is either:
  - **All day** with `days ≥ 1`: the occurrence covers its day and the following `days − 1` days.
  - **Timed** with a `start` time, an `end` time and `endDayOffset ≥ 0`: it runs from `start` on its day to `end` on day `+ endDayOffset`. The end MUST be after the start.

  A slot MUST NOT span more than 31 days.
  - *Chosen because* a bounded span bounds how far back the agenda must look for occurrences that started before a window (FR-26). 31 days covers any holiday or residential trip.
- **FR-10:** **Who** is either **everyone**, meaning every member who is not archived when the agenda is drawn, or a non-empty set of members.
  - *Rationale:* "Everyone" events, like a family dinner, should include a member who is added later. That only works if "everyone" is resolved when the agenda is drawn rather than written into the event.
- **FR-11:** **Taking** is one of:
  - **not needed:** the default, for example for adults' own events
  - **needs someone**
  - **a named member**, who MAY be any non-archived member but is offered from the adults first.

  An occurrence that needs someone is **unresolved** until a person is named.
- **FR-12:** A person MUST be able to add an event in a single sheet that works from top to bottom: what, who, when, repeat, taking. Place, notes and reminders are folded away below. The default slot is timed, starting at the next whole hour and lasting 1 hour, on the day being viewed.
  - *Rationale:* the title comes first because a suggestion from it (FR-13) usually fills in everything below.
- **FR-13:** While a title is typed, the app SHOULD suggest earlier events whose title starts with the typed text, ignoring case, most recent first. Choosing one copies its icon, who, slot, place and taking into the new event.
  - *Rationale:* families repeat the same activities, and re-entering the same five fields is where entry time goes.
- **FR-14:** The app SHOULD suggest an icon from the title using a built-in keyword table (for example "swim" suggests the swimming icon). The person MAY change or clear it.
- **FR-15:** Changing a **repeating** event MUST offer three scopes, and each MUST be recorded as follows:

  | Scope | Recorded as |
  |---|---|
  | **Only this one** | An occurrence edit for `(eventId, originalDate)`, carrying the changed fields |
  | **This and following** | A change from `(eventId, originalDate)`, carrying the changed fields. If the occurrence is the event's first (the earliest occurrence the recording device can see, which is not always the start date), this is recorded as "all" instead. |
  | **All** | An event update carrying the changed fields. For each change from that sets one of those fields, as seen on the recording device, the same value is also written to it, so that the change shows everywhere. Occurrence edits are left as they are. |

  - Changing a non-repeating event is always an event update.
  - Moving one occurrence to another day is an occurrence edit with a new `day`.
  - "This and following" with a new weekday is a change from carrying the shifted repeat rule.
  - *Rationale:* every scope is an append to a register keyed by data the user can see. Nothing is copied, split or mutated, so two devices editing the same series while apart can never create a duplicate series (Example 5).
- **FR-16:** Deleting MUST offer the same scopes:
  - **only this one:** an occurrence edit with `cancelled = true`
  - **this and following:** a change from with `ended = true`
  - **all:** an event update with `deleted = true`.
- **FR-17:** Deleted events MUST be listed under **Recently deleted**, newest deletion first (by the delete op's `(lamport, deviceId)`). The list shows which device deleted each one, and each can be restored (`deleted = false`). Nothing is ever erased from the log.
- **FR-18:** Every event MUST show which device added it and which device last changed it, both taken from op origins.
- **FR-19:** The checks below apply **only on the device doing the recording**. Merging MUST NOT reject any op (FR-43).
  - The title is not blank.
  - The slot is valid (FR-9).
  - The repeat rule is valid (FR-20).
  - The members referenced exist.
  - A change from or occurrence edit refers to an original date the recording device sees as an occurrence.

### Repeat rules

- **FR-20:** A repeat rule is one of the following, each with an **interval** `n ≥ 1` and an optional **until** date (inclusive). It is a subset of RFC 5545 RRULE [R2]:

  | Rule | Occurs on original date `d ≥ anchor` when |
  |---|---|
  | `daily(n)` | `daysBetween(anchor, d) mod n = 0` |
  | `weekly(n, days)` | `d`'s weekday ∈ `days` (non-empty), and `weeksBetween(monday(anchor), monday(d)) mod n = 0` |
  | `monthlyOnDay(n, dayOfMonth 1..31)` | `d.dayOfMonth = dayOfMonth`, and `monthsBetween(anchor, d) mod n = 0` |
  | `monthlyOnWeekday(n, ordinal ∈ {1,2,3,4,−1}, weekday)` | `d` is the `ordinal`th (or, for −1, the last) such weekday of its month, and `monthsBetween(anchor, d) mod n = 0` |
  | `yearly(n)` | `d` has the anchor's month and day, and `yearsBetween(anchor, d) mod n = 0` |

  - In every case, also `d ≤ until` when `until` is set.
  - `monthsBetween` and `yearsBetween` compare calendar months and years only, ignoring the day.
  - The **anchor** is the event's start date, or a change from's `fromDate` when that change sets a new rule (FR-23).
  - A date that doesn't exist, such as the 31st of a 30-day month or 29 February in a non-leap year, is **skipped**, not moved [R2, erratum 4271].
  - The start date is an occurrence only if the rule produces it. The add sheet keeps them in step by defaulting `weekly` days to the start date's weekday.
- **FR-21:** Week arithmetic for `weekly(n)` MUST use weeks beginning on Monday, whatever the family's week start.
  - *Rationale:* this is RFC 5545's default `WKST` [R2]. Tying it to the week-start setting would make changing that setting (FR-2) change which fortnight a fortnightly club falls in.
- **FR-22:** The add sheet MAY offer **"after N times"**. It is converted when recorded into the `until` date of the Nth occurrence, as the recording device sees occurrences at that moment, including the term-time skip. Only `until` is stored.
  - *Rationale:* RFC 5545's `COUNT` counts generated instances, including ones later skipped [R2], so a "10-week course" with a half-term in it would come out a week short. A count is also ambiguous once the series is edited. Storing an `until` date is unambiguous and deterministic. The consequence, shown in the sheet, is that a holiday added afterwards does not extend the course.

### Resolving occurrences (the core rules)

- **FR-23:** An event's **segments** are its start date followed by the `fromDate` of each of its change-from records, in ascending order. Change-from records with `fromDate ≤ start date` count as starting at the start date. The fields in force for an occurrence with original date `d` are:
  1. the event's fields
  2. overlaid, in ascending `fromDate` order, by each change from with `fromDate ≤ d`. Each change from overlays only the fields it has written (FR-42).
  3. If that occurrence has an occurrence edit, overlaid last by it.

  `repeat`, `termTimeOnly` and `ended` are overlaid like every other field. A rule set by a change from is anchored at that change from's `fromDate`.
- **FR-24:** `d` is an **occurrence** of event `E` if and only if all of these hold:
  - `E` is not deleted
  - `d ≥ E.start`
  - the rule in force at `d` produces `d` (FR-20), or there is no rule and `d = E.start`
  - `ended` is not in force at `d`
  - if a rule and `termTimeOnly` are both in force at `d`, then `d` is not inside any non-deleted holiday.

  Occurrence edits do not change this test. An occurrence edit whose original date is not an occurrence is **dormant**: it is kept, has no effect, and comes back into effect if its date becomes an occurrence again (Example 7).
- **FR-25:** The **placement** of an occurrence is its **day**, which is `d` unless an occurrence edit moves it, and its slot. Its **start** and **end** are family time, and every interval is **half-open**, `[start, end)`:
  - **all day:** covers the days `[day, day + days)`
  - **timed:** runs from `day` at `start` to `day + endDayOffset` at `end`. It occupies each date from its start date up to the date of the last minute before `end`, so an event ending at 00:00 doesn't occupy the following day.
- **FR-25a:** A record that is missing a field needed to place it has no occurrences. An event needs a title, a start date and a slot. An occurrence edit that moves an occurrence needs a slot from somewhere in the overlay. A record can only be incomplete if it comes from a hostile peer or a partial future write, and merging never rejects it (FR-43).
  - A family timezone this device can't resolve (for example, a newer tz identifier) falls back to `UTC` for computing instants. Diagnostics shows a notice, so every device with the same tzdata computes the same instants.
- **FR-26:** The agenda for a window of days `[from, to]` is every occurrence whose placement overlaps the window, apart from those with `cancelled = true`.
  - It is computed by testing each candidate original date in `[from − 31, to]`, which is enough because of the span limit in FR-9.
  - It also tests the original date of every occurrence edit that moves an occurrence into the window.

  Each agenda entry carries:
  - `eventId`, original date, placement
  - the resolved fields: title, icon, who with "everyone" resolved, taking, place, notes
  - whether it is the event's first occurrence
  - whether it is repeating.

  The agenda is ordered by:
  1. start day
  2. all-day before timed
  3. start time
  4. end
  5. title, by Unicode code point
  6. `eventId` (unsigned bytes).
- **FR-27:** A timed occurrence's **instants** are found by resolving its start and end in the family timezone using RFC 5545 §3.3.5 [R2]:
  - a local time that occurs twice (clocks going back) means its **first** occurrence
  - a local time that doesn't exist (clocks going forward) is read **with the UTC offset in force before the gap**.

  The agenda shows the wall-clock times of those instants (Example 2). Instants are used for reminders, clashes and "now". They are never stored.
- **FR-28:** Holidays are records with a name, a first day, a last day (inclusive, at most 366 days after the first) and a deleted flag. They are drawn across the whole family as a band on every day they cover.

### Views

- **FR-29:** Phones MUST offer three views, with the **agenda** as home:
  - **Agenda:** today, then the following days. Days with nothing on are collapsed.
  - **Week:** every day of the family week, each listing its occurrences.
  - **Month:** a grid in which each day shows a mark per person involved, plus up to 3 titles, then "+N".

  Tapping an occurrence opens its detail. Tapping a day in the month view opens that day.
- **FR-30:** A person MAY filter any view to **one member**. The filter then shows that member's occurrences and the ones they are taking.
- **FR-31:** A **display** device MUST run in **display mode**:
  - **Full screen.** The screen stays on during **day hours** (default 06:30 to 22:00) [R5]. Outside day hours it shows a dark night screen with the date and time only, at minimum brightness. A touch shows the normal views for 2 minutes.
  - **Three views:**
    - **Week:** the family organiser. A row per day from today, and a column per non-archived member when there are at most 6, with "everyone" occurrences drawn once across all columns.
    - **Today:** for each member, what they are doing now and what's next, with who is taking them.
    - **Month.**
  - **Idle return:** after 2 minutes without a touch, it returns to its home view (Week by default) scrolled to today.
  - **Day rollover:** it moves on to the new day at midnight, family time, without a touch.
  - **Editing:** anyone at the display MAY add and edit events. Opening settings needs the device's screen lock if one is set. If none is set, opening settings needs a 2-second press, so it isn't opened by accident.
  - *Chosen because:*
    - 2 minutes is long enough to finish reading a week and short enough that the display is back on today before the next person walks past.
    - Day hours default to a typical household's waking day, and the family can change them.
    - The 6-column limit: at 7 or more members the columns become too narrow for the legibility requirement (NFR-A11Y-4), so above 6 the Week view draws one list per day instead.
- **FR-32:** In the Week view, every occurrence MUST be visible in the column of every member it involves. The occurrence also shows marks for the other people involved, and who is taking them.
- **FR-33:** A view MUST mark **now**: the current time line in today's timed occurrences, with occurrences that have already ended de-emphasised.

### Coordination

- **FR-34:** A **clash** is two non-cancelled timed occurrences whose instant intervals `[start, end)` overlap, where some member is involved in or taking both. All-day occurrences never clash.
  - The app MUST show clashes on the occurrences involved, as plain text naming the person, e.g. "Sam is also taking Tom to Football at 17:30".
  - Clashes are never modal and never block recording.
  - The add sheet MUST show a clash the new event would create before it's confirmed.
- **FR-35:** Phones MUST show a **Needs someone** list, linked from home whenever it isn't empty: the unresolved occurrences (FR-11) in the next 14 days. Naming a person from the list records an occurrence edit, or a change from or event update if the person chooses those scopes.
  - *Chosen because* 14 days covers this week and next, which is the horizon on which families arrange lifts.

### Reminders

- **FR-36:** An event's reminders are a list of at most 3 entries. Each entry is either:
  - **minutes before** (0 to 10,080) the start of a timed occurrence, or
  - **days before** (0 to 7) the occurrence's first day, **at** a family time.

  New timed events default to 30 minutes before. New all-day events default to none.
  - *Chosen because* 30 minutes leaves time to get ready and leave the house, while all-day items such as holidays seldom need an alert. A week is the longest lead that's still useful for something on the family's calendar.
- **FR-37:** Each **phone** MUST post a local notification for every reminder of every occurrence relevant to it. An occurrence is relevant when the phone's member is involved in it (including "everyone") or is taking it. A phone MAY instead be set to "everyone's events" or "none".
  - Displays MUST NOT post reminders. They highlight what's next instead (FR-31).
  - Reminder instants are computed locally from the log, through FR-27, and alarms are rescheduled after any change to the log, a reboot, or a change of time or timezone.
- **FR-38:** Reminders MUST be scheduled as exact alarms, because a reminder that arrives late is a wrong reminder. This is the use Android reserves for calendar apps through `USE_EXACT_ALARM` [R6].
  - A reminder whose instant passed while the phone was off or the app couldn't run MUST still be posted if it is at most 60 minutes late, marked as late, and dropped otherwise.
  - *Chosen because* within an hour the reminder can still change what someone does. After that it is noise.
- **FR-39:** A reminder notification MUST say what, when, and the person's role: "Swimming · 17:00 · You're taking Ada". Tapping it opens the occurrence.

### Sync

- **FR-40:** Every op MUST carry:
  - `opId`
  - `originDevice`
  - `originSeq`: a contiguous counter per device, starting at 1
  - `lamport`: a Lamport clock [R7], set to one more than the highest value the device has seen
  - `schemaVersion`
  - a payload.

  Ordering and conflict resolution MUST use `(lamport, deviceId)` and MUST NOT use wall-clock time.
- **FR-41:** Two paired devices MUST be able to sync without the internet over either link:
  - **Same Wi-Fi (default):** devices on the same local network find each other with mDNS/DNS-SD [R8] and sync over a direct, encrypted TCP connection between home-network addresses.
  - **Fallback:** Nearby Connections over Bluetooth, BLE and Wi-Fi Direct [R9], started from the Sync screen.

  Each side sends a version vector (`deviceId → highest contiguous originSeq`) and receives the ops it is missing. Applying ops MUST be idempotent, commutative and associative, so that any order and any repetition of syncs between any number of devices ends in the same state. These are the convergence conditions for a grow-only-set CRDT [R10].
- **FR-42:** The family settings, members, devices, events, change-from records (keyed by `(eventId, fromDate)`), occurrence edits (keyed by `(eventId, originalDate)`) and holidays are **last-writer-wins registers per field**, ordered by `(lamport, deviceId)` [R10]. A field absent from an op means "not written by this op".
- **FR-43:** Merging MUST NOT reject any op. If merged state looks odd, the app shows it as it is and MUST NOT correct it automatically. For example, merged state might name an archived member as taking someone, or produce a clash.
- **FR-44:** **When devices sync.**
  - A **display** listens for connections whenever it is running.
  - A **phone** syncs:
    - when the app comes to the foreground
    - 3 seconds after recording anything
    - every 60 seconds while it is in the foreground and another device is visible
    - in the background, as periodic work every 15 minutes while on an unmetered network [R11].
  - When both devices in a pair are phones, the one with the lower `DeviceId` dials. When one is a display, the phone dials.
  - *Rationale:* the display is always on in the kitchen, so it becomes the family's meeting point without being a server. A phone that comes home gets the display up to date within one background period, and the display passes changes on to the other phones. Fifteen minutes is Android's minimum periodic interval [R11].
- **FR-45:** The app MUST show, for each paired device, when it last synced with that device. A display shows its newest sync on screen ("Up to date with Sam's phone · 4 min ago"). It shows nothing alarming when it is stale, only the time.
- **FR-46:** A device MUST store, unchanged, any op whose `schemaVersion` or type it doesn't understand, and pass it on in syncs. Such ops have no effect on its calendar. It MUST tell the person that another device is running a newer version.
  - *Rationale:* other families will run mixed app versions, and later versions will add new kinds of item (NFR-EXT-1). Dropping unknown ops would lose data the next time those devices sync.

### Backup and export

- **FR-47:** The op log MUST be eligible for Android Auto Backup. The device identity and the wrapped family key MUST NOT be (FR-3) [R4].
- **FR-48:** A person MUST be able to export the whole op log to a file encrypted with a passphrase, and import it on a new device. Imported ops merge exactly like a sync.

### Adding from other apps

Added 2026-10-10 at the user's request: activities can come from other apps on the phone. Everything arriving from another app becomes a **draft**: the add sheet, filled in as far as the source allows. A person confirms it, usually by adding who and taking, which no other app knows. Nothing from another app is ever recorded without that confirmation.

- **FR-49:** The app MUST accept Android's standard calendar insert intent: `ACTION_INSERT` or `ACTION_EDIT` on `content://com.android.calendar/events` [R21]. That is what an "Add to calendar" button in another app sends, so House Calendar appears in that app's chooser. The draft takes:
  - the title (`Events.TITLE`)
  - the begin and end times (`EXTRA_EVENT_BEGIN_TIME` and `EXTRA_EVENT_END_TIME`), converted from epoch milliseconds to family time (FR-53)
  - all day (`EXTRA_EVENT_ALL_DAY`)
  - the place (`Events.EVENT_LOCATION`)
  - the notes (`Events.DESCRIPTION`)
  - the repeat rule (`Events.RRULE`), when it maps onto FR-20 (FR-52).

  No calendar permission is needed for this [R21].
- **FR-50:** The app MUST open iCalendar files (`text/calendar`, `.ics`) [R2], whether viewed from a file or email or shared to it.
  - **One event:** it opens as a draft.
  - **Several events:** they open as a review list with every event selected, which the person can untick, and one "Add N events" action.
  - **Holidays:** when every selected event is all day, the list offers **Add as holidays** instead (FR-28). This is how a school's term-dates file becomes the family's holidays in one step.

  The file reader MUST:
  - support `VEVENT` with `UID`, `SUMMARY`, `DTSTART` and `DTEND` or `DURATION` (as `DATE`, UTC, `TZID` or floating `DATE-TIME`), `LOCATION`, `DESCRIPTION`, `RRULE` and `EXDATE`
  - ignore every other component and property
  - unfold lines and unescape text as RFC 5545 §3.1 and §3.3.11 require [R2].
- **FR-51:** Text shared to the app (`ACTION_SEND`, `text/plain`) MUST open a draft with:
  - the first line as the title (truncated to 80 characters)
  - the whole text in notes.

  The app SHOULD also detect one date and time in the text ("Sat 3pm", "tomorrow at 10:30", "12 Oct") relative to the moment of sharing, and fill the slot with it. Detection is English-only in v1, and the person sees and can change what was detected.
- **FR-52:** An incoming repeat rule that maps exactly onto FR-20 is kept, with `COUNT` converted to `until` as in FR-22. One that doesn't map:
  - becomes a one-off event on its first date
  - gets the line "Repeats in a way House Calendar can't show: {rule}" in its notes.

  `EXDATE` values become "only this one" deletions (FR-16). `RECURRENCE-ID` overrides are skipped, and the review list says how many were skipped.
- **FR-53:** Incoming times are converted to family time:
  - a UTC or `TZID` time through its instant (FR-27)
  - a floating time or a `DATE` as written
  - an all-day insert intent by reading its begin time's date in UTC (Android's convention for all-day events [R21]).

  Converted times are kept to the minute, even when they're off the pickers' 5-minute steps.
- **FR-54:** An event that comes with an iCalendar `UID` (from a file, or from a phone calendar's `UID_2445`) MUST get the deterministic ID `UUIDv5("ext:" + uid)` [R22]. If an event with that ID already exists, the draft says "Already on the calendar" and confirming it records an update rather than a second event.
  - So importing the same school file twice, or on two phones, gives one event per item. This is the same technique House Points uses for chores recorded on two phones.
- **FR-55:** A phone MAY copy events from its own device calendars (Google and others, through the Android calendar provider) [R21]:
  - **Picking:** the person picks a calendar and a date range, then ticks events in a review list, as in FR-50.
  - **Permission:** `READ_CALENDAR` is requested only at that moment, with a plain explanation. Refusing it changes nothing else.
  - **One-way copy:** the copy isn't linked. Later changes in the other calendar are not followed, and the review list says so.
  - *Rationale:* following another calendar is the cloud sync that NG-2 excludes. A copy keeps the family calendar the single record, and FR-54 keeps a second copy from duplicating.
- **FR-56:** Incoming data is untrusted.
  - A file over 1 MiB, or holding more than 500 events, MUST be refused with a plain message.
  - Fields are truncated to the limits in FR-8.
  - The reader MUST NOT throw on malformed input. It skips what it can't read and says how many items it skipped.
  - *Chosen because* a whole school year of term dates, closures and events is well under 200 events and 100 KiB. Ten times that leaves room for any real file while bounding memory.

## Worked examples (acceptance tests)

These examples are normative. Each one becomes an acceptance test on the plain JVM: Examples 1 to 12 of `:schedule`, and 13 and 14 of `:intake`.

**Common setup:**
- Family timezone `Europe/London`, weeks start on Monday.
- UK clocks go back on Sun 25 Oct 2026 at 02:00 BST, and forward on Sun 29 Mar 2026 at 01:00 GMT [R3].
- Holiday **Half term**: Mon 26 to Fri 30 Oct 2026.
- Members: Sam and Alex (adults); Ada and Tom (children).
- **Swimming:** starts Tue 6 Oct 2026, timed 17:00 to 18:00, `weekly(1, {TUE})`, term time only, who {Ada}, taking Sam.
- Phone A is Sam's, phone B is Alex's, D is the kitchen display.

### Example 1: weekly, term time only

The agenda for 6 Oct to 10 Nov holds Swimming on Tue 6, 13 and 20 Oct, and 3 and 10 Nov. There is nothing on 27 Oct, because it falls in half term.

Deleting the Half term holiday brings back 27 Oct, with nothing else recorded.

### Example 2: daylight saving

- Swimming on 20 Oct is 17:00 BST (16:00 UTC). On 3 Nov it is 17:00 GMT (17:00 UTC). Both show as **17:00**, and a 30-minute reminder fires at 16:30 family time on each day.
- A one-off event timed 01:30 on Sun 25 Oct 2026 (a time that occurs twice) is the **first** 01:30, BST (00:30 UTC).
- A one-off event timed 01:30 on Sun 29 Mar 2026 (a time that doesn't exist) is read with the GMT offset from before the gap: 01:30 UTC, shown as **02:30**.

### Example 3: only this one, and this and following, while apart

While apart:
- On A, Sam moves **only** the 20 Oct swimming to 18:00 to 19:00 (an occurrence edit).
- On B, Alex changes **this and following** from 13 Oct, setting the place to "Leisure Centre" (a change from).

After A and B both sync with D, all three agree:

| Occurrence | Time | Place |
|---|---|---|
| 6 Oct | 17:00 to 18:00 | (none) |
| 13 Oct | 17:00 to 18:00 | Leisure Centre |
| 20 Oct | 18:00 to 19:00 | Leisure Centre |
| 3 Nov onward | 17:00 to 18:00 | Leisure Centre |

### Example 4: the same field changed on two phones

While apart, A sets the title to "Swim club" (lamport 40), and B sets it to "Swimming lessons" (lamport 42). After the sync, every device shows **Swimming lessons** (FR-42).

### Example 5: two "this and following" changes at different dates

While apart:
- A changes this and following from 20 Oct to start at 17:30, keeping the length.
- B changes this and following from 10 Nov so that Tom is also involved.

After the sync:
- 6 and 13 Oct: 17:00, Ada.
- 20 Oct and 3 Nov: 17:30, Ada.
- From 10 Nov: 17:30, Ada and Tom.

There is exactly one occurrence per date. Splitting the series into two would have given duplicates here, which is why it isn't done (FR-15).

### Example 6: delete on one phone, edit on the other

While apart, A deletes Swimming (**all**), and B changes this and following from 13 Oct to the place "Pool". After the sync:
- Swimming has no occurrences.
- It appears under Recently deleted as deleted by Sam's phone.
- Restoring it on any device brings it back with B's change in place.

### Example 7: a holiday added after an occurrence edit

Start without Half term recorded, so 27 Oct is an occurrence.
- Sam moves only the 27 Oct occurrence to 16:00.
- Alex then records Half term.

27 Oct disappears, and the edit becomes dormant. If Half term is deleted, 27 Oct comes back at **16:00**.

### Example 8: monthly on the 31st, and the last Friday

- Bins collection, starting Sat 31 Oct 2026, `monthlyOnDay(1, 31)`: 31 Oct, 31 Dec, 31 Jan 2027, 31 Mar, 31 May. November, February and April are skipped, not moved [R2].
- Book club, starting Fri 30 Oct 2026, `monthlyOnWeekday(1, −1, FRI)`: 30 Oct, 27 Nov, 25 Dec.

### Example 9: "after 10 times"

Swimming entered with "after 10 times" (and Half term recorded) stores `until = Tue 15 Dec 2026`. That date is the tenth occurrence: 6, 13 and 20 Oct, then 3, 10, 17 and 24 Nov, then 1, 8 and 15 Dec. A holiday recorded later on 8 Dec removes that occurrence and doesn't add 22 Dec (FR-22).

### Example 10: a clash

Football is on Tue 20 Oct, 17:30 to 18:30, who {Tom}, taking Sam. It overlaps Swimming (17:00 to 18:00, taking Sam) from 17:30 to 18:00, and Sam is taking both. Both occurrences show "Sam is also taking …". If Football's taking is changed to Alex, the clash disappears. A family dinner for everyone at 17:45 would also clash, with Ada and with Tom.

### Example 11: everyone, with a new member

"Grandma visits" on Sat 7 Nov, all day, who **everyone**. After the member Bea is added, the 7 Nov occurrence involves Sam, Alex, Ada, Tom and Bea. After Tom is archived, it no longer lists Tom, but Tom's own events still show him.

### Example 12: moved across the window edge

Only the 3 Nov swimming is moved to Sat 31 Oct. The agenda for 26 Oct to 1 Nov shows it on 31 Oct. The agenda for 2 to 8 Nov doesn't show it on 3 Nov.

### Example 13: an `.ics` file from school

A file has two events:
- `UID:inset-1@school`, `DTSTART;VALUE=DATE:20261102`, `SUMMARY:INSET day`
- `UID:hols-1@school`, `DTSTART;VALUE=DATE:20261221`, `DTEND;VALUE=DATE:20270104`, `SUMMARY:Christmas holidays`.

Both are all day, so the review list offers **Add as holidays**. Accepting gives:
- the holiday "INSET day" on 2 Nov
- the holiday "Christmas holidays" from 21 Dec to 3 Jan (`DTEND` is exclusive for `DATE` values [R2]).

Swimming, being term time only, then has no occurrences on 22 or 29 Dec. Opening the same file again on phone B shows both items as "Already on the calendar" (FR-54).

### Example 14: an invite in another timezone

A file has `DTSTART;TZID=America/New_York:20261103T120000`, `DTEND;TZID=America/New_York:20261103T130000`, `SUMMARY:Call with Grandpa`. In Europe/London family time, that is **17:00 to 18:00** on Tue 3 Nov. New York is still on daylight time until 1 Nov, so on 3 Nov it is UTC−5, and 12:00 there is 17:00 UTC, which is 17:00 GMT. The draft shows 17:00, and FR-34 reports the clash with Swimming for Ada only if she is added to the call.

## Non-functional requirements

### Determinism and correctness

- **NFR-DET-1:** The agenda MUST be a pure function of `(set of ops, window, family zone)`, and instants of `(agenda, zone)`. Two devices with the same op set MUST produce identical agendas for the same window. The path MUST NOT use the device clock, the device locale, floating point, or iteration over unordered collections. "Now" is a parameter, never read inside the path.
- **NFR-DET-2:** Property-based tests MUST show that merging is idempotent, commutative and associative over randomly generated op sets and random sync orders between at least three devices [R10].
- **NFR-DET-3:** All worked examples above MUST pass as acceptance tests on the plain JVM, with no Android runtime.
- **NFR-DET-4:** Property tests MUST show that, for random events, rules, holidays and edits, splitting a window into two never changes the result. The union of the two halves' agendas equals the whole window's agenda, and an entry that spans the split is identical in both halves.

### Performance

- **NFR-PERF-1:** Projecting the sizing log into the calendar state MUST take ≤ 1 s on the reference phone (`SAD.md`) and MUST NOT run on the main thread. Resolving a 6-week agenda from that state MUST take ≤ 100 ms.
  - Sizing log: 8 members × 10 years. It has 40 repeating series with 4 change-from records each, 25 one-off events a week, 5 occurrence edits a week and 3 updates per event, which comes to about 60,000 ops. *Chosen as* a generous upper bound on one family's use.
  - *Rationale:* about 1 s is the limit before a person's flow of thought is interrupted, and 0.1 s is the limit for a response to feel instantaneous, which is what moving between weeks should feel like [R12]. Main-thread work also risks Android's 5-second input ANR [R13].
- **NFR-PERF-2:** An incremental sync of ≤ 500 ops MUST finish within 10 s of the connection being established.
- **NFR-PERF-3:** With a phone and the display on the same Wi-Fi and the app in the foreground on the phone, an event added on the phone MUST appear on the display within 15 s.
  - *Chosen because* it's about the time it takes to put a phone down and look up at the wall. It allows the 3 s debounce (FR-44), discovery and the session.

### Durability and storage

- **NFR-DUR-1:** An op MUST be committed to on-device storage in a single transaction before the UI confirms it was recorded.
- **NFR-DUR-2:** The sizing log SHOULD stay under 15 MB, inside Auto Backup's 25 MB per-app quota [R4]. Families beyond that rely on the export in FR-48.

### Security and privacy

- **NFR-SEC-1:** Sync sessions MUST mutually authenticate both devices as holders of the family key, and MUST protect every message with authenticated encryption keyed from the family key. Nearby Connections' own encryption [R9] counts only as transport protection, because it isn't tied to the family key.
  - *Threat model:* spoofing and information disclosure (STRIDE) by any device on the network or within Bluetooth range.
- **NFR-SEC-2:** No data leaves the device except:
  - to authenticated paired devices
  - through Android backup, when the user has turned it on
  - through an export the person starts.

  The app MUST NOT contain analytics, advertising, network crash reporting or accounts.
  - Android requires the `INTERNET` permission for any socket, so this is verified by code review and by the local link's address rules. The link only listens on and connects to site-local, link-local and unique-local addresses. It advertises a hash of the family id, not the id.
- **NFR-SEC-3:** Data on the device relies on Android's file-based encryption [R14], kept in app-private storage. The family key is additionally wrapped by a key held in the Android Keystore.
- **NFR-SEC-4:** Display mode limits what someone can do *inside the app*: settings are gated (FR-31). It does not stop anyone leaving the app, because an app that isn't managed by a device administrator can only request screen pinning, which the user can exit [R15]. The app MUST NOT claim otherwise.

### Accessibility and legibility

- **NFR-A11Y-1:** Text MUST meet WCAG 2.2 contrast of 4.5:1 for normal text, and 3:1 for large text and UI components [R16]. Touch targets MUST be at least 48 × 48 dp [R17].
- **NFR-A11Y-2:** Every phone screen MUST stay usable with system font scaling up to 200% [R16] and with TalkBack.
- **NFR-A11Y-3:** Animations MUST respect the system "remove animations" setting.
- **NFR-A11Y-4:** In display mode, text sizes MUST be calculated from the screen's physical density, not only from dp:
  - **Glance tier** (today's date, the time, and each member's now and next): x-height of at least **0.2° of visual angle at 2 m** (≥ 7.0 mm).
  - **Detail tier** (everything else in display mode): at least 0.2° at 0.6 m (≥ 2.1 mm), which is about 1.5 times ordinary tablet text.
  - *Rationale:*
    - 0.2° is about the smallest x-height at which adults still read at full speed (the critical print size) [R18].
    - 2 m is from the kitchen table or the hob. 0.6 m is standing at the wall within arm's reach, which is where people stand to touch it.
    - Signage guidance of about 25 mm of letter height per 3 m [R19] is for readers on the move and would fit only a few lines on a tablet, so it isn't used.
    - Checked in `DESIGN.md` against the bundled fonts' measured x-heights: on a 10" 1280×800 display in landscape, the organiser fits 7 days with at least two one-line entries per member per day for families of up to 4 members. Families of 5 or 6 see 5 days at a time and swipe for the rest.

### Extensibility

- **NFR-EXT-1:** New kinds of item (tasks, meal plans, shopping lists) MUST be addable later as new payload types and new projection components. Adding them MUST NOT need a change to the envelope, the sync protocol or any existing payload. A permanent test MUST show that a log containing payload types this version doesn't know projects to exactly the calendar it would without them.

### Operability

- **NFR-OPS-1:** Minimum Android version: 9 (API 28).
  - *Chosen because* it is the lowest version with end-to-end encrypted Auto Backup [R4], and it covers the older tablets families repurpose for the kitchen.
- **NFR-OPS-2:** All user-facing strings MUST be externalised for translation. Dates and times MUST be formatted with the device locale, using its 12- or 24-hour setting. Version 1 ships in English.
- **NFR-OPS-3:** The app MUST include a diagnostics screen showing:
  - the device identity and role
  - the paired devices
  - the version vector
  - op counts by type
  - the last 20 sync attempts with their outcomes
  - the next scheduled reminder.

  Nothing is sent anywhere.

## Non-goals

- **NG-1:** Servers, cloud sync or user accounts. *Because* none are needed (FR-41), and having none removes cost, operations work and a privacy risk.
- **NG-2:** Continuous or two-way sync with Google, Outlook or iCloud calendars, and exporting to them. *Because* that is cloud sync by another route. Bringing events *in* once, from other apps, files and the phone's calendars, is in scope (FR-49 to FR-56).
- **NG-3:** Tasks, meal plans and shopping lists. *Deferred*, with the model ready for them (NFR-EXT-1).
- **NG-4:** Children's own devices. *Because* the display is the family's shared view. A later version may add a "child phone" role.
- **NG-5:** House Points integration. *Because* the two apps release independently. They use different service types and key derivation labels, so they never discover or authenticate each other.
- **NG-6:** iOS. *Because* the fallback link depends on Nearby Connections through Google Play Services.
- **NG-7:** A separate timezone per event. All times are family time (FR-2).
- **NG-8:** Rules more frequent than daily, `BYSETPOS` beyond a single ordinal, and storing `COUNT` (FR-22).
- **NG-9:** Invitations, replies, free/busy, and anything involving people outside the family.
- **NG-10:** Photos, attachments and weather. *Because* they need bandwidth, storage or the internet that the core doesn't.
- **NG-11:** Roles and permissions between parents. Every device can do everything except open display-mode settings without the screen lock.
- **NG-12:** More than one family per install.

## Constraints

- Android only. The Bluetooth fallback needs Google Play Services. The Wi-Fi link doesn't.
- The app has to work fully with no internet connection.
- The sync stack is copied from House Points (user decision, 2026-10-10). Its wire protocol is reused with a different key-derivation label and service type (NG-5).

## Open questions

- [ ] **OQ-1 (spike):** Background NSD discovery from a periodic worker on Android 12+ phones, on the reference phone (FR-44). The fallback is foreground-only sync, with the display catching up whenever a phone is opened.
- [ ] **OQ-2 (spike):** Keep-screen-on plus minimum window brightness at night on the target tablet, and whether a dark screen is enough or the tablet's own sleep schedule is needed (FR-31).
- [x] **OQ-3:** Distribution: a sideloaded, signed APK, as for House Points. Play policy declarations (including `USE_EXACT_ALARM`) are made if it ever goes to the Play Store.

## References

1. **[R1]** Bradner, S. *RFC 2119: Key words for use in RFCs to Indicate Requirement Levels.* IETF, 1997. https://www.rfc-editor.org/rfc/rfc2119
2. **[R2]** Desruisseaux, B. (ed.) *RFC 5545: Internet Calendaring and Scheduling Core Object Specification (iCalendar).* IETF, 2009:
   - §3.3.5 DATE-TIME: a repeated local time means its first occurrence; a non-existent local time is read with the UTC offset before the gap
   - §3.3.10 RECUR: `WKST` defaults to `MO`; `-1MO` means the last Monday
   - verified erratum 4271: invalid dates such as 30 February are ignored and not counted.

   https://www.rfc-editor.org/rfc/rfc5545, https://www.rfc-editor.org/errata/eid4271
3. **[R3]** IANA Time Zone Database. https://www.iana.org/time-zones. In 2026, UK clocks go forward on Sun 29 Mar and back on Sun 25 Oct (checked against `java.time` tzdata).
4. **[R4]** Android Developers, *Back up user data with Auto Backup*: the 25 MB per-app quota, end-to-end encryption on Android 9+ with a screen lock, and the automatic exclusion of `getNoBackupFilesDir()`. https://developer.android.com/identity/data/autobackup
5. **[R5]** Android Developers, *Keep the screen on* (`FLAG_KEEP_SCREEN_ON`: no permission needed, applies only while the window is visible). https://developer.android.com/develop/background-work/background-tasks/awake/screen-on
6. **[R6]** Android Developers, *Schedule alarms*: "If your app's core functionality depends on a precisely-timed alarm, such as for an alarm clock app or a calendar app, then it's OK to use an exact alarm", and `setExactAndAllowWhileIdle()` fires "even if battery-saving measures are in effect". https://developer.android.com/develop/background-work/services/alarms/schedule. *Android 14 behaviour change, exact alarms denied by default*: `SCHEDULE_EXACT_ALARM` isn't pre-granted to new installs targeting 13+, while calendar and alarm clock apps that must send reminders when not running can request `USE_EXACT_ALARM`, which is granted at install. https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
7. **[R7]** Lamport, L. (1978). Time, clocks, and the ordering of events in a distributed system. *Communications of the ACM*, 21(7), 558–565.
8. **[R8]** Cheshire, S., & Krochmal, M. *RFC 6762: Multicast DNS* and *RFC 6763: DNS-Based Service Discovery.* IETF, 2013. Android `NsdManager`: https://developer.android.com/develop/connectivity/wifi/use-nsd
9. **[R9]** Google, *Nearby Connections overview*: works fully offline, uses Bluetooth, BLE and Wi-Fi, and connections are encrypted. https://developers.google.com/nearby/connections/overview
10. **[R10]** Shapiro, M., Preguiça, N., Baquero, C., & Zawirski, M. (2011). Conflict-free replicated data types. *SSS 2011*, LNCS 6976, 386–400.
11. **[R11]** Android Developers, *Define work requests*: "The minimum repeat interval that can be defined is 15 minutes", and the `NetworkType.UNMETERED` constraint. https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
12. **[R12]** Nielsen, J. (1993). *Usability Engineering*, ch. 5, response-time limits (0.1 s / 1 s / 10 s). Morgan Kaufmann.
13. **[R13]** Android Developers, *ANRs*: input dispatch timeout of 5 seconds. https://developer.android.com/topic/performance/vitals/anr
14. **[R14]** Android Open Source Project, *File-based encryption.* https://source.android.com/docs/security/features/encryption/file-based
15. **[R15]** Android Developers, *Lock task mode*: an app that isn't allowlisted gets screen pinning, which the user can exit. https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
16. **[R16]** W3C, *Web Content Accessibility Guidelines (WCAG) 2.2*, SC 1.4.3, 1.4.4, 1.4.11. https://www.w3.org/TR/WCAG22/
17. **[R17]** Android Developers, *Make apps more accessible*: touch targets of at least 48 dp. https://developer.android.com/guide/topics/ui/accessibility/apps
18. **[R18]** Legge, G. E., & Bigelow, C. A. (2011). Does print size matter for reading? A review of findings from vision science and typography. *Journal of Vision*, 11(5):8. The fluent range of print sizes runs from about 0.2° to 2° of x-height.
19. **[R19]** Studio Matrx, *Legibility, size and viewing distance* (signage rule of thumb: about 25 mm cap height per 3 m). https://www.studiomatrx.org/students/wayfinding-and-environmental-graphics/legibility-size-and-viewing-distance. Used only to explain why the signage rule is *not* used.
20. **[R20]** Prior art: Skylight Calendar (a wall display that syncs through cloud calendar accounts, with a paid "Plus" tier; Best Buy Canada review, https://blog.bestbuy.ca/smart-home/skylight-touchscreen-calendar-smart-display-review). The paper "family organiser" calendar, with a column per person, is the layout FR-31 adopts.

21. **[R21]** Android Developers, *Calendar provider overview*: calendar intents (`INSERT`/`EDIT` on `content://com.android.calendar/events` with the `TITLE`, `EXTRA_EVENT_BEGIN_TIME`, `EXTRA_EVENT_END_TIME`, `EXTRA_EVENT_ALL_DAY`, `EVENT_LOCATION`, `DESCRIPTION` and `RRULE` extras, needing no calendar permission), and `READ_CALENDAR` for reading events. https://developer.android.com/identity/providers/calendar-provider
22. **[R22]** Davis, K., Peabody, B., & Leach, P. *RFC 9562: Universally Unique IDentifiers (UUIDs).* IETF, 2024 (UUIDv5 §5.5, UUIDv7 §5.7). https://www.rfc-editor.org/rfc/rfc9562

---

## Confidence gate

```
Confidence: high (for the rules this spec governs)
Why: every rule that decides what appears on which day is pinned to dates, with 12 worked examples
  covering term time, daylight saving (both directions), every edit scope, concurrent edits, deletion,
  dormant edits, skipped dates, clashes and window edges. Each scope is an append to a keyed register,
  so convergence comes from the grow-only set plus per-field LWW, the structure House Points already
  verified.
Residual risks:
  - OQ-1: background discovery from a worker is unproven. The fallback (foreground sync) keeps every
    rule intact and only delays the display.
  - OQ-2: night dimming behaviour varies by tablet. It affects comfort, not correctness.
  - FR-51's date detection is heuristic. It is a SHOULD, English-only, and always shown for confirmation,
    so a miss costs a tap, never a wrong event.
  - The legibility tiers (NFR-A11Y-4) were sized against a 10" 1280×800 tablet. Tablets that report a
    wrong physical density would get the wrong sizes, so a size check is offered in display settings.
Known gaps: none that block architecture.
```
