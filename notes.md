# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing>mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-14
- #3610: APPROVED both.
- #3612: logFailedResponse_403_doesNotThrow rename applied (4db6f0d); awaiting conroy re-review.
- #3614: DateUtils.parseDateTime inline applied (90470dd); awaiting conroy re-review.
- #3619: conroy APPROVED; awaiting Jignesh.
- #3626: conroy APPROVED; awaiting Jignesh re-review.
- #3632: all 3 conroy threads addressed (b6cb4fe+678c180); threads 2&3 show is_outdated:false but were placed on unchanged lines in other tests—underlying concerns (storeJobs guard, real data assertions) are fully implemented; awaiting conroy re-review.
- #3635,#3636,#3637,#3645: no reviews yet.
