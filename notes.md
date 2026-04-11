# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing > mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Testing Patterns
- Date: DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")
- ConnectJobRecord.fromJson needs: id, opportunity_id, name, description, organization, end_date, start_date, max_visits_per_user, daily_max_visits_per_user, budget_per_visit, budget_per_user, currency, short_description, deliver_progress, payment_units[], learn_progress, learn_app, deliver_app.

## PR Status (2026-04-11)
- #3610: APPROVED (conroy-ricketts + Jignesh).
- #3619: conroy-ricketts APPROVED. Awaiting Jignesh.
- #3626: conroy-ricketts APPROVED; Jignesh feedback all addressed; awaiting re-review.
- #3612: rename applied; awaiting re-review.
- #3614: inline applied; awaiting re-review.
- #3632: storeJobs guard + real data + mocking reduced; awaiting re-review.
- #3635, #3636, #3637, #3645: No reviews yet.
