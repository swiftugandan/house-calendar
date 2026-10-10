# Design

The visual system for House Calendar, following [impeccable.style](https://impeccable.style) conventions. Product context is in `PRODUCT.md`. Every colour, font, size and radius used in the app must come from this file.

## Direction: "Organiser"

The app is the family organiser from the kitchen wall: a column for each person, a ruled row for each day, entries written as short lines of text, and today circled in pen. It is House Points' sibling. It shares the type families, the ink-and-paper neutrals and the action green, so the two apps look related on the same phone. The motif is carried by **the grid, the rules and the pen ring**, not by imitation paper. There's no paper texture, no torn edges and no handwriting font.

**Alternatives considered:**
- **Fridge door:** sticky notes, magnets, a cork texture. *Rejected:* skeuomorphic clutter, illegible from across the room, and every note competes with every other.
- **Productivity calendar:** an hour grid with coloured blocks, as in desktop calendar apps. *Rejected:*
  - a 24-hour axis spends most of the screen on empty hours
  - block colour is the only cue for whose event it is
  - it is dense at 2 m
  - it answers "when am I free?", where a family asks "where is everyone?"
- **Smart display dashboard:** dark, with weather tiles, a big clock and photo backgrounds. *Rejected:* it is the hero-metric template, it needs the internet for half its tiles, it invites burn-in, and the calendar becomes one tile among many.

**What makes it recognisable:**
1. **The organiser grid.** People across the top, each with a coloured disc and name. Days down the side. Thin rules between them. The person's colour lives in their **header disc** and nowhere else in their column, so the content stays readable ink on paper.
2. **The pen ring.** Today's date number is circled in `today` vermilion, as a parent would circle it on paper. The same colour draws the now line. It is never used for anything else.
3. **The empty disc.** A lift that needs someone is a dashed, empty disc where the person would be. When someone takes it, the disc fills with their colour.

## Colour

The palette is fixed. **Material dynamic colour is off**, because a person's colour must be identical on every device in the family.

Neutrals are tinted towards blue-green ink, as in House Points. All contrast figures were computed with the WCAG 2.2 relative-luminance formula [SPEC R16]. The app's token contrast test (`TokensContrastTest`) recomputes them, so a token change that breaks a ratio fails the build.

### Light

| Token | Hex | Use | Contrast on paper |
|---|---|---|---|
| `paper` | `#F5F7F6` | App background; past-day rows | n/a |
| `surface` | `#FFFFFF` | Sheets; today's row | n/a |
| `sunk` | `#E9EEEC` | Pressed states, input wells, the month grid's other-month days | n/a |
| `band` | `#E4EAE7` | Holiday and "everyone" bands | n/a |
| `rule` | `#D3DAD7` | Grid rules, dividers, holiday hatching (decoration only, never the only cue) | n/a |
| `ink` | `#18232B` | Text, times | 14.9:1 (13.1:1 on `band`) |
| `ink-muted` | `#52606A` | Ended entries, secondary text, places | 6.0:1 (5.3:1 on `band`) |
| `action` | `#1E6B4E` | The single primary action; confirmation | 6.0:1 (white on it 6.4:1) |
| `today` | `#B3361C` | The pen ring and the now line, nothing else | 5.6:1 (white on it 6.1:1) |

### Dark

| Token | Hex | Contrast on ground |
|---|---|---|
| `ground` | `#121A1F` | n/a |
| `surface` | `#1B252B` | n/a |
| `sunk` | `#243037` | n/a |
| `band` | `#22303A` | n/a |
| `rule` | `#2C3940` | n/a |
| `ink` | `#E6EDEA` | 14.8:1 |
| `ink-muted` | `#A3B1AB` | 7.9:1 |
| `action` | `#6CC9A0` (text `#0D1A14`, 8.9:1) | 8.8:1 |
| `today` | `#FF8F6B` (text `#1A0D08`, 8.5:1) | 7.9:1 |

The display uses dark from 30 minutes before the end of day hours, and outside day hours its night screen is `ground`, with `ink-muted` text at minimum brightness. Phones follow the system setting.

### Member colours

There are 8, assigned in order and reused cyclically after that. The first 6 are House Points' child colours, so a child has the same colour in both apps.

| Token | Light | Dark | White on light | Light on paper | Dark on ground |
|---|---|---|---|---|---|
| `member-1` | `#2456A6` | `#8DB4F2` | 7.1:1 | 6.6:1 | 8.3:1 |
| `member-2` | `#0E6E73` | `#6FCFD3` | 6.0:1 | 5.6:1 | 9.7:1 |
| `member-3` | `#7B3F8C` | `#D3A2E0` | 7.2:1 | 6.7:1 | 8.4:1 |
| `member-4` | `#5C6B12` | `#BFD06A` | 5.9:1 | 5.5:1 | 10.4:1 |
| `member-5` | `#4A44A8` | `#ADA9F5` | 7.8:1 | 7.3:1 | 8.2:1 |
| `member-6` | `#A63A62` | `#F2A0BF` | 6.2:1 | 5.7:1 | 8.8:1 |
| `member-7` | `#7A5A00` | `#E3B55B` | 6.4:1 | 5.9:1 | 9.2:1 |
| `member-8` | `#3D5A73` | `#9DB8CF` | 7.2:1 | 6.7:1 | 8.5:1 |

On dark, initials on a member disc are `ground` (all ≥ 8.2:1).

### Colour rules

- A member's colour appears on their **disc** and their **column header**, and as a 4 dp marker before entries in a mixed list (the agenda, the month). Never as an entry's background fill.
- `today` is only the pen ring and the now line.
- Clashes and "needs someone" are **never** coloured as warnings. They are ink text with a glyph.
- Colour is never the only signal. Discs carry initials, and entries in mixed lists carry the person's name for TalkBack.
- No gradients anywhere.

## Typography

There are three families, the same as House Points, all under the SIL Open Font License and bundled in the APK (no network font loading).

| Role | Family | Measured x-height | Why |
|---|---|---|---|
| Display: dates, names in headers, the clock | **Bricolage Grotesque** 600/700 | 0.528 em | Has character without being cute. Its optical-size axis (12 to 96) keeps it crisp from phone to wall. |
| Interface and body text | **Atkinson Hyperlegible Next** 400/700 | 0.496 em | Keeps easily confused characters (`1 l I`, `0 O`) distinct, which matters for children reading times. |
| Times and figures | **Atkinson Hyperlegible Next** with tabular figures (`tnum`) | 0.496 em | Times line up in a column. Same legibility as the body text. *Was the Mono face: its 0.632 em advance made 12-hour times ("12:00 PM") collide with the people discs on phones and crowd the display's columns; changed after the first emulator screenshots, 2026-10-10.* |

### Phone type scale (sp)

| Token | Size / line height | Family and weight | Use |
|---|---|---|---|
| `headline` | 26 / 32 | Bricolage 600 | Screen titles, the day heading on the agenda |
| `title` | 18 / 24 | Atkinson Next 700 | Entry titles, sheet sections |
| `body` | 16 / 24 | Atkinson Next 400 | Notes, explanations |
| `label` | 14 / 20 | Atkinson Next 700 | Buttons, segmented controls, section labels |
| `caption` | 14 / 20 | Atkinson Next 400, `ink-muted` | Place, "Sam taking", sync line |
| `time` | 16 / 24 | Atkinson Next 400, tabular | Times in lists |
| `time-strong` | 18 / 24 | Atkinson Next 700, tabular | The time in an event's detail |

The smallest text anywhere on a phone is 14 sp. There's no all-caps text. Headings never use italics.

### Display type: sized in millimetres

Display-mode sizes are **physical**. Each tier is an x-height in millimetres (SPEC NFR-A11Y-4), converted per device:

`fontSizePx = xHeightMm ÷ xHeightRatio × xdpi ÷ 25.4`

The conversion uses the screen's reported physical `xdpi`. If that is implausible (below 90 or above 640), the density bucket is used instead, and display settings offer a **size check**: a 5 cm bar for the person to compare with a ruler, and a scale slider.

| Tier | x-height | Use | On a 10" 1280×800 (≈150 ppi) |
|---|---|---|---|
| `glance` | 7.0 mm (0.2° at 2 m) | Today's date, the clock, Today view's names and now/next titles | Bricolage ≈ 78 px; Atkinson ≈ 83 px |
| `glance-small` | 7.0 mm, but with no ascenders needed | Day numbers in the Week view | Bricolage ≈ 78 px |
| `detail` | 2.1 mm (0.2° at 0.6 m) | Everything else: entries, names in headers, labels | Atkinson ≈ 25 px; Mono ≈ 25 px |

Line height is 1.2 × size for `detail` and 1.1 × size for `glance`.

**Times on the display** use Atkinson Hyperlegible Next with tabular figures (`tnum`) rather than the mono face, and the clock uses Bricolage with tabular figures. At these sizes the mono face's wide advance (0.632 em) made a 12-hour "5:00 PM" take 4.4 em of a 11 em column. Tabular figures keep times aligned in a column, which was the reason for mono. *Changed after the first screenshots on the 10" emulator (2026-10-10).*

**Entry lines in narrow columns.** A column narrower than 13 em puts the time on its own line above the title when every entry of the cell fits that way. Otherwise entries stay on one line with a shortened title, and "+N more" appears only when even one-line entries don't fit. Today's row is weighted ×1.6.

**Fit check (10" landscape, 1280×800 px).**
- **Vertical:** the header (date, clock and sync) takes ≈ 110 px and the people row ≈ 56 px, leaving ≈ 620 px for days.
- **Rows:** with today weighted ×2 over 7 days, a normal row is ≈ 77 px. That's two 32 px lines plus padding, and today has four.
- **Columns:** at ≤ 4 members, a column is ≈ 279 px. That fits "17:00 Swimming" on one line (≈ 12 px per character). At 5 or 6 members, columns are ≈ 186 to 223 px, so the time sits above the title. The Week view then shows **5 days** to keep two entries per cell, and is swiped for more.
- **Portrait (800×1280):** 7 days fit with four lines per row at ≤ 4 members.

## Space and shape

- **Spacing scale (dp):** 4, 8, 12, 16, 24, 32, 48. The phone's side margin is 16. The display's is 24. Related items use 8; separate groups use 24 or more.
- **Radius:** `r-sm` 4 dp (inputs, segmented controls, entry markers), `r-md` 12 dp (sheets, buttons), `r-full` (discs, the pen ring). Nothing else.
- **Depth:** only sheets lift, with one shadow token. Everything else is separated by tone (`paper` vs `surface` vs `band`) or rules.
- **No cards.** Entries are lines in a ruled grid or list. A sheet is the only container that floats.

## Iconography

- **Icon set:** a single bespoke set of 2 dp rounded strokes on a 24-unit grid, drawn the same way as House Points' (`HpIcons`). It is bundled as Compose `ImageVector`s, with no network and no icon font.
- **Sizes:** UI icons are 24 dp. Event icons are 20 dp in phone lists and match the `detail` cap height on the display.
- **Event icons** are keyed by an `IconKey` string stored in ops. The v1 catalogue, which is also the picker order: `swim`, `football`, `run`, `bike`, `dance`, `gym`, `music`, `art`, `book`, `school`, `graduation`, `doctor`, `tooth`, `pill`, `cake`, `party`, `gift`, `food`, `cook`, `shop`, `car`, `bus`, `train`, `plane`, `holiday`, `tent`, `home`, `work`, `phone`, `bin`, `recycle`, `wash`, `pet`, `vet`, `haircut`, `church`, `film`, `game`, `star`, `heart`.
- **Keyword suggestions (SPEC FR-14)** map English words to those keys ("swim", "swimming", "pool" → `swim`; "dentist", "orthodontist" → `tooth`; "birthday" → `cake`). The table lives with the icons and is unit-tested.
- Icons never sit in a tinted tile above a heading.

## Components

**Person disc.** A filled circle in the member's colour, with the initial in Bricolage 700 at about 45% of its diameter. The size is 40 dp in pickers and headers, 20 dp inline, and `detail` cap height × 1.6 on the display.
- **Empty disc:** dashed 2 dp `ink-muted` ring, no fill, with the label "Needs someone".
- **Group disc:** a ring of 2 dp `ink` with "All", for everyone.

**Entry line (the core unit).** One line where it fits:
- `time` in tabular figures (`ink-muted` once ended), a space, the event icon, then the title in `title` weight.
- The second line, in `caption`, holds the place, then "· Sam taking" with a 20 dp disc, or "· Needs someone" with the empty disc.
- A repeating occurrence adds the `repeat` glyph (12 dp) after the title.
- In a member's column, other people involved appear as 20 dp discs at the end of the first line.
- All-day entries have no time. They sit above timed ones with a 4 dp left marker in `rule`.

**Organiser grid (display Week view).**
- **Day label column:** the weekday in `detail` and the day number in `glance-small`. Today's number gets the **pen ring**: a 2 dp `today` circle, slightly irregular (rotated 6°, 3% oval), the one hand-drawn gesture in the app.
- **Rows:** past days are hidden, so the grid always starts today. Today's row is `surface`. The other rows are `paper`, with 1 dp `rule` lines between them.
- **Everyone band:** occurrences for everyone are drawn once at the top of the day row as a `band` strip across all member columns, with "Everyone" as a group disc.
- **Holiday band:** a day inside a holiday has its label cell hatched with 45° `rule` lines 6 dp apart and the holiday name in `caption`.
- **"+N more":** the last line of a cell that overflows reads "+2 more" and opens that day.

**Today board (display Today view).** One row per member:
- their disc and name (`glance`)
- **Now:** the current occurrence's title (`glance`) and "until 15:15" (`detail`)
- **Next:** its time (tabular figures), its title, and "Sam taking" (`detail`).

An everyone row comes first when an everyone occurrence is now or next.

**Agenda row (phone).**
- `time` column, 56 dp wide.
- A stack of the people's 20 dp discs, or the group disc.
- The entry line.

Day headings are sticky (`headline` for today and tomorrow, `title` for later days), with the holiday name in `caption` beside the date. The **now line** is a 2 dp `today` rule between the last started entry and the next one, with the time in `today` at its left.

**Add sheet.** A bottom sheet on phones. On the display it is a centred sheet 720 dp wide with 64 dp targets. Worked from top to bottom:
1. **What:** the title field, focused, with suggestions from earlier events below it (SPEC FR-13) and the suggested icon at its start.
2. **Who:** person discs as toggles (40 dp, multi-select) and "Everyone".
3. **When:** the day (Today / Tomorrow / a date chip opening a picker), All day or a time range, and the end day if it's later.
4. **Repeat:** Never / Every day / Every week (with weekday toggles) / Every 2 weeks / Every month (on the 13th, or on the 2nd Tuesday) / Every year / Custom. Then "Ends" (never / on a date / after N times), and **Term time only**, shown only when holidays exist.
5. **Taking:** Not needed / Needs someone / an adult's disc (adults first, then "Someone else…").
6. **More:** place, notes and reminders, folded.
7. **Confirm:** one `action` button whose label says what will happen, for example "Add Swimming · Tuesdays from 13 Oct" or "Add Dentist · Thu 15 Oct 16:20". Never "Save" or "Done".

A clash that the new event would create appears as one `caption` line above the button (SPEC FR-34).

**Scope question.** A small sheet with three full-width choices that use the event's own day words:
- "Only Tue 20 Oct"
- "Tue 20 Oct and following Tuesdays"
- "Every Swimming"

The default focus is the first.

**Import review (SPEC FR-50, FR-55).** A list of ticked rows (date, time, title). Skipped items are explained at the bottom in `caption`. The action reads "Add 12 events" or, when all are all day, the pair "Add 3 as holidays" (primary) and "Add as events". Rows already on the calendar read "Already on the calendar · will update".

**Sync line.** One line under the phone's header, or at the display's top right: "Up to date with the kitchen · 2 min ago". A spinner appears only while a session is running. There is no pulsing dot.

**Clash line.** `caption` ink with a small overlap glyph: "Sam is also taking Tom to Football at 17:30". It sits on both occurrences' detail, and in the add sheet.

**Needs someone list (phone).** Ruled rows: date, entry line, and to the right the adults' discs as one-tap "I'll take" buttons, "Me" first.

## Screens and what wins each one

| Screen | Wins the eye | Supporting |
|---|---|---|
| **Agenda (phone home)** | The Add button (bottom, full width, `action`) | Today's entries, the now line, the sync line, "2 need someone" |
| **Week (phone)** | Today's row | Day rows with entry lines |
| **Month (phone)** | Today's ring | Per-person marks, up to 3 titles a day |
| **Event detail** | Time, place and taking, as one block | Who, repeat summary, reminders, "Added by Sam's phone", clashes |
| **Add sheet** | The outcome button | What / Who / When / Repeat / Taking |
| **Needs someone** | The "I'll take" discs | Dates and titles |
| **Import review** | "Add 3 as holidays" or "Add 12 events" | The ticked list, what was skipped |
| **Display: Week** | Today's row, with the pen ring | The rest of the week, the people row, the clock |
| **Display: Today** | Each person's Now | Next, who's taking |
| **Display: night** | Nothing (dim clock) | n/a |
| **Sync / devices** | "Show QR to add a device" | The device list with last-synced times |

## Display mode behaviour

- **Navigation:** three segmented views at the top right (Week / Today / Month), with Add bottom right as a 64 dp `action` disc with a plus. There's no other chrome.
- **Settings:** opened by the gear in the sync line. It needs the screen lock, or a 2-second hold with a ring that fills while held (SPEC FR-31).
- **Idle return:** after 2 minutes the view cross-fades (200 ms) back to Week, scrolled to today. An open sheet closes first, and a draft keeps its fields until the next time Add is opened.
- **Day rollover:** at midnight, yesterday's row leaves the grid without animation, the way a page would be turned.
- **Burn-in:** the whole layout shifts by up to 2 px in a slow circle every 10 minutes. That is invisible at 2 m and protects OLED tablets.

## Motion

Motion is restrained and has a purpose. Every animation respects the system "remove animations" setting.

| Moment | Motion |
|---|---|
| Sheet open/close | Standard Material sheet motion, 250 ms, emphasised deceleration |
| Switching views | Cross-fade 150 ms. Nothing slides. |
| New entry appearing (local or synced) | Fades in over 150 ms. It doesn't move its neighbours. |
| **A lift is taken** (the one moment of delight) | The empty disc's dashes close into a ring (120 ms), then it fills with the person's colour from the centre (200 ms, ease-out, no overshoot) |
| Pen ring on a new day | Drawn as a stroke, 400 ms, ease-in-out, the first time the display shows the new day |

**Banned motion:** confetti, bounce or elastic easing, pulsing dots, shimmer placeholders (local data loads instantly), parallax, and animated size changes that move neighbouring elements.

## Copy patterns

- Buttons say what will happen: "Add Swimming · Tuesdays from 13 Oct", "I'll take Ada", "Move only this one".
- Day words, not dates, near now: "Today", "Tomorrow", "Thu", and then "Tue 20 Oct".
- Repeat summaries in plain words: "Every Tuesday in term time, until 15 Dec".
- Times use the device's 12- or 24-hour setting. Ranges use "to" in speech and an en dash only between times ("17:00–18:00").
- "Taking" is always "{Name} taking" or "Needs someone". Never "driver", because not every lift is a car.

## Large text

On phones at a font scale of 1.5 or more, segmented controls stack into a full-width list, and agenda rows move the time above the title rather than clipping (SPEC NFR-A11Y-2). Display mode ignores the system font scale. Its sizes are already physical, and its size check is the control for them.

## Bans

The general impeccable slop checks apply. These bans are specific to this app:

- Hour grids with coloured blocks as the main view.
- Red badges, unread counts, warning colours for clashes or missing lifts, modal alerts for anything a sync brings.
- Streaks, "productivity" stats, "You're all caught up!", celebration effects.
- Weather, news, photos or any tile that needs the internet.
- Gradients, glassmorphism, glows, neon on dark, purple-to-cyan palettes, cream or beige surfaces.
- Cards inside cards. Icon tiles above headings. Eyebrow chips. Numbered section labels.
- A person's colour as an entry's background fill.
- Inter, Roboto or other system fonts as the visible face, except in widgets, which can't load bundled fonts.
