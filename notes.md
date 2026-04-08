# PR Comment Handler Notes

## Key Reviewers
- **conroy-ricketts**: Assert specific values (not non-null), date fields via DateUtils, "doesNotThrow" naming.
- **Jignesh-dimagi**: Class-level members in setUp(), null input tests with @Test(expected=NullPointerException::class).

## Testing Patterns
- Date: `DateUtils.parseDate("YYYY-MM-DD")` / `DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")`
- "doesNotThrow" test: call method, no assertions.
- Parser tests use `ByteArrayInputStream`.

## Build
- Gradle/ktlint not runnable in sandbox.

## PR Status (2026-04-08)
- #3610: APPROVED.
- #3612, #3614, #3619, #3626: All feedback addressed (last bot commit/comment 2026-04-06); awaiting re-review (no new reviews as of 2026-04-08).
- #3632, #3635, #3636, #3637, #3645: No reviews yet (2026-04-08).
