# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`
- Coverage: `./gradlew JacocoTestReport`
- Lint: `./gradlew ktlintFile -PfilePath=path/to/file.kt`
- Needs `../commcare-core/` sibling

## Project
- Tests: `app/unit-tests/src/org/commcare/`
- Pattern: `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- JUnit4 + Robolectric + MockK/Mockito; mockStatic for static methods

## Maintainer Notes
- "go or no go" — no iterations on auto-PRs; close if not ready (shubham1g5)
- Full-class coverage per PR (conroy-ricketts)
- Specific assertions only; no non-null checks

## Backlog
1. ConnectOpportunitiesParser.kt - HIGH - 3 static mocks needed
2. RetrieveHqTokenResponseParser.kt - MEDIUM - CommCareApplication.instance() mock
3. MainCoroutineRule.kt - LOW - modernize deprecated TestCoroutineDispatcher

## Open PRs
- #3610 HashUtils (1 approval)
- #3612 NetworkUtils
- #3614 DeliveryAppProgressResponseParser
- #3619 LearningAppProgressResponseParser
- #3626 LinkHqWorkerResponseParser + RetrieveChannelEncryptionKeyResponseParser

## Round-Robin
- T1:2026-03-21 T2:2026-03-19 T3:2026-03-22(held)
- T4:2026-03-22 T5:2026-03-19 T6:2026-03-21 T7:2026-03-22
