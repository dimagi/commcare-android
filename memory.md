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
- Ktlint: no blank line after class `{`, no chained `.apply{}.parse()` on one line

## Backlog
1. HashUtils.kt - `computeHash()`: pure SHA-1/SHA-256 (HIGH)
2. EntityMapUtils.kt - geo data parsing (HIGH)
3. RequestStats.kt - `getRequestAge()` time bucketing (MEDIUM)
4. NotificationIdentifiers.kt - `generateNotificationIdFromString()` (LOW)

## Completed
- PR #3602: ConnectDateUtils tests + ktlint fix (2026-03-12)
- Issue #3601: Monthly Activity 2026-03

## Round-Robin
- Task 1: 2026-03-11, Task 2: 2026-03-12, Task 3: 2026-03-11
- Task 4: 2026-03-12, Task 7: 2026-03-12, Tasks 5,6: pending
