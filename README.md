# House Calendar

The family calendar from the kitchen wall, on an Android tablet in the same spot and on the parents' phones.
- **The display** shows the family's week as a column for each person and a row for each day, like a paper family organiser.
- **The phones** carry the same calendar, with reminders for the things you're in or taking someone to.
- **Lifts are first-class.** Every event says who is taking them, or shows that it still needs someone.
- **Term time.** Repeating clubs skip the school holidays you've entered.

There's no account, no server and no internet. The devices sync with each other directly over the home Wi-Fi, or over Bluetooth. House Calendar is the sibling of [House Points](../house-points).

## Status

Version 1 is complete and verified on emulators (phone and 10" tablet): events and activities, term time, lifts, reminders, sync, display mode, adding from other apps, and export. Sync over real Wi-Fi, the Bluetooth fallback, and running on the actual kitchen tablet still need physical devices. See [REVIEW.md](REVIEW.md). Screens are in [docs/screens/](docs/screens/).

## How it's built

Every calendar is a pure function of an append-only log of operations. Devices exchange the operations they're missing, and two devices holding the same operations always show the same calendar.

| Module | What it is |
|---|---|
| `contracts` | The frozen vocabulary: IDs, calendar value types, the op envelope and payloads, and the codec (pure JVM) |
| `schedule` | Ops in, calendar out: repeat rules, term time, the agenda, instants across daylight saving, clashes, reminders, edit scopes (pure JVM) |
| `intake` | Untrusted input from other apps: `.ics` files, "Add to calendar" intents, shared text (pure JVM) |
| `sync` | Op factory, version vectors, the authenticated and encrypted sync protocol, and who dials whom (pure JVM, copied from House Points) |
| `data` | SQLite op log, Keystore-wrapped family key, device identity and settings, encrypted export (Android) |
| `lan` | Same-Wi-Fi discovery (mDNS) and TCP transport (Android) |
| `nearby` | Bluetooth fallback through Nearby Connections (Android) |
| `app` | Jetpack Compose UI: the phone screens and the kitchen display mode (Android) |

## Design documents

| Document | Purpose |
|---|---|
| [VISION.md](VISION.md) | One paragraph |
| [CONCEPT.md](CONCEPT.md) | The idea, for families |
| [SPEC.md](SPEC.md) | Requirements, and the worked examples that are also the tests |
| [SAD.md](SAD.md) | Architecture |
| [CONTRACTS.md](CONTRACTS.md) | The contracts module |
| [PLAN.md](PLAN.md) | Build order and acceptance |
| [PRODUCT.md](PRODUCT.md), [DESIGN.md](DESIGN.md) | Product context and the visual system ("Organiser") |
| [REVIEW.md](REVIEW.md) | Decisions taken while the owner was away, and what is verified where |

## Building

You need JDK 17 and an Android SDK with platform 35 (set `sdk.dir` in `local.properties`).

```sh
./gradlew test                    # contracts, schedule, intake, sync on the JVM
./gradlew testDebugUnitTest       # Android modules' JVM tests
./gradlew :schedule:benchmark     # the sizing benchmark (NFR-PERF-1), reported, not gating
./gradlew :app:installDebug       # installs "House Calendar (debug)" beside any release install
./gradlew :app:assembleRelease    # signed if housecalendar.signing.* are set in ~/.gradle/gradle.properties
```

## Fonts

Bricolage Grotesque and Atkinson Hyperlegible (Next and Mono) are bundled under the SIL Open Font License. See [licenses/](licenses/).

## Licence

MIT. See [LICENSE](LICENSE). The bundled fonts keep their own SIL Open Font License.
