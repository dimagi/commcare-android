# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline (no local vars), doesNotThrow naming, no InputStream type, skip storeJobs for empty lists, real parsing > mocks.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-16 (verified, no change from 2026-04-15)
- #3610: APPROVED both. Awaiting merge.
- #3612: Awaiting conroy re-review (rename fix @ 4db6f0d; thread outdated/done).
- #3614: Awaiting conroy re-review (DateUtils inline @ 90470dd; thread outdated/done).
- #3619: conroy APPROVED. Awaiting Jignesh first review.
- #3626: conroy APPROVED. Null tests added @ 9b0f322. Awaiting Jignesh re-review.
- #3632: Awaiting conroy re-review. storeJobs guarded (@ 678c180). Threads addressed.
- #3635,#3636,#3637,#3645: no reviews yet.
