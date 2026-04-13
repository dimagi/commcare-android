# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing>mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-13
- #3610: APPROVED both.
- #3612: rename done 4db6f0d; awaiting conroy re-review.
- #3614: inline done 90470dd; awaiting conroy re-review.
- #3619: conroy APPROVED 2026-04-08; awaiting Jignesh.
- #3626: all done 9b0f322; conroy APPROVED 2026-04-08; awaiting Jignesh.
- #3632: all done b6cb4fe+678c180; awaiting conroy re-review.
- #3635,#3636,#3637,#3645: no reviews.
