# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real parsing > mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-04-20
- #3610: APPROVED x2 (conroy + Jignesh). Merge pending.
- #3612: All comments addressed (renamed logFailedResponse_403_doesNotThrow). Awaiting conroy re-review.
- #3614: All comments addressed (inlined DateUtils.parseDateTime in assertEquals). Awaiting conroy re-review.
- #3619: conroy APPROVED. Awaiting Jignesh.
- #3626: All comments addressed (class-level members, assertFalse, null NPE tests added). conroy APPROVED. Awaiting Jignesh re-review.
- #3632: All comments addressed (removed InputStream types, storeJobs guard, real JSON helpers, reduced mocking). Awaiting conroy re-review.
- #3635, #3636, #3637, #3645: No reviews yet.

## Notes
- Last bot commits: #3612/#3614/#3626 on 2026-04-08, #3632 on 2026-04-09.
- All unresolved threads verified 2026-04-20: either outdated (code already changed) or fix confirmed present.
- No new reviews or actionable feedback since 2026-04-09.
