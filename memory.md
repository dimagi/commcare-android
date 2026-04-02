# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`; Coverage: `./gradlew JacocoTestReport`
- Lint: `./gradlew ktlintFile -PfilePath=...`; Needs `../commcare-core/` sibling

## Project
- Tests: `app/unit-tests/src/org/commcare/`; JUnit4+Robolectric+MockK/Mockito
- `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- CommCareApplication.instance(): mockkStatic+mockk<CommCareApp>()
- Dates: abs(actual-expected) < 5000L tolerance
- pr-comment-handler (PR #3629, 2026-03-19) auto-implements reviewer feedback every 4h

## Maintainer Notes
- "go or no go" — close if not ready (shubham1g5); full-class coverage; specific+date assertions (conroy-ricketts)

## Backlog
1. PersonalIdWorkHistory.fromJsonArray() - MEDIUM - 10-field JSON parser, JSONException handling, no DB deps
2. MainCoroutineRule.kt - LOW - deprecate TestCoroutineDispatcher→StandardTestDispatcher (issue filed 2026-04-02)

## Open PRs
- #3610 HashUtils (2 approvals, merge-ready)
- #3612 NetworkUtils (no-go)
- #3614 DeliveryAppProgressResponseParser (no-go)
- #3619 LearningAppProgressResponseParser (no-go)
- #3626 LinkHqWorker+RetrieveChannelEncryptionKey (no-go; pr-comment-handler updated 2026-04-01)
- #3632 ConnectOpportunitiesParser (awaiting review)
- #3635 RetrieveHqTokenResponseParser (awaiting review)
- #3636 PushNotificationRecord (awaiting review)
- #3637 PushNotificationApiHelper (awaiting review)

## Monthly Activity
- April issue #3642 open

## Round-Robin
- T1:2026-04-01 T2:2026-03-31 T3:2026-03-30
- T4:2026-04-01 T5:2026-04-02 T6:2026-04-02 T7:2026-04-02
