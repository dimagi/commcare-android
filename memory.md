# Test Improver Memory - dimagi/commcare-android

## Commands
- Unit tests: `./gradlew testCommcareDebug`
- Coverage: `./gradlew JacocoTestReport`
- Format: `ktlint --format path/to/file.kt`
- Requires `../commcare-core/` sibling dir (auto in CI)

## Project
- Unit tests: `app/unit-tests/src/org/commcare/`
- Pattern: `@RunWith(AndroidJUnit4::class)` + `@Config(application = CommCareTestApplication::class)`
- Frameworks: JUnit4 + Robolectric 4.8.2 + MockK + Mockito
- New code: Kotlin. Existing: mixed Java/Kotlin.

## Backlog
1. ApkDependenciesUtils.kt - 100 lines, no tests (MEDIUM)
2. ConnectAppUtils.kt - shouldOverridePassword(), security-relevant (MEDIUM)
3. ConnectDateUtils formatNotificationTime - Context-dependent (LOW)

## Completed
- 2026-03-11: PR draft for ConnectDateUtils tests (branch: test-assist/connect-date-utils-tests)
- 2026-03-11: Created [Test Improver] Monthly Activity 2026-03 issue

## Round-Robin
- Task 1 (Commands): 2026-03-11
- Task 3 (Implement): 2026-03-11
- Task 7 (Monthly): 2026-03-11
- Task 2,4,5,6: pending
