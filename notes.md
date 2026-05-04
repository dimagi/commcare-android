# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real data over mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-05-04
No new reviews. All feedback from prior rounds fully implemented, awaiting re-review.
- #3612 #3614 #3632: CHANGES_REQUESTED by conroy-ricketts (2026-04-08), all addressed.
  - #3632: storeJobs guarded (`if (jobs.isEmpty()) 0 else storeJobs(...)`); real dummy data via validJobJson() in tests.
- #3626: APPROVED by conroy-ricketts; Jignesh CHANGES_REQUESTED open, all threads addressed (null pointer tests added to both parser test files).
- #3635 #3636 #3637 #3645: no reviews yet.
