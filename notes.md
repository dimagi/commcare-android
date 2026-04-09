# PR Comment Handler Notes

## Key Reviewers
- **conroy-ricketts**: Specific assertions over non-null, DateUtils for dates, "doesNotThrow" naming, inline single-use locals, minimize mocking (real parsing > static mocks), skip storeJobs for empty lists, no explicit InputStream type on locals.
- **Jignesh-dimagi**: Class-level members in setUp(), null input tests with @Test(expected=NullPointerException::class).

## Testing Patterns
- Date: `DateUtils.parseDate("YYYY-MM-DD")` / `DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")`
- "doesNotThrow" test: call method, no assertions. Parser tests use `ByteArrayInputStream`.
- For real ConnectJobRecord.fromJson: use `unmockkStatic(ConnectJobRecord::class)` + full JSON (id, opportunity_id, name, description, organization, end_date, start_date, max_visits_per_user, daily_max_visits_per_user, budget_per_visit, budget_per_user, currency, short_description, deliver_progress, payment_units[], learn_progress{total_modules,completed_modules}, learn_app, deliver_app).

## Build
- Gradle/ktlint not runnable in sandbox.

## PR Status (2026-04-09)
- #3610, #3619, #3626: APPROVED.
- #3612, #3614: Feedback addressed 2026-04-08 (bot committed + summary posted); remaining threads outdated — awaiting re-review.
- #3632: Additional feedback addressed 2026-04-09 — moved ConnectJobRecord mock out of setUp, all tests now use real JSON except testCorruptJobEntry. Summary posted.
- #3635, #3636, #3637, #3645: No reviews yet.
