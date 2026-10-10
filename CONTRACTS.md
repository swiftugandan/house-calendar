# Contracts

The `:contracts` module (`contracts/`, package `dev.housecalendar.contracts`) is the single source of truth for every type that crosses a module boundary or leaves the device. This document explains it. The code is authoritative.

**Version:** `0.1.0`, frozen at `contracts/v0.1.0-frozen`. After the freeze, changes need a `contracts/amendment-<desc>` branch and a version bump. Anything that changes stored bytes or payload meaning is a major bump.

## Provenance

The envelope (`Op`, `OpCodec`'s byte format, `Uuids`, `InstantMs`, `Lamport`, `Seq`, the UUID, date, zone and weekday serializers) is copied from House Points v0.2.3 (SAD ADR-5), with House Points' envelope tests. Everything else is new.

## Policies

- **Typed identifiers.** `FamilyId`, `DeviceId`, `OpId`, `MemberId`, `EventId` and `HolidayId` are value classes over `java.util.UUID`. A raw UUID never crosses a module boundary.
  - Ordering is `Uuids.compare`: unsigned and byte-wise, so it's identical everywhere.
  - `java.util.UUID.compareTo` is signed and **must not** be used for tie-breaks.
- **Time.**
  - `InstantMs` is epoch milliseconds, UTC.
  - Calendar days are `java.time.LocalDate`, written as ISO-8601 (`2026-10-06`).
  - Wall-clock times are `java.time.LocalTime`, written as `HH:mm` to the minute. Seconds are dropped when written.
  - Zones are IANA IDs. Week days are `DayOfWeek` names (`TUESDAY`).
  - There's no floating point anywhere.
- **Ordering for conflicts.** `Op.CAUSAL_ORDER` is `(lamport, originDevice)`. It's the only order used for last-writer-wins.
- **Storage and transfer order.** `Op.ORIGIN_ORDER` is `(originDevice, originSeq)`.

## Op envelope

```
Op(opId, familyId, originDevice, originSeq ≥ 1, lamport ≥ 1, schemaVersion, type, body)
```

- `body` is the payload's UTF-8 JSON **exactly as first written**. It is never re-encoded.
- **Binary form** (`OpCodec.encode`/`decode`): `0x01 ‖ opId ‖ familyId ‖ originDevice ‖ originSeq i64 ‖ lamport i64 ‖ schemaVersion i32 ‖ u32 len ‖ type ‖ u32 len ‖ body`, big-endian, at most 64 KiB.
- `decode` never throws. Bad input comes back as `DecodeResult.Malformed`.

## Payload JSON policy

- **Config:** `kotlinx.serialization` with `ignoreUnknownKeys = true`, `explicitNulls = false` and `encodeDefaults = false`.
- **Names:** property names are the Kotlin property names (camelCase), without renaming. Renaming a property is a breaking change.
- **Absent vs written:** in an upsert, a field that is **absent** means "not written by this op". That is what makes per-field last-writer-wins possible (SPEC FR-42).
- **Clearing:** `icon`, `place` and `notes` are cleared by writing the empty string (`IconKey.NONE` is `""`). Titles and names can't be cleared (FR-19). Lists are cleared by writing `[]`.
- **Tagged unions.** `Slot`, `Repeat`, `Who`, `Taking`, `Reminder` and `Owner` are written as an object whose first key is `"type"`, followed by the variant's fields, e.g. `{"type":"timed","start":"17:00","end":"18:00","endDayOffset":0}`. Variants without fields are just the tag: `{"type":"everyone"}`.
- **Open sets.** `DeviceRole` (`PHONE`, `DISPLAY`) and `MemberKind` (`ADULT`, `CHILD`) are plain strings.
- **Unrecognised values (forward compatibility).**
  - A union tag or open-set name this version doesn't know is read as that type's `Unrecognised` value, holding the raw JSON or string. The rest of the payload reads normally, and an unrecognised value is written back exactly as read.
  - So a newer device can add a repeat kind, a taking kind or a device role without its events vanishing from older devices.
  - `:schedule` defines what each unrecognised value means for display (SAD §3.2).
- **Malformed payloads.** A union **missing** its `"type"`, a field of the wrong JSON kind, an invalid date or time, or an invalid UUID makes the whole payload `MalformedPayload`. It is kept and forwarded, and affects nothing.

### Union variants

| Type | Tag | Fields |
|---|---|---|
| `Slot` | `allDay` | `days` |
| | `timed` | `start` (`HH:mm`), `end` (`HH:mm`), `endDayOffset` (always written) |
| `Repeat` | `never` | — |
| | `daily` | `interval`, `until?` |
| | `weekly` | `interval`, `days` (weekday names), `until?` |
| | `monthlyOnDay` | `interval`, `dayOfMonth` (1 to 31), `until?` |
| | `monthlyOnWeekday` | `interval`, `ordinal` (1 to 4, or −1), `weekday`, `until?` |
| | `yearly` | `interval`, `until?` |
| `Who` | `everyone` | — |
| | `members` | `members` (member ids) |
| `Taking` | `notNeeded` / `needsSomeone` | — |
| | `person` | `member` |
| `Reminder` | `minutesBefore` | `minutes` |
| | `daysBeforeAt` | `days`, `at` (`HH:mm`) |
| `Owner` | `none` | — |
| | `member` | `member` |

`interval` is always written. `until` is an inclusive original date, and absent means no end.

## Payload catalogue (schemaVersion 1)

| `type` | Class | Key | Fields (all optional unless noted) |
|---|---|---|---|
| `family.created` | `FamilyCreated` | — | `name`, `zone`, `weekStart` (all required). One per family. Its fields are the first writes of the settings. |
| `family.settings` | `FamilySettings` | — | `name`, `zone`, `weekStart` |
| `device.upsert` | `DeviceUpsert` | `deviceId` | `name`, `role`, `owner` |
| `device.removed` | `DeviceRemoved` | `deviceId` | — (SPEC FR-6) |
| `member.upsert` | `MemberUpsert` | `memberId` | `name`, `colorIndex` (0-based), `kind`, `archived` |
| `event.upsert` | `EventUpsert` | `eventId` | `title`, `icon`, `start`, `slot`, `repeat`, `termTimeOnly`, `who`, `taking`, `place`, `notes`, `reminders`, `deleted` |
| `event.change` | `EventChange` | `eventId` + `from` | `title`, `icon`, `slot`, `repeat`, `termTimeOnly`, `who`, `taking`, `place`, `notes`, `reminders`, `ended` |
| `event.edit` | `EventEdit` | `eventId` + `occurrence` | `movedTo`, `slot`, `title`, `icon`, `who`, `taking`, `place`, `notes`, `cancelled` |
| `holiday.upsert` | `HolidayUpsert` | `holidayId` | `name`, `first`, `last`, `deleted` |
| *(anything else)* | `UnknownPayload(type, json)` | | Kept and forwarded (SPEC FR-46). `task.*`, `meal.*` and `list.*` are reserved for future kinds (NFR-EXT-1). |
| *(known type, unreadable)* | `MalformedPayload(type, json, reason)` | | Kept and forwarded, and affects nothing |

**Which record can write which field.** The three event payloads are separate classes, so a field can't be written where the spec doesn't allow it:

| Field | `event.upsert` (all) | `event.change` (this and following) | `event.edit` (only this one) |
|---|---|---|---|
| `start` | ✓ | — (the key `from` plays this role) | — |
| `slot`, `title`, `icon`, `who`, `taking`, `place`, `notes` | ✓ | ✓ | ✓ |
| `repeat`, `termTimeOnly`, `reminders` | ✓ | ✓ | — |
| `deleted` | ✓ | — | — |
| `ended` | — | ✓ | — |
| `movedTo`, `cancelled` | — | — | ✓ |

**Known limitation (by design).** `who` is one register. If two devices each add a different person to the same event while apart, the later write wins and one addition is lost, as with House Points' chore assignees. A per-person register was considered and rejected: two concurrent removals could leave an event involving nobody, which is a worse surprise than a lost addition, and the lost addition is visible and easy to redo.

## Deterministic IDs (SPEC FR-54)

`CalendarIds` uses UUIDv5 under the fixed namespace `0d1ccd49-f501-4b47-9bb0-3e4dfc92f9bc`, which is distinct from House Points' (SPEC NG-5):

| Function | Name hashed |
|---|---|
| `externalEvent(uid)` | `ext:{uid}` |
| `externalHoliday(uid)` | `holiday:{uid}` |

Everything else uses `randomEvent`, `randomHoliday` or `randomMember`, which are time-ordered UUIDv7. The namespace and name formats are part of the contract, pinned by `CalendarIdsTest` vectors.

## Ports (defined in `:sync`, listed here because they cross module boundaries)

`OpLog`, `Transport` and `PeerLink` are as in SAD §4.4. `LinkPeer` carries the peer's `DeviceRole` from this module.

## Compatibility rules

1. **Minor bumps.** Each of these is a minor bump:
   - **A new payload type.** Older apps keep it as `UnknownPayload`.
   - **A new optional field** on an existing payload. Older apps ignore it when reading, and the stored body keeps it.
   - **A new union variant or open-set name.** Older apps read it as `Unrecognised` and apply the meaning `:schedule` gives that.
2. **Major bumps.** Each of these is a major bump, needing a new `schemaVersion` or envelope format byte, with migration rules written here first:
   - removing or renaming a field or tag
   - changing a type string
   - changing the envelope bytes
   - changing a deterministic-ID name format or the namespace.

---

## Confidence gate (freeze)

```
Confidence: high
Why: the module compiles in isolation with explicit API mode and no warnings. 29 tests pin the envelope
  bytes, every payload's round trip, the exact JSON of the union fields and the edit/change keys,
  clear-vs-absent, minute-precision times, unrecognised variants (read, kept, re-encoded), malformed
  bodies (never thrown), future types (unknown, never malformed), and the UUIDv5 vectors.
  Walking SAD §2:
  - :schedule consumes Op and every payload
  - :intake produces the Slot, Repeat, IconKey and CalendarIds values
  - :sync consumes Op, ids, Lamport, Seq and DeviceRole
  - :data consumes Op and OpCodec
  - :lan and :nearby consume DeviceId, FamilyId and DeviceRole.
Residual risks: none known in the contract. The `who` register limitation is recorded above.
Known gaps: none.
```
