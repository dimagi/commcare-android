# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`
- Coverage: `./gradlew JacocoTestReport`
- Lint: `./gradlew ktlintFile -PfilePath=path/to/file.kt`
- Needs `../commcare-core/` sibling; ktlint blocked in this env

## Project
- Tests: `app/unit-tests/src/org/commcare/`
- Pattern: `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- JUnit4 + Robolectric + MockK/Mockito; mockStatic pattern in ReportIntegrityResponseParserTest

## Maintainer Notes
- "go or no go" (shubham1g5) — no iterations on auto-PRs
- Full-class coverage per PR (conroy-ricketts)
- Specific assertions only; no non-null checks
- Ktlint: no blank line after class `{`, no chained methods on one line

## Infrastructure
- `ModernHttpRequesterMock` static state → leakage (tracked #2649)
- `MainCoroutineRule.kt` deprecated TestCoroutineDispatcher (only LazyMediaDownloadTest.kt uses it)

## Backlog
1. ConnectOpportunitiesParser.kt - HIGH effort - complex nested JSON + 3 static mocks
2. RetrieveHqTokenResponseParser.kt - needs CommCareApplication.instance().getCurrentApp() mock
3. MainCoroutineRule.kt - modernize to StandardTestDispatcher/runTest (LOW urgency)

## Open PRs (all Test Improver, all drafts, no reviews yet)
- #3602 ConnectDateUtils (2 approvals, not draft)
- #3610 HashUtils (1 approval conroy-ricketts)
- #3612 NetworkUtils
- #3614 DeliveryAppProgressResponseParser
- #3619 LearningAppProgressResponseParser
- #3626 LinkHqWorkerResponseParser + RetrieveChannelEncryptionKeyResponseParser

## Round-Robin (last run dates)
- T1:2026-03-11 T2:2026-03-19 T3:2026-03-18
- T4:2026-03-19 T5:2026-03-19 T6:2026-03-14 T7:2026-03-19
