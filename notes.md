# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline (no local vars), doesNotThrow naming, no InputStream type, skip storeJobs for empty lists, real parsing > mocks.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-17
- #3610: Both APPROVED. Awaiting merge.
- #3612: Done (rename logFailedResponse_403_doesNotThrow, commit 4db6f0d). Awaiting conroy re-review.
- #3614: Done (inline DateUtils.parseDateTime, commit 90470dd). Awaiting conroy re-review.
- #3619: conroy APPROVED. Awaiting Jignesh first review.
- #3626: Done (class-level setUp, null tests, commits 05a5c5c+9b0f322). conroy APPROVED. Awaiting Jignesh re-review.
- #3632: Done (storeJobs guard, real JSON, InputStream removed, commits b6cb4fe+678c180). Awaiting conroy re-review.
- #3635,#3636,#3637,#3645: No reviews yet (draft).
