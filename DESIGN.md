# Design

The visual system for House Calendar (direction "Kitchen Light", v0.2), following [impeccable.style](https://impeccable.style) conventions. Product context is in `PRODUCT.md`. Every colour, font, size and radius used in the app must come from this file.

## Direction: "Kitchen Light" (v0.2)

The calendar as a well-made object: the cool, clear light of a kitchen in the morning, white cards resting on it, and each person's colour carried through everything that's theirs. It aims for the craft of first-party Apple apps and the best of Dribbble: confident type, real depth, generous space, and one idea about colour carried all the way through. It keeps the parts of "Organiser" that worked: the organiser grid on the wall, the pen ring, the empty disc, physical type sizes, and legibility as a requirement rather than a style.

**What "Organiser" (v0.1) got wrong.** It was principled but flat. It banned cards, depth and fills in a person's colour, so every screen became ink lines on grey paper. The one thing a family calendar has to say at a glance, *whose is this?*, was carried by a 20 dp disc. The rules kept it safe at the cost of making it look unfinished.

**Alternatives considered for v0.2:**
- **Dark smart-display dashboard:** big clock, photo background, glass tiles. *Rejected:* it's the hero-metric template, glass and photos fight legibility at 2 m, and it invites burn-in.
- **Full-colour blocks**, as in Google Calendar's week view: saturated person-colour backgrounds with white text. *Rejected:* eight saturated colours in one week read as noise, and white text on mid-tones fails contrast for half the palette.
- **Monochrome editorial:** black and white, oversized type, colour only on discs. *Rejected:* beautiful in a screenshot, but it hides the one thing families need, whose is what.

**What makes it recognisable:**
1. **Person tint.** An entry that belongs to someone sits on a card tinted with their colour: 13% of it over white in light, 16% over the card tone in dark. Text stays `ink`, so contrast never depends on the hue. A whole week shows at a glance whose it is, without becoming a rainbow.
2. **The colour rail.** Every entry card starts with a 4 dp vertical rail, split into one segment per person in it, in their full colour. "Everyone" entries carry every member's segment. It's the signature mark: a family, in one stroke.
3. **The pen ring**, kept: today circled in coral, slightly irregular, the one hand-drawn gesture. Coral also draws the now line, and nothing else.
4. **The empty disc**, kept: a dashed ring where a person would be, for a lift that still needs someone.

## Colour

The palette is fixed. **Material dynamic colour is off**, because a person's colour must be identical on every device in the family. Neutrals are cool and faintly blue: never beige, never warm grey. Every pairing below is recomputed by `FoundationsTest` (WCAG 2.2 relative luminance [SPEC R16]), so a token change that breaks a ratio fails the build.

### Light

| Token | Hex | Use | Contrast |
|---|---|---|---|
| `ground` | `#F2F4F6` | App background, behind cards | n/a |
| `surface` | `#FFFFFF` | Cards, sheets, today's row on the wall | n/a |
| `sunk` | `#E8ECEF` | Input wells, segmented-control track, pressed states | n/a |
| `band` | `#EEF1F3` | Neutral cards: everyone, holidays, all-day | n/a |
| `rule` | `#DCE2E6` | Hairlines, card borders in dark, holiday hatching | n/a |
| `ink` | `#0E161D` | Text, icons | ≥ 15.4:1 on every ground |
| `ink-muted` | `#55616C` | Secondary text | ≥ 5.3:1 on every ground, ≥ 5.2:1 on every tint |
| `action` | `#0E161D` | Primary buttons: an ink pill | white on it 18.2:1 |
| `today` | `#C2351B` | The pen ring, the now line and its time | ≥ 4.6:1 on every ground; white on it 5.5:1 |

### Dark

| Token | Hex | Contrast |
|---|---|---|
| `ground` | `#0B0F13` | n/a |
| `surface` | `#161C22` | n/a |
| `sunk` | `#1F272E` | n/a |
| `band` | `#1A2128` | n/a |
| `rule` | `#28313A` | n/a |
| `ink` | `#EEF2F4` | ≥ 13.4:1 |
| `ink-muted` | `#9EAAB4` | ≥ 6.4:1, ≥ 5.0:1 on every tint |
| `action` | `#EEF2F4`, a light pill, text `#0B0F13` | 17.1:1 |
| `today` | `#FF7B5C`, text `#1A0B06` | ≥ 5.9:1 |

### Member colours

There are 8, assigned in order and reused cyclically. They're unchanged from v0.1, and the first 6 are House Points' child colours, so a child has the same colour in both apps.

Each member colour has three uses:
- **fill:** the disc behind the initials, and the rail segment.
- **tint:** the card background, the fill blended over `surface` at 13% (light) or 16% (dark).
- **accent:** text in the member's colour on `surface` or their tint: names on the wall's column headers.

| Token | Light fill | Light tint | Dark fill | Dark tint |
|---|---|---|---|---|
| `member-1` | `#2456A6` | `#E3E9F3` | `#8DB4F2` | `#293443` |
| `member-2` | `#0E6E73` | `#E0ECED` | `#6FCFD3` | `#24393E` |
| `member-3` | `#7B3F8C` | `#EEE6F0` | `#D3A2E0` | `#343140` |
| `member-4` | `#5C6B12` | `#EAECE0` | `#BFD06A` | `#31392E` |
| `member-5` | `#4A44A8` | `#E7E7F4` | `#ADA9F5` | `#2E3344` |
| `member-6` | `#A63A62` | `#F3E5EB` | `#F2A0BF` | `#39313B` |
| `member-7` | `#7A5A00` | `#EEEADE` | `#E3B55B` | `#37342B` |
| `member-8` | `#3D5A73` | `#E6EAED` | `#9DB8CF` | `#2C353E` |

Checked pairings: `ink` and `ink-muted` on every tint (≥ 14.9:1 and ≥ 5.2:1 light, ≥ 10.6:1 and ≥ 5.0:1 dark); each fill on its own tint (≥ 4.9:1); initials on fills (white ≥ 5.9:1 light, `ground` ≥ 8.0:1 dark).

### Colour rules

- An entry belonging to one person sits on that person's **tint**. An entry for several people takes the **first** person's tint, and its rail shows all of them. An entry for everyone sits on `band`, with every member in its rail.
- `today` is only the pen ring, the now line and its time.
- Clashes and "needs someone" are **never** coloured as warnings. They're ink text with a glyph.
- Colour is never the only signal. Discs carry initials, rails sit beside discs or names, and every entry's spoken description names its people.
- No gradients, no glass, no glow.

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
| `large-title` | 34 / 40 | Bricolage 700, −1% tracking | The home screen's day ("Saturday"), screen titles on first-level pages |
| `headline` | 24 / 30 | Bricolage 700 | Day headings for today and tomorrow, sheet titles, top bars |
| `title` | 17 / 22 | Atkinson Next 700 | Entry titles, section headings |
| `body` | 16 / 24 | Atkinson Next 400 | Notes, explanations, list rows |
| `label` | 15 / 20 | Atkinson Next 700 | Buttons, chips, segmented controls |
| `caption` | 14 / 20 | Atkinson Next 400, `ink-muted` | Place, "Sam taking", sync line |
| `time` | 15 / 20 | Atkinson Next 700, tabular | An entry's start time |
| `time-strong` | 18 / 24 | Atkinson Next 700, tabular | The time in an event's detail |

The smallest text anywhere on a phone is 14 sp. There's no all-caps text, except the three-letter weekday heads of the month grid. Headings never use italics.

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

- **Spacing scale (dp):** 4, 8, 12, 16, 24, 32, 48. The phone's side margin is 16 (20 inside grouped lists). The display's is 24. Related items use 8; separate groups use 24 or more.
- **Radius:**
  - `r-sm` 8 dp: chips' inner elements, icon tiles, the segmented thumb
  - `r-md` 14 dp: inputs, buttons that aren't pills, month tiles
  - `r-lg` 22 dp: cards and entry cards
  - `r-sheet` 28 dp: the top corners of sheets
  - `r-full`: discs, pills, the pen ring.
- **Depth, light:** two shadow tokens, both cool-tinted (`#0E161D`):
  - `raised`, for cards: a 1 dp contact shadow at 5% plus an 8 dp ambient at 6%
  - `floating`, for sheets, the Add pill and the segmented thumb: a 2 dp contact at 8% plus a 24 dp ambient at 12%.
- **Depth, dark:** shadows vanish on a dark ground, so cards are lifted by tone (`surface` on `ground`) and a 1 dp `rule` hairline instead.
- **Grouped lists:** settings, detail and pickers group related rows into one `r-lg` card with hairlines between rows, as in iOS Settings. A card never sits inside another card.

## Iconography

- **Icon set:** a single bespoke set of 2 dp rounded strokes on a 24-unit grid, drawn the same way as House Points' (`HpIcons`). It is bundled as Compose `ImageVector`s, with no network and no icon font.
- **Sizes:** UI icons are 24 dp. Event icons are 20 dp in phone lists and match the `detail` cap height on the display.
- **Event icons** are keyed by an `IconKey` string stored in ops. The v1 catalogue, which is also the picker order: `swim`, `football`, `run`, `bike`, `dance`, `gym`, `music`, `art`, `book`, `school`, `graduation`, `doctor`, `tooth`, `pill`, `cake`, `party`, `gift`, `food`, `cook`, `shop`, `car`, `bus`, `train`, `plane`, `holiday`, `tent`, `home`, `work`, `phone`, `bin`, `recycle`, `wash`, `pet`, `vet`, `haircut`, `church`, `film`, `game`, `star`, `heart`.
- **Keyword suggestions (SPEC FR-14)** map English words to those keys ("swim", "swimming", "pool" → `swim`; "dentist", "orthodontist" → `tooth`; "birthday" → `cake`). The table lives with the icons and is unit-tested.
- In entry cards, the event icon sits in a 32 dp `r-sm` tile of the card's fill colour at 18%. Icons never sit in a tile above a heading.

## Components

**Person disc.** A filled circle in the member's colour, with the initial in Bricolage 700 at about 45% of its diameter. The size is 40 dp in pickers and headers, 18 to 24 dp inline, and `detail` cap height × 1.6 on the display. A disc overlapping another carries a 2 dp ring of the surface behind it, so stacks read as separate people.

**Person chip.** In pickers and filters: an `r-full` pill holding the disc and name. Unselected, it's `surface` with a hairline. Selected, it fills with the person's tint and its border takes their colour, with a check.

**Buttons.** The primary action is an `action` pill (56 dp, `label`), floating on phones with the `floating` shadow. Secondary actions are `surface` pills with a hairline. Destructive actions are ink text, never red.

**Segmented control.** A `sunk` `r-full` track with a `surface` thumb carrying the `floating` shadow. The thumb slides between options (220 ms).
- **Empty disc:** dashed 2 dp `ink-muted` ring, no fill, with the label "Needs someone".
- **Group disc:** a ring of 2 dp `ink` with "All", for everyone.

**Entry card (the core unit).** An `r-lg` card on the owner's tint (or `band` for everyone), with the **colour rail** at its leading edge, inset 6 dp from the top and bottom:
- **Phone:** the start time in `time` with the end below it in `caption` (a measured column, so 12-hour times never clip), the icon tile, then the title in `title` and a second line in `caption`: the place, then "· Sam taking" with a 18 dp disc, or "· Needs someone" with the empty disc. The people's discs stack at the trailing edge.
- A repeating occurrence adds the `repeat` glyph after the title.
- Ended entries fade to 55%. They're never hidden.
- At a font scale of 1.5 or more, the times move to their own line above the title.

**Organiser grid (display Week view).**
- **People row:** each column is headed by a `surface` pill holding the person's disc and their name in their accent colour.
- **Day label column:** the weekday in `detail` and the day number in `detail-strong`. Today's number is larger and wears the **pen ring**.
- **Rows:** the grid starts today. Today's row is a `surface` card with the `raised` shadow, so the day you're in lifts off the wall. Other rows sit on `ground`, separated by hairlines. Rows share the height by what they hold: an empty day is slim (0.8 of a row, still enough for its label), each entry past the first in the busiest cell adds 0.8, and an everyone band adds 0.75. Today is never less than 1.6.
- **Entries:** compact entry cards (`r-md`, the person's tint, a 3 dp rail), each holding the time and the title on one line, or two when the column is narrow.
- **Everyone:** occurrences for everyone are one `band` card across all columns, with the full rail and the group disc.
- **Holiday:** the day label is hatched with 45° `rule` lines, with the holiday name in `caption`.
- **"+N more":** cells measure their entries as drawn, and the last line reads "+2 more" when the rest don't fit.

**Today board (display Today view).** A grid of person cards, each one on that person's tint, with their rail at the leading edge:
- the disc (glance size) and the name in their accent colour
- **Now:** the current title in `glance`, with "until 15:15" beside the name; or "Nothing on now" in `detail`, leaving the room to Next
- **Next:** "Next · Tomorrow 12:00" in `detail`, the title in `detail-strong`, and who's taking.

The grid is 1 card across for one person, 2 for 2 to 4, 3 for 5 or 6. A larger family scrolls a list. An everyone card leads when an everyone occurrence is now or next.

**Agenda (phone home).**
- A `large-title` header: the weekday, with the date beneath in `caption` and today's day number in the pen ring.
- Day headings are sticky: `headline` for today and tomorrow, `title` for later days, with the holiday beside the date.
- Entry cards, 8 dp apart. The **now line** is a 2 dp `today` rule with a 6 dp dot at its start and the time in `today`.
- "1 needs someone in the next fortnight" is a neutral card with the empty disc, above the list.

**Add sheet.** A bottom sheet on phones. On the display it is a centred sheet 720 dp wide with 64 dp targets. Worked from top to bottom:
1. **What:** the title field, focused, with suggestions from earlier events below it (SPEC FR-13) and the suggested icon at its start.
2. **Who:** person discs as toggles (40 dp, multi-select) and "Everyone".
3. **When:** the day (Today / Tomorrow / a date chip opening a picker), All day or a time range, and the end day if it's later.
4. **Repeat:** Never / Every day / Every week (with weekday toggles) / Every 2 weeks / Every month (on the 13th, or on the 2nd Tuesday) / Every year / Custom. Then "Ends" (never / on a date / after N times), and **Term time only**, shown only when holidays exist.
5. **Taking:** Not needed / Needs someone / an adult's disc (adults first, then "Someone else…").
6. **More:** place, notes and reminders, folded.
7. **Confirm:** one `action` pill, floating at the bottom of the sheet, whose label says what will happen, for example "Add Swimming · Tuesdays from 13 Oct" or "Add Dentist · Thu 15 Oct 16:20". Never "Save" or "Done".

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

Motion is quick, physical and has a purpose. Every animation respects the system "remove animations" setting.

| Moment | Motion |
|---|---|
| Sheet open/close | Spring (damping 0.85, stiffness medium-low), no overshoot visible |
| Segmented thumb | Slides to the chosen option, 220 ms, emphasised easing |
| Switching views | Cross-fade 180 ms |
| Pressing a card or button | Scales to 97% while held (spring back on release) |
| New entry appearing (local or synced) | Fades in over 180 ms. It doesn't move its neighbours. |
| **A lift is taken** (the one moment of delight) | The empty disc's dashes close into a ring (120 ms), then it fills with the person's colour from the centre (200 ms, ease-out) |
| Pen ring on a new day | Drawn as a stroke, 400 ms, ease-in-out, the first time the display shows the new day |

**Banned motion:** confetti, bounce or elastic overshoot, pulsing dots, shimmer placeholders (local data loads instantly), parallax, and animated size changes that move neighbouring elements.

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
- A person's full colour as an entry's background, or white text on a member colour anywhere but the disc. Fills are tints.
- Inter, Roboto or other system fonts as the visible face, except in widgets, which can't load bundled fonts.
