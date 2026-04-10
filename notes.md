# PR Comment Handler Notes

## Reviewer Preferences
- conroy-ricketts: specific assertions, DateUtils dates, doesNotThrow naming, inline locals, real parsing > mocks, no InputStream type, skip storeJobs for empty lists.
- Jignesh-dimagi: class-level members in setUp(), @Test(expected=...).

## Testing Patterns
- Date: DateUtils.parseDate("YYYY-MM-DD") / DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")
- ConnectJobRecord.fromJson needs: id, opportunity_id, name, description, organization, end_date, start_date, max_visits_per_user, daily_max_visits_per_user, budget_per_visit, budget_per_user, currency, short_description, deliver_progress, payment_units[], learn_progress, learn_app, deliver_app.

## PR Status (2026-04-10)
- #3610: APPROVED.
- #3619: APPROVED.
- #3626: conroy-ricketts APPROVED (2026-04-08); Jignesh-dimagi CHANGES_REQUESTED (2026-03-24) — all 8 threads addressed in commits 05a5c5c + 9b0f322; summary posted 2026-04-06; awaiting Jignesh re-review.
- #3612: conroy-ricketts CHANGES_REQUESTED (2026-04-08) — rename applied in 4db6f0d (thread outdated); awaiting re-review.
- #3614: conroy-ricketts CHANGES_REQUESTED (2026-04-08) — inline applied in 90470dd (thread outdated); awaiting re-review.
- #3632: conroy-ricketts CHANGES_REQUESTED (2026-04-08) — storeJobs guard + real data + mocking reduced (b6cb4fe, 678c180); summary posted 2026-04-08 & 2026-04-09; awaiting re-review.
- #3635, #3636, #3637, #3645: No reviews yet.
