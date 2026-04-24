# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real data over mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-04-24 (re-verified 2026-04-24 — no new reviews since last bot run 2026-04-09)
- #3612: Rename to `logFailedResponse_403_doesNotThrow` implemented (commit `4db6f0d`). Awaiting conroy re-review.
- #3614: DateUtils.parseDateTime inlined into assertEquals (commit `90470dd`). Awaiting conroy re-review.
- #3626: Class-level members + assertFalse + null NPE tests implemented (commits `05a5c5c`, `9b0f322`). conroy APPROVED. Awaiting Jignesh re-review (CHANGES_REQUESTED still open from 2026-03-24, all feedback addressed).
- #3632: storeJobs guard + real JSON data + removed mockkStatic from setUp implemented (commits `b6cb4fe`, `678c180`). Awaiting conroy re-review.
- #3635, #3636, #3637, #3645: No reviews.
