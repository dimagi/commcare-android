# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing > mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Testing Patterns
- Date: DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")
- ConnectJobRecord.fromJson needs: id, opportunity_id, name, description, organization, end_date, start_date, max_visits_per_user, daily_max_visits_per_user, budget_per_visit, budget_per_user, currency, short_description, deliver_progress, payment_units[], learn_progress, learn_app, deliver_app.

## PR Status (2026-04-12)
- #3610: APPROVED by both.
- #3612: rename done (4db6f0d); awaiting conroy re-review.
- #3614: inline done (90470dd); awaiting conroy re-review.
- #3619: conroy APPROVED. Awaiting Jignesh.
- #3626: conroy APPROVED. Jignesh feedback done (9b0f322); awaiting Jignesh.
- #3632: all feedback done (b6cb4fe+678c180); awaiting conroy.
- #3635, #3636, #3637, #3645: no reviews yet.
