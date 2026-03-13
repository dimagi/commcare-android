# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`
- Coverage: `./gradlew JacocoTestReport`
- Lint: `ktlint --format path/to/file.kt`
- Needs `../commcare-core/` sibling (auto in CI)

## Project
- Tests in: `app/unit-tests/src/org/commcare/`
- Pattern: `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- JUnit4 + Robolectric + MockK/Mockito

## Maintainer Notes
- "go or no go" for auto-PRs (no iterations)
- Full-class coverage: test entire class, not just individual functions (conroy-ricketts, PR #3602)
- Specific assertions: use known expected values, not just non-null (AGENTS.md + Jignesh feedback)
- Ktlint: no blank line after class `{`, no chained `.apply{}.parse()` on one line

## Backlog
1. EntityMapUtils.kt - `parseHexColor`, `parseBoundaryFromString`, `parsePointListFromString` (HIGH)
2. RequestStats.kt - `getRequestAge()` time bucketing (MEDIUM)
3. NotificationIdentifiers.kt - `generateNotificationIdFromString()` (LOW)

## Completed
- PR #3610: HashUtils tests (full class: computeHash SHA1/SHA256 + enum) (2026-03-13)
- PR #3602: ConnectDateUtils tests + ktlint fix (2026-03-12) — 2 approvals as of 2026-03-13
- Issue #3601: Monthly Activity 2026-03

## Round-Robin
- Task 1: 2026-03-11, Task 2: 2026-03-12, Task 3: 2026-03-13
- Task 4: 2026-03-12, Task 7: 2026-03-13, Tasks 5,6: pending
