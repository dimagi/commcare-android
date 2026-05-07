# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, doesNotThrow naming, no InputStream type, real data over mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...). May re-request items already done — verify file first.

## Status (2026-05-07)
8 open [Test Improver] PRs. All `mergeable_state: blocked` (CI gating, not conflicts).
- #3645: Added null-object-in-JSONArray test per Jignesh clarification.
- #3632, #3626, #3614, #3612: prior threads addressed; no new feedback.
- #3637, #3636, #3635: no reviewer feedback yet.

## Environment
- Gradle wrapper unusable in sandbox; ktlint/tests run only via CI.
