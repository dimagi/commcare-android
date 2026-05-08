# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, `doesNotThrow` naming, no `InputStream` type, real data over mocks.
- Jignesh-dimagi: class-level `setUp()` members, `@Test(expected=...)`. Verify file before re-implementing.

## Status (2026-05-08)
8 open [Test Improver] PRs. No new actionable feedback this run.
- #3645: null-object-in-array tests added 2026-05-07. New 2026-05-08 comment is coverage bot (non-actionable).
- #3632, #3614, #3612, #3626: prior feedback already implemented; reviewers haven't followed up.
- #3636, #3635, #3637: zero review comments.
- All PRs `mergeable_state=blocked` (branch protection, not conflicts). None CONFLICTING.

## Environment
Gradle wrapper unusable in sandbox; ktlint/tests via CI only.
