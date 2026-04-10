# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing > mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Testing Patterns
- Date: DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")
- ConnectJobRecord.fromJson needs: id, opportunity_id, name, description, organization, end_date, start_date, max_visits_per_user, daily_max_visits_per_user, budget_per_visit, budget_per_user, currency, short_description, deliver_progress, payment_units[], learn_progress, learn_app, deliver_app.

## PR Status (2026-04-10)
- #3610: APPROVED.
- #3619: APPROVED (conroy-ricketts 2026-04-08).
- #3626: conroy-ricketts APPROVED (2026-04-08); Jignesh last COMMENTED (2026-04-01); awaiting re-review.
- #3612: Addressed (4db6f0d, rename logFailedResponse_403_doesNotThrow); awaiting re-review.
- #3614: Addressed (90470dd, inline DateUtils.parseDateTime); awaiting re-review.
- #3632: Addressed (b6cb4fe, 678c180 — types, storeJobs guard, reduce mocks); awaiting re-review.
- #3635, #3636, #3637, #3645: No reviews yet.
