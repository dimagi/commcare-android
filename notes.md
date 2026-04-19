# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty lists, real parsing > mocks.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Status 2026-04-19
- #3610: APPROVED x2. Awaiting merge.
- #3612: All feedback addressed (rename logFailedResponse_403_doesNotThrow). Awaiting conroy re-approval.
- #3614: All feedback addressed (inline DateUtils.parseDateTime). Awaiting conroy re-approval.
- #3619: conroy APPROVED. Awaiting Jignesh first review.
- #3626: All Jignesh feedback addressed (class-level members, null tests). conroy APPROVED. Awaiting Jignesh re-review.
- #3632: All feedback addressed (no InputStream type, skip storeJobs for empty, real parsing). Awaiting conroy re-approval.
- #3635,#3636,#3637,#3645: No reviews yet.

## Notes
- Last bot action on #3612/#3614/#3632 was 2026-04-08 (same-day as CHANGES_REQUESTED reviews).
- All PRs marked draft. No new reviews since 2026-04-09.
