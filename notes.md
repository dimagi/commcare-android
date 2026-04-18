# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty lists, real parsing > mocks.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Status 2026-04-18
- #3610: APPROVED x2. Awaiting merge.
- #3612: All feedback done. Awaiting conroy re-review.
- #3614: All feedback done. Awaiting conroy re-review.
- #3619: conroy APPROVED. Awaiting Jignesh.
- #3626: conroy APPROVED. All Jignesh feedback done (NullPointerException tests added, class-level members). Awaiting Jignesh re-review.
- #3632: All feedback done (storeJobs guarded for empty lists, real JSON data, InputStream type removed). Awaiting conroy re-review.
- #3635,#3636,#3637,#3645: No reviews yet.
