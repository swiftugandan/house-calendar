# Product

Durable product context for design work, following [impeccable.style](https://impeccable.style) conventions. Visual rules live in `DESIGN.md`; behaviour rules live in `SPEC.md`.

## Audience

There are three audiences on two kinds of device.

**Parents on their phones (organisers).** They add things in passing: at the school gate, reading a WhatsApp from another parent, booking a dentist on the phone. They are interrupted and one-handed. They need to add "Ada, swimming, Tuesdays at 5, not in the holidays" in a few seconds, and trust that the kitchen and their partner will see it. Their real question is usually *who's taking her?*

**Anyone in the kitchen (readers).** Parents making breakfast, children checking whether it's swimming today, a grandparent babysitting. They look at the wall from the table or the hob, usually for two seconds. They aren't holding anything and may not be wearing glasses.

**Children (readers who sometimes add).** From about 6 they can read their own column. Older children may add their own plans on the display. They don't have the app on a phone (SPEC NG-4).

## Purpose

1. Everyone can see, without asking, what's on and where everyone needs to be.
2. Every lift has a named person, or is visibly waiting for one.
3. The repeating shape of family life (clubs, term time, bin day) is entered once and stays right.

It's a household object, like the paper calendar and the pen beside it, not a productivity tool. It earns trust by being right every morning.

## Operating context

- A kitchen tablet, always on and charging, usually in landscape on the wall or a stand. It is the family's meeting point and never leaves the house.
- Parents' Android phones, which come and go. They sync with the tablet when they're home and with each other when they meet.
- There's no internet requirement. Most of the time the internet is there, but the calendar doesn't use it.
- There are two modes:
  - **Glance** (the display): reading from across the room, rarely touched.
  - **Organise** (phones, and sometimes the display): adding, changing, sorting out lifts.
- The week has a rhythm: Sunday evening is "what's on this week", school mornings are "what's on today", and the run-up to half term is "what's changing".

## Design principles

1. **The week is the picture.** On the display, the family's week as a grid of people by days *is* the interface. Everything else is a sheet over it, and it returns there by itself.
2. **Readable from where people actually are.** Display type is sized in physical millimetres for 2 m and 0.6 m (SPEC NFR-A11Y-4), not in dp for a hand-held screen.
3. **One action wins each phone screen.** On the agenda it's Add. In the add sheet it's the button that says exactly what will happen: "Add Swimming, Tuesdays from 13 Oct".
4. **Lifts are first-class.** "Who's taking them" sits next to the time, not buried in notes. A missing lift is visible, quietly and specifically: an empty disc, "Needs someone".
5. **Say what repeats and what doesn't.** Every repeating occurrence shows that it repeats. Every edit asks one plain question about scope, using words, not jargon ("Only this one / This and following / Every time").
6. **Never alarm.** Clashes and missing lifts are information for grown-ups, written plainly. No red badges, no modals, no exclamation marks.
7. **Be truthful about the system.** Show when the display last heard from each phone. Never claim display mode is a lock. Say when an import couldn't bring something over.

## Voice

Plain, warm and specific, like a note on the fridge from a well-organised parent.

- On the display: almost no words. Names, times, titles. "Now", "Next", "Needs someone".
- On phones: short and factual. "Swimming · 17:00 · Sam taking". "Up to date with the kitchen · 2 min ago".
- Scope questions: "Change only this one, or this and the following Tuesdays?"
- Reminders: what, when, your part. "Swimming · 17:00 · You're taking Ada".
- Never: "Oops!", "Awesome!", "You're all caught up!", "Don't forget!!", or productivity-speak ("crush your week").
- Times use the device's 12- or 24-hour setting. Days are written out ("Tue 13 Oct"), never as "10/13".

## Accessibility goals

- WCAG 2.2 AA contrast, 48 dp touch targets, phone screens usable at 200% font scale, TalkBack (SPEC NFR-A11Y-*).
- Colour is never the only signal. A person's colour always comes with their initial or name.
- The display's legibility tiers are physical (SPEC NFR-A11Y-4).
- The typeface is chosen for telling characters apart (`1 l I`, `0 O`), because children read times.

## Evidence

- People read at full speed down to about 0.2° of x-height (Legge & Bigelow, 2011), which sets the display's type sizes.
- About 0.1 s feels instantaneous and about 1 s keeps flow (Nielsen, 1993), which sets moving between weeks and opening the app.
- The paper "family organiser", a column per person and a row per day, is the layout families already use on their kitchen walls. The display adopts it rather than inventing a new one.
