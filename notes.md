# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real parsing > mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-04-21 (re-verified)
- #3612: All comments addressed (renamed logFailedResponse_403_doesNotThrow, bot commit 2026-04-08). Awaiting conroy re-review.
- #3614: All comments addressed (inlined DateUtils.parseDateTime in assertEquals, bot commit 2026-04-08). Awaiting conroy re-review.
- #3626: All comments addressed (class-level members, assertFalse, null NPE tests in both test files). conroy APPROVED. Awaiting Jignesh re-review.
- #3632: All comments addressed (production code guard `if (jobs.isEmpty()) 0 else storeJobs(...)`, real JSON via validJobJson(), reduced mocking). Awaiting conroy re-review.
- #3635, #3636, #3637, #3645: No reviews yet.

## Notes
- Last bot commits: #3612/#3614 on 2026-04-08, #3626 on 2026-04-04, #3632 on 2026-04-09.
- All unresolved threads re-verified 2026-04-21: all outdated or fixes confirmed present in latest code.
- No new reviews or actionable feedback since 2026-04-09.
- All blocked PRs are in draft/blocked state — no merge conflicts.
