# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug`; Coverage: `./gradlew JacocoTestReport`
- Lint: `./gradlew ktlintFile -PfilePath=...`; Needs `../commcare-core/`
- Note: Gradle wrapper needs `~/.gradle/wrapper/dists` write access (unavailable in sandbox)

## Project
- Tests: `app/unit-tests/src/org/commcare/`; JUnit4+Robolectric+MockK/Mockito
- `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- pr-comment-handler (PR #3629) auto-implements reviewer feedback every 4h

## Maintainer Notes
- "go or no go" — close if not ready; full-class coverage; specific+date assertions

## Backlog
1. MainCoroutineRule.kt - LOW - deprecate TestCoroutineDispatcher (issue filed 2026-04-02)

## Open PRs
- #3610 HashUtils (2 approvals, merge-ready)
- #3612 NetworkUtils (no-go); #3614 Delivery (no-go); #3619 Learning (no-go)
- #3626 LinkHqWorker (no-go; updated 2026-04-01)
- #3632 ConnectOpportunitiesParser; #3635 RetrieveHqToken; #3636 PushNotifRecord; #3637 PushNotifApi (awaiting review)
- NEW PersonalIdWorkHistory.fromJsonArray() (submitted 2026-04-03, 11 tests)

## Monthly Activity
- April issue #3642 open

## Round-Robin
- T1:2026-04-01 T2:2026-03-31 T3:2026-04-03
- T4:2026-04-01 T5:2026-04-02 T6:2026-04-02 T7:2026-04-03
