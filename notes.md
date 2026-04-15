# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing>mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-15
- #3610: APPROVED both. Awaiting merge.
- #3612: rename applied (4db6f0d); awaiting conroy re-review.
- #3614: DateUtils inline applied (90470dd); awaiting conroy re-review.
- #3619: conroy APPROVED (4d03acb); awaiting Jignesh re-review.
- #3626: all threads done (9b0f322); conroy APPROVED; awaiting Jignesh re-review.
- #3632: all conroy threads done (b6cb4fe, 678c180); awaiting conroy re-review.
- #3635,#3636,#3637,#3645: no reviews yet.
