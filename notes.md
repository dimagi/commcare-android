# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, `doesNotThrow` naming, no `InputStream` type, real data over mocks.
- Jignesh-dimagi: class-level `setUp()` members, `@Test(expected=...)`. Verify file before re-implementing.

## Status (2026-05-08)
7 open [Test Improver] PRs. No new actionable feedback or conflicts. All prior reviewer threads on #3632/#3614/#3612/#3626 already addressed in commits dated 2026-04-08/09; awaiting reviewer follow-up. PRs #3636/#3635/#3637 have zero review comments.

## Environment
Gradle wrapper unusable in sandbox; ktlint/tests via CI only.
