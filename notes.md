# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific assertions, DateUtils dates, "doesNotThrow" naming, inline locals, real parsing > mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), `@Test(expected=NullPointerException::class)`.

## Testing Patterns
- Date: `DateUtils.parseDate("YYYY-MM-DD")` / `DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")`
- Real ConnectJobRecord.fromJson needs full JSON (id, opportunity_id, name, description, organization, end_date, start_date, max_visits_per_user, daily_max_visits_per_user, budget_per_visit, budget_per_user, currency, short_description, deliver_progress, payment_units[], learn_progress, learn_app, deliver_app).

## PR Status (2026-04-09)
- #3610, #3619: APPROVED.
- #3626: conroy-ricketts APPROVED (2026-04-08); Jignesh CHANGES_REQUESTED — all addressed, awaiting re-review.
- #3612, #3614: All addressed 2026-04-08; unresolved threads outdated, awaiting re-review.
- #3632: All addressed 2026-04-09 (two commits: InputStream types + storeJobs guard + reworked test class); awaiting re-review.
- #3635, #3636, #3637, #3645: No reviews yet.
