# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`
- Coverage: `./gradlew JacocoTestReport`
- Lint: `./gradlew ktlintFile -PfilePath=path/to/file.kt`
- Needs `../commcare-core/` sibling (CI only)
- Note: Gradle wrapper lock file issue in this environment blocks ktlint

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
1. EntityMapUtils.kt - geo parsing (HIGH) - note: needs Google Maps SDK mocking
2. LinkHqWorkerResponseParser.kt / RetrieveChannelEncryptionKeyResponseParser.kt / RetrieveHqTokenResponseParser.kt - untested connectId parsers (MEDIUM)

## Completed
- NetworkUtils.kt (2026-03-15) - PR #3612
- PR #3610: HashUtils (2026-03-13) - 1 approval (conroy-ricketts, tests pass locally)
- PR #3602: ConnectDateUtils + ktlint (2026-03-12) - 2 approvals (Jignesh, avazirna)
- DeliveryAppProgressResponseParser.kt (2026-03-16) - PR #3614
- LearningAppProgressResponseParser.kt (2026-03-17) - PR pending (branch: test-assist/learning-progress-parser-tests-20260317)
- Commented #2649: MockWebServer migration (2026-03-14)
- Commented #2880: Nepali timezone fix (2026-03-14)
- Issue #3601: Monthly Activity 2026-03

## Round-Robin
- T1:2026-03-11 T2:2026-03-16 T3:2026-03-17
- T4:2026-03-15 T5:2026-03-14 T6:2026-03-14 T7:2026-03-17
