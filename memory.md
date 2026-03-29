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
- `pr-comment-handler` (merged PR #3629, 2026-03-19) auto-implements reviewer feedback every 4h

## Maintainer Notes
- "go or no go" — close if not ready, no iterations (shubham1g5)
- Full-class coverage; specific assertions; date assertions required (conroy-ricketts)

## Backlog
1. MainCoroutineRule.kt - LOW - modernize TestCoroutineDispatcher (hold until PR backlog clears)

## Open PRs
- #3610 HashUtils (2 approvals, merge-ready)
- #3612 NetworkUtils (reviewed, updated 2026-03-25)
- #3614 DeliveryAppProgressResponseParser (no-go)
- #3619 LearningAppProgressResponseParser (no-go)
- #3626 LinkHqWorker + RetrieveChannelEncryptionKey (no-go)
- #3632 ConnectOpportunitiesParser (awaiting review)
- #3635 RetrieveHqTokenResponseParser (awaiting review)
- #3636 PushNotificationRecord (awaiting review, created 2026-03-28)

## Round-Robin
- T1:2026-03-27 T2:2026-03-28 T3:2026-03-28
- T4:2026-03-28 T5:2026-03-29 T6:2026-03-29 T7:2026-03-29
