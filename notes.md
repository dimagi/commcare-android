# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific asserts, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real data over mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-05-02 (verified)
All feedback addressed. No new reviews since 2026-04-09. All unresolved threads are outdated (code already changed) or changes are verified present.
- #3612: awaiting conroy re-review. Thread outdated — logFailedResponse_403_doesNotThrow() already named correctly.
- #3614: awaiting conroy re-review. Thread outdated — DateUtils.parseDateTime() already inlined in assertEquals.
- #3626: conroy APPROVED, awaiting Jignesh re-review. Null pointer tests present in both files.
- #3632: awaiting conroy re-review. All 3 threads addressed — empty array guard in prod code, real data used, storeJobs not called for empty.
- #3635 #3636 #3637 #3645: no reviews yet.
