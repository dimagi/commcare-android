# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing>mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-14
- #3610: APPROVED both.
- #3612: logFailedResponse_403_doesNotThrow rename done (commit 4db6f0d); summary 2026-04-08; awaiting conroy re-review.
- #3614: DateUtils.parseDateTime inline done (commit 90470dd); summary 2026-04-08; awaiting conroy re-review.
- #3619: conroy APPROVED 2026-04-08; awaiting Jignesh.
- #3626: conroy APPROVED 2026-04-08; all Jignesh threads done; summary 2026-04-06; awaiting Jignesh re-review.
- #3632: all 3 conroy threads done (types b6cb4fe, storeJobs guard, real-data tests; mock cleanup 678c180); summaries 2026-04-08/09; awaiting conroy re-review.
- #3635,#3636,#3637,#3645: no reviews.
