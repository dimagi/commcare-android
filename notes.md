# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing>mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-13
- #3610: APPROVED both.
- #3612: rename done 4db6f0d; thread outdated; awaiting conroy re-review.
- #3614: inline done 90470dd; thread outdated; awaiting conroy re-review.
- #3619: conroy APPROVED; awaiting Jignesh.
- #3626: conroy APPROVED; awaiting Jignesh.
- #3632: InputStream+storeJobs guard+real JSON done (b6cb4fe+678c180); threads 2+3 not-outdated but addressed; awaiting conroy re-review.
- #3635,#3636,#3637,#3645: no reviews.
