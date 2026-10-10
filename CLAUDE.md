# Project: House Calendar

@import ./ENGINEERING_DISCIPLINE.md

## Overview

An Android app that replaces the paper calendar in the family kitchen. A tablet on the kitchen wall shows the family's week, and parents' phones carry the same calendar. Each event has the people involved and who is taking them. All devices sync with each other peer-to-peer over the home Wi-Fi (Bluetooth as a fallback), with no server, account or internet. Every agenda is calculated from an append-only log of operations, so devices holding the same log always show the same calendar.

It is the sibling of House Points (`../house-points`). The sync stack (`:sync`, `:data`, `:lan`, `:nearby` and the envelope half of `:contracts`) was **copied** from House Points at v0.2.3 by the user's decision, then renamed to `dev.housecalendar`. Fixes to that code should be considered for both repos.

## Current phase

Phase 6, implementation. Spec v0.1.1, SAD v0.1.0, contracts frozen at 0.1.0, plan v0.1.0. Implementing `:schedule` next (PLAN.md §1).

## Quick status

Run these to get a snapshot:

```sh
# Phase artifacts that have been tagged
git tag --list 'spec/*' 'sad/*' 'contracts/*' 'plan/*'

# Modules and where they are in their TDD cycle
git tag --list 'module/*'

# What is live-verified
git tag --list 'module/*/live-verified'
```

## Key documents

- `ENGINEERING_DISCIPLINE.md`: how engineering work is done here (imported above, loaded in every session)
- `VISION.md`: one-paragraph problem statement and definition of done
- `CONCEPT.md`: plain-language product concept for families (not a spec)
- `SPEC.md`: functional and non-functional requirements with citations, including the recurrence and agenda rules and their worked examples
- `PRODUCT.md`, `DESIGN.md`: product context and visual system in impeccable.style conventions
- `SAD.md`: software architecture document
- `CONTRACTS.md`: prose documentation of the `:contracts` module
- `PLAN.md`: decomposition with dependency DAG and acceptance criteria

## Notes for future sessions

- **Language and platform:** Kotlin, Android, Jetpack Compose. The protocol's "contracts crate" is a **pure-JVM Kotlin module** `:contracts` with no Android dependencies, so contract and schedule tests run on the plain JVM. Do not look for `crates/`.
- **Kotlin equivalents of the forbidden smells** in `ENGINEERING_DISCIPLINE.md`:
  - `!!` stands in for `unwrap()`.
  - `error()` and `require()` on recoverable paths stand in for panics. Return a sealed result type instead.
  - Also forbidden: `GlobalScope`, `runBlocking` on the main thread, `lateinit` used to dodge initialisation order, and catching `Throwable`/`Exception` without rethrowing or modelling the failure.
- **Determinism is the core invariant.** The agenda is a pure function: `(operation set, date window, family zone) → agenda`. Never introduce wall-clock reads, locale-dependent formatting, floating point or iteration over unordered collections into that path. "Now" is always a parameter.
- **The UI never expands a recurrence rule itself.** Only `:schedule` does.
- **The `.env*` rule in the global instructions applies here too.**
