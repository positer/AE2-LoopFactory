# 2026-08-28 Changelog time-information contract

## Requirement

`CHANGELOG.md` must not contain any date or time information. Release entries identify releases by version only.

## Enforcement

- Removed the calendar date from the 0.0.1 changelog heading.
- Added one loader-independent JUnit contract under `shared/src/test`, so both maintained generation builds enforce the same rule.
- Declared the root changelog as an explicit input of both Gradle test tasks, preventing incremental builds from skipping the contract after changelog-only edits.
- The contract rejects four-digit calendar years, year-first and day/month numeric dates, 24-hour and AM/PM clock times, English month and weekday names, common relative-time words, and Chinese numeric or named date/time expressions.
- Dotted Minecraft and semantic versions such as `1.21.1`, `26.1.2`, and `0.0.1` remain valid.
- Rewrote the changelog as a concise bilingual summary of the two added blocks and their responsibilities only; it contains no crafting recipe or release-verification detail.

## Verification

- Minecraft 1.21.1: 57 tests, zero failures, zero errors.
- Minecraft 26.1.2: 57 tests, zero failures, zero errors.
- Both complete Gradle builds pass with the shared changelog contract enabled.
