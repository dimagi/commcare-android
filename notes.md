# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real parsing > mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-04-20 (re-verified)
- #3612: All comments addressed (renamed logFailedResponse_403_doesNotThrow, bot commit 2026-04-08). Awaiting conroy re-review.
- #3614: All comments addressed (inlined DateUtils.parseDateTime in assertEquals, bot commit 2026-04-08). Awaiting conroy re-review.
- #3626: All comments addressed (class-level members, assertFalse, null NPE tests). conroy APPROVED. Awaiting Jignesh re-review.
- #3632: All comments addressed (removed InputStream types, storeJobs guard in production code, real JSON helpers with validJobJson, reduced mocking, bot commits 2026-04-08 and 2026-04-09). Awaiting conroy re-review.
- #3635, #3636, #3637, #3645: No reviews yet.

## Notes
- Last bot commits: #3612/#3614/#3626 on 2026-04-08, #3632 on 2026-04-09.
- All unresolved threads re-verified 2026-04-20: all outdated or fixes confirmed present in latest code.
- No new reviews or actionable feedback since 2026-04-09.
- All blocked PRs are in draft state, not CONFLICTING — no merge conflict resolution needed.
