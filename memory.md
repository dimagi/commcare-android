# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`
- Coverage: `./gradlew JacocoTestReport`
- Lint: `ktlint --format path/to/file.kt`
- Needs `../commcare-core/` sibling (CI only)

## Project
- Tests: `app/unit-tests/src/org/commcare/`
- Pattern: `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- JUnit4 + Robolectric + MockK/Mockito

## Maintainer Notes
- "go or no go" for auto-PRs (no iterations)
- Full-class coverage required per PR (conroy-ricketts)
- Specific assertions: known values, not just non-null
- Ktlint: no blank line after class `{`, no chained methods on one line

## Infrastructure
- `ModernHttpRequesterMock` uses static state → test leakage risk (tracked #2649)
- `MainCoroutineRule.kt` uses deprecated `TestCoroutineDispatcher`/`runBlockingTest`
- `NotificationTestUtil.kt` = good Kotlin builder pattern to follow

## Backlog
1. EntityMapUtils.kt - geo parsing (HIGH)
2. RequestStats.kt - `getRequestAge()` bucketing (MEDIUM)
3. NotificationIdentifiers.kt - `generateNotificationIdFromString()` (LOW)

## Completed
- PR #3610: HashUtils (2026-03-13)
- PR #3602: ConnectDateUtils + ktlint (2026-03-12)
- Commented #2649: MockWebServer migration (2026-03-14)
- Commented #2880: Nepali timezone fix (2026-03-14)
- Issue #3601: Monthly Activity 2026-03

## Round-Robin
- T1:2026-03-11 T2:2026-03-12 T3:2026-03-13
- T4:2026-03-12 T5:2026-03-14 T6:2026-03-14 T7:2026-03-14
