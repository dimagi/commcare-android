# Test Improver - dimagi/commcare-android

## Commands
- Tests: `./gradlew testCommcareDebug` (single: `--tests "pkg.ClassTest"`); Coverage: `./gradlew JacocoTestReport`; Lint: `./gradlew ktlintFile -PfilePath=...`
- Needs `../commcare-core/` sibling. SANDBOX CANNOT run gradle (no wrapper dists / no core) -> verify in CI. ktlint hook `.claude/hooks/ktlint-check.sh` always fails in sandbox (infra, ignore). No `.editorconfig` -> line-length unenforced.

## Project
- Tests: `app/unit-tests/src/org/commcare/`; JUnit4 4.13.2 + Robolectric + MockK/Mockito.
- Pure-logic tests: plain JUnit4, no `@RunWith`. Template `OtpAnalyticsMapperTest.kt` (backtick names, trailing commas multiline, specific assertEquals). `internal` main decls visible from unit-test source set.

## Maintainer Notes
- "go or no go" - close if not ready; full-class coverage; specific+date assertions.

## Backlog
1. MainCoroutineRule.kt - LOW - deprecate TestCoroutineDispatcher (issue 2026-04-02).
2. Untested candidates (assess first): DeepLinkHelper, ConnectSsoSyncHelper, NotificationBroadcastHelper. Many Util/Helper classes Android/dialog-heavy - poor unit candidates.

## PRs / State
- 2026-07-15: OutcomeMapperTest PR (20 tests, branch test-assist/outcome-mapper) - awaiting review.
- All April PRs (#3610,#3612,#3614,#3619,#3626,#3632,#3635,#3636,#3637,#3645) CLOSED/MERGED.
- Monthly: April #3642 CLOSED 2026-07-15; July issue created.

## Round-Robin (last run)
- T1-T4,T7: 2026-07-15. T5,T6: 2026-04-02 (oldest - do next).
