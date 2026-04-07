# PR Comment Handler Notes

## Key Reviewers
- **conroy-ricketts**: Assert specific values (not non-null), date fields via DateUtils, "doesNotThrow" test naming.
- **Jignesh-dimagi**: Class-level members in setUp(), null input tests with @Test(expected=NullPointerException::class).

## Testing Patterns
- `@Config(application = CommCareTestApplication::class)` + `@RunWith(AndroidJUnit4::class)`
- Date: `DateUtils.parseDate("YYYY-MM-DD")` / `DateUtils.parseDateTime("YYYY-MM-DDTHH:MM:SS.mmm")`
- "doesNotThrow" test: just call method, no assertions
- Parser tests use `ByteArrayInputStream`

## Build
- Gradle/ktlint not runnable in sandbox (lock file permission errors)
- Test: `./gradlew testCommcareDebug --tests "org.commcare.package.TestClass"`

## PR Status (2026-04-07)
- #3610: APPROVED — ready to merge
- #3612, #3614, #3619, #3626: All review feedback addressed and confirmed in bot comments (2026-04-06); still awaiting re-review/thread resolution from maintainers (checked 2026-04-07, no new reviews)
- #3636, #3635, #3645, #3632, #3637: No reviews yet (checked 2026-04-07)
