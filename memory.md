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
- mockStatic pattern: setUp/tearDown with MockedStatic, verify() with actual values or any()/eq()

## Maintainer Notes
- "go or no go" for auto-PRs (no iterations)
- Full-class coverage required per PR (conroy-ricketts)
- Specific assertions: known values, not just non-null
- Ktlint: no blank line after class `{`, no chained methods on one line

## Infrastructure
- `ModernHttpRequesterMock` uses static state → test leakage risk (tracked #2649)
- `MainCoroutineRule.kt` uses deprecated `TestCoroutineDispatcher`/`runBlockingTest`
- `NotificationTestUtil.kt` = good Kotlin builder pattern to follow
- Context in tests: `ApplicationProvider.getApplicationContext<Context>()`

## Backlog
1. EntityMapUtils.kt - geo parsing (HIGH) - note: needs Google Maps SDK mocking
2. RetrieveHqTokenResponseParser.kt - needs CommCareApplication.instance().getCurrentApp() mocked (MEDIUM)

## Completed
- NetworkUtils.kt (2026-03-15) - PR #3612
- PR #3610: HashUtils (2026-03-13) - 1 approval (conroy-ricketts, tests pass locally)
- PR #3602: ConnectDateUtils + ktlint (2026-03-12) - 2 approvals (Jignesh, avazirna)
- DeliveryAppProgressResponseParser.kt (2026-03-16) - PR #3614
- LearningAppProgressResponseParser.kt (2026-03-17) - PR #3619
- LinkHqWorkerResponseParser.kt + RetrieveChannelEncryptionKeyResponseParser.kt (2026-03-18) - PR pending (branch: test-assist/connectid-response-parsers-20260318)
- Commented #2649: MockWebServer migration (2026-03-14)
- Commented #2880: Nepali timezone fix (2026-03-14)
- Issue #3601: Monthly Activity 2026-03

## Round-Robin
- T1:2026-03-11 T2:2026-03-16 T3:2026-03-18
- T4:2026-03-18 T5:2026-03-14 T6:2026-03-14 T7:2026-03-18
