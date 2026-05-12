# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, `doesNotThrow` naming, no `InputStream` type, real data over mocks.
- Jignesh-dimagi: class-level `setUp()` members, `@Test(expected=...)`. Verify file before re-implementing.

## Status (2026-05-12)
7 open [Test Improver] PRs. No new feedback since 2026-04-09. All prior review threads on #3632/#3614/#3612/#3626 already addressed in commits 9b0f322 / 4db6f0d / 90470dd / b6cb4fe / 678c180. #3636/#3635/#3637 still have zero comments. No merge conflicts detected.

## Environment
Gradle wrapper unusable in sandbox; ktlint/tests via CI only.
