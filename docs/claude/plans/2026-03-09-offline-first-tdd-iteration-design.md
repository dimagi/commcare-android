# Design: TDD Iteration of Offline-First Connect Network Architecture Plan

## Overview

This design restructures the existing
[Offline-First Connect Network Architecture Implementation Plan](2026-02-23-offline-first-connect-architecture.md)
to follow strict Test-Driven Development (TDD). Tests are written before each implementation step,
enforcing the Red → Green cycle per phase pair.

---

## Phase Structure

The original 6-phase plan becomes 11 phases arranged as a/b pairs:

| Phase  | Type              | Content                                                                 |
|--------|-------------------|-------------------------------------------------------------------------|
| 0      | Implementation    | DataState + RefreshPolicy (unchanged — pure type definitions)           |
| 1a     | Tests (Red)       | ConnectSyncPreferencesTest + ConnectRequestManagerTest                  |
| 1b     | Implementation    | ConnectSyncPreferences + ConnectRequestManager                          |
| 1.5a   | Tests (Red)       | ConnectNetworkClientTest                                                |
| 1.5b   | Implementation    | ConnectApiService + ConnectNetworkClient + ConnectNetworkHelper + ConnectApiException |
| 2a     | Tests (Red)       | ConnectRepositoryTest                                                   |
| 2b     | Implementation    | ConnectRepository                                                       |
| 3a     | Tests (Red)       | ConnectJobsListViewModelTest + ConnectJobsListsFragmentTest (Robolectric)|
| 3b     | Implementation    | ConnectJobsListViewModel + migrate ConnectJobsListsFragment             |
| 4a     | Tests (Red)       | ConnectLearningProgressViewModelTest + ConnectLearningProgressFragmentTest (Robolectric) |
| 4b     | Implementation    | ConnectLearningProgressViewModel + migrate ConnectLearningProgressFragment |
| 5      | Validation        | Manual testing checklist only                                           |

---

## Content Changes

### Moved (no modification)
- `ConnectSyncPreferencesTest` full implementation (was Phase 5) → Phase 1a
- `ConnectRequestManagerTest` full implementation (was Phase 5) → Phase 1a
- Manual testing checklist (was Phase 5) → Phase 5 (renamed)

### New content (test signatures only — no full implementation)

**Phase 1.5a — `ConnectNetworkClientTest`**
- `testGetConnectOpportunities_success_returnsModel`
- `testGetConnectOpportunities_httpError_returnsFailure`
- `testGetConnectOpportunities_networkException_returnsNetworkError`
- `testGetConnectOpportunities_authHeaderFailure_returnsFailure`
- `testGetLearningProgress_success_returnsModel`
- `testGetLearningProgress_httpError_returnsFailure`

**Phase 2a — `ConnectRepositoryTest`**
- `testGetOpportunities_noCache_emitsLoading`
- `testGetOpportunities_withCache_emitsCachedThenSuccess`
- `testGetOpportunities_networkFailure_emitsError_withCachedData`
- `testGetOpportunities_networkFailure_noCache_emitsError_withNullCachedData`
- `testGetOpportunities_forceRefresh_bypassesShouldRefreshCheck`
- `testGetOpportunities_shouldRefreshFalse_emitsCachedOnly`
- `testGetOpportunities_networkSuccess_writesSyncTimestamp`
- `testGetLearningProgress_alwaysPolicy_alwaysFetches`

**Phase 3a — `ConnectJobsListViewModelTest`**
- `testLoadOpportunities_postsLoadingThenSuccess`
- `testLoadOpportunities_postsError_onFailure`
- `testLoadOpportunities_forceRefresh_passedToRepository`

**Phase 3a — `ConnectJobsListsFragmentTest` (Robolectric)**
- `testFragment_showsLoadingSpinner_onLoadingState`
- `testFragment_showsJobList_onSuccessState`
- `testFragment_showsJobList_onCachedState`
- `testFragment_showsError_onErrorState`
- `testFragment_showsCachedList_andErrorToast_whenErrorHasCachedData`

**Phase 4a — `ConnectLearningProgressViewModelTest`**
- `testLoadLearningProgress_postsLoadingThenSuccess`
- `testLoadLearningProgress_postsError_onFailure`

**Phase 4a — `ConnectLearningProgressFragmentTest` (Robolectric)**
- `testFragment_showsLoading_onLoadingState`
- `testFragment_updatesUI_onSuccessState`
- `testFragment_updatesUI_onCachedState`
- `testFragment_showsErrorToast_onErrorState_withCachedData`

### Removed
- Phase 5 automated test section (distributed into 1a/1.5a/2a/3a/4a)
- "Testing Strategy" summary section (redundant once tests are co-located with phases)

---

## TDD Enforcement Model

### `a` phase success criteria
1. `ktlint` passes on the new test file
2. `./gradlew compileDebugKotlin` — compile failure is acceptable if implementation types don't exist yet; the commit captures test intent
3. `./gradlew testDebugUnitTest --tests <TestClass>` **fails** (expected — no implementation yet)
4. Commit failing tests before proceeding to the `b` phase

### `b` phase success criteria
1. `./gradlew compileDebugKotlin` and `compileDebugJava` succeed
2. `./gradlew testDebugUnitTest --tests <TestClass>` **passes**
3. `ktlint` passes on all new implementation files
4. Commit passing implementation

### Phase 0 (no test step)
`DataState` and `RefreshPolicy` are pure sealed class definitions with no logic to test.
Phase 1a tests reference these types, providing an implicit compile-time contract check.

---

## Dependency Chain

Each phase depends on all prior phases being complete (compile + tests passing):

```
Phase 0 → Phase 1a → Phase 1b → Phase 1.5a → Phase 1.5b
       → Phase 2a → Phase 2b → Phase 3a → Phase 3b
                             → Phase 4a → Phase 4b → Phase 5
```

Phases 3a and 4a both depend on Phase 2b (ConnectRepository implemented).
Phases 3b and 4b can be worked in parallel after their respective `a` phases pass.
