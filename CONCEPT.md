# House Calendar: the concept

This document is for families, not engineers. It explains what the app does and why it works the way it does. The precise rules are in `SPEC.md`.

## The idea in one line

The family calendar from the kitchen wall, on a tablet in the same spot and on the parents' phones, kept in step without the internet.

## The devices

- **The kitchen display.** Any Android tablet, on the wall or a stand, plugged in. It shows the week with a column for each person, like a paper family organiser. It stays on during the day and goes dark at night. Anyone can add something on it.
- **Parents' phones.** The same calendar in your pocket, with reminders for the things that involve you.

There's no account, no sign-up and no server. The devices talk to each other directly over your home Wi-Fi. When you get home, your phone brings the display up to date, and the display passes it on to everyone else. If two phones meet away from home, they can sync over Bluetooth.

## People

Everyone in the family is a **member**: adults and children, with a colour and an initial each. A child doesn't need a phone to be on the calendar. Adding Grandma as a member works too, if she does the Thursday pick-up.

## Events

An event is a title, a day and time (or all day), and **who** it's for: certain people, or everyone. Two more things matter in a family:

- **Who's taking them.** Any event can say "Sam taking", or "Needs someone" until someone says they'll do it. Your phone keeps a short list of the next fortnight's lifts that still need someone. If one person is down to be in two places at once, the app says so in plain words.
- **Where.** A place, so the display can say "Swimming · Leisure Centre".

### Repeating things

Clubs and lessons repeat: every Tuesday, every other Saturday, the last Friday of the month. Tick **term time only** and they skip the school holidays you've entered. You don't have to delete half-term swimming by hand.

When you change a repeating event, the app asks one question: **only this one**, **this and the following**, or **every time**. If both parents change the same series while apart, both changes are kept, and the calendar never ends up with two copies of the same club.

### Holidays

Enter the school holidays once a year, or open the school's calendar file and choose **Add as holidays**. They show as a band across everyone's week.

## Adding from other apps

Things often start somewhere else: a booking confirmation, a party invite in a message, the school's calendar file, a work calendar. House Calendar takes them in from:
- **"Add to calendar" buttons** in other apps, where it appears as a choice
- **calendar files** (`.ics`) from email or downloads
- **text you share** to it, such as a message saying "Party at Mia's, Sat 3pm"
- **your phone's other calendars**, copying the events you tick.

Each one opens as a ready-filled event for you to check. Add who it's for and who's taking them, then confirm. Nothing is added behind your back. If you bring the same thing in twice, or on both phones, it's still one event.

## Reminders

Each phone reminds its owner about the things they're in or taking them to, on time, even if the app is closed: "Swimming · 17:00 · You're taking Ada". The kitchen display never beeps. It shows **Now** and **Next** for everyone instead.

## Fairness and trust

- **Both phones and the display agree once they've synced.** If two people change the same detail while apart, the later change wins, everywhere.
- **Nothing is ever really deleted.** Deleted events go to **Recently deleted**, and anyone can bring them back.
- **You can see who did what.** Every event shows which phone added it and which last changed it.
- **The display says when it last heard from each phone**, so a stale wall is obvious.

## The display at night and when idle

The display stays on during day hours (06:30 to 22:00 unless you change them). Outside those hours it shows only a dim clock. If someone leaves it on next month's page, it goes back to this week after two minutes. At midnight it moves on to the new day by itself.

## What's deliberately left out (for now)

- **Tasks, meal plans and shopping lists.** They come next, and the app is built to add them without disturbing anything already on the calendar.
- **Keeping in step with Google, Outlook or iCloud calendars.** You can copy things in, but the family calendar stays the only record.
- **Children's own phones.**
- **iPhone.**
- **Photos and weather,** which would need the internet or a lot of storage.
