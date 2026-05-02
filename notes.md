# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real data over mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-05-02
All prior feedback addressed. No new reviews since 2026-04-09.
- #3612 #3614 #3632: awaiting conroy re-review.
- #3626: conroy approved, awaiting Jignesh re-review. Jignesh's unresolved threads (lines 23/25,
  not outdated) reference null pointer tests that ARE present in the files — threads just not
  marked resolved by reviewer. No further action needed.
- #3635 #3636 #3637 #3645: no reviews yet.
