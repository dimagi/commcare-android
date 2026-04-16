# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline (no local vars), doesNotThrow naming, no InputStream type, skip storeJobs for empty lists, real parsing > mocks.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Date helpers
DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")

## Status 2026-04-16 (updated)
- #3610: Both APPROVED. Awaiting merge.
- #3612: All feedback addressed (rename to logFailedResponse_403_doesNotThrow done, commit 4db6f0d). Awaiting conroy re-review.
- #3614: All feedback addressed (DateUtils.parseDateTime inlined into assertEquals, commit 90470dd). Awaiting conroy re-review.
- #3619: conroy APPROVED. Awaiting Jignesh first review.
- #3626: All feedback addressed (class-level members in setUp(), null tests added, commits in history). conroy APPROVED. Awaiting Jignesh re-review.
- #3632: All feedback addressed (guard storeJobs for empty array, real JSON parsing, InputStream types removed, commits b6cb4fe + 678c180). Awaiting conroy re-review.
- #3635,#3636,#3637,#3645: No reviews yet.
