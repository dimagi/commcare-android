# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing>mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-13
- #3610: APPROVED both.
- #3612: logFailedResponse_403_doesNotThrow rename done; summary 2026-04-08; awaiting conroy.
- #3614: DateUtils.parseDateTime inline done; summary 2026-04-08; awaiting conroy.
- #3619: conroy APPROVED; awaiting Jignesh.
- #3626: conroy APPROVED 2026-04-08; all Jignesh threads done; summary 2026-04-06; awaiting Jignesh.
- #3632: all 3 conroy threads done (types, storeJobs guard, real-data tests); summaries 2026-04-08/09; awaiting conroy.
- #3635,#3636,#3637,#3645: no reviews.
