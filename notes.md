# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline (no local vars), doesNotThrow naming, no InputStream type, skip storeJobs for empty lists, real parsing > mocks.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-16
- #3610: Both APPROVED. Awaiting merge.
- #3612: conroy CHANGES_REQUESTED. Sole thread outdated (renamed). Awaiting re-review.
- #3614: conroy CHANGES_REQUESTED. Sole thread outdated (DateUtils inlined). Awaiting re-review.
- #3619: conroy APPROVED. Awaiting Jignesh first review.
- #3626: conroy APPROVED. Null tests present. Awaiting Jignesh re-review.
- #3632: conroy CHANGES_REQUESTED. All feedback addressed (guard + real JSON). Awaiting re-review.
- #3635,#3636,#3637,#3645: no reviews yet.
