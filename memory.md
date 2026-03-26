# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`; Coverage: `./gradlew JacocoTestReport`
- Lint: `./gradlew ktlintFile -PfilePath=...`; Needs `../commcare-core/` sibling

## Project
- Tests: `app/unit-tests/src/org/commcare/`
- `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- JUnit4 + Robolectric + MockK/Mockito
- CommCareApplication.instance(): mockkStatic(CommCareApplication::class) + mockk<CommCareApp>()
- Dates: abs(actual - expected) < 5000L tolerance

## Maintainer Notes
- "go or no go" — close if not ready, no iterations (shubham1g5)
- Full-class coverage; specific assertions; date assertions required (conroy-ricketts)

## Backlog
1. MainCoroutineRule.kt - LOW - modernize TestCoroutineDispatcher

## Open PRs
- #3610 HashUtils (2 approvals, merge-ready)
- #3612 NetworkUtils (reviewed)
- #3614 DeliveryAppProgressResponseParser (no-go)
- #3619 LearningAppProgressResponseParser (no-go)
- #3626 LinkHqWorker + RetrieveChannelEncryptionKey (no-go)
- #3632 ConnectOpportunitiesParser (awaiting review)
- pending: RetrieveHqTokenResponseParser (2026-03-26)

## Round-Robin
- T1:2026-03-21 T2:2026-03-23 T3:2026-03-26
- T4:2026-03-26 T5:2026-03-23 T6:2026-03-21 T7:2026-03-26
