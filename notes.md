# PR Comment Handler Notes

## Reviewers
- conroy-ricketts: specific assertions, DateUtils inline, doesNotThrow naming, no InputStream type, skip storeJobs for empty, real parsing > mocks.
- Jignesh-dimagi: class-level setUp() members, @Test(expected=...).

## Status 2026-04-20
- #3610: APPROVED x2. Merge pending.
- #3612: Addressed (rename doesNotThrow). Awaiting conroy.
- #3614: Addressed (inline DateUtils). Awaiting conroy.
- #3619: conroy APPROVED. Awaiting Jignesh.
- #3626: Addressed (class members, null tests). conroy APPROVED. Awaiting Jignesh.
- #3632: Addressed (no InputStream type, storeJobs guard, real parsing). Awaiting conroy.
- #3635,#3636,#3637,#3645: No reviews yet.

## Notes
- Last bot commits: #3612/#3614 on 2026-04-08, #3632 on 2026-04-09.
- No new reviews since 2026-04-09. Threads not-outdated on added files are expected.
