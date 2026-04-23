# Parse Relearn Tasks From Server — Design (shared-prefs revision)

> Supersedes `2026-04-22-parse-relearn-tasks-design.md`. The prior design
> persisted each parsed task to a new `connect_tasks` DB table (Connect DB
> v24 → v25 migration). This revision avoids database changes: the only
> persistent state the feature actually needs — a per-job "any assigned
> task?" flag and the already-stored `RELEARN_TASKS_COMPLETED_TIME`
> timestamp — is kept in shared preferences.

## Goal

Extend the Connect delivery-progress API integration to parse a new
`assigned_tasks` array from the server response and use those tasks to
drive the already-stubbed relearn-tasks UI behaviors on
`ConnectJobRecord` (`isRelearnTaskPending()` and
`shouldShowRelearnTasksCompletedMessage()`). No new DB table, no
migration — the two signals the UI consumes are persisted via the
existing `ICommCarePreferenceManager` long API.

## Context

- `DeliveryAppProgressResponseParser.kt` currently parses `deliveries`
  and `payments` from the delivery-progress response into
  `ConnectJobDeliveryRecord` and `ConnectJobPaymentRecord` instances.
  Tasks follow the same JSON shape but the persistence requirement for
  this feature is much lighter — the UI reads only aggregate signals,
  not per-task fields.
- `ConnectJobRecord.isRelearnTaskPending()` is currently a stub
  returning `false`.
- `ConnectJobRecord.shouldShowRelearnTasksCompletedMessage()` already
  reads the `RELEARN_TASKS_COMPLETED_TIME` shared pref correctly; what's
  missing is the write-side logic that updates the pref when task data
  arrives.
- Both methods are consumed by UI code
  (`StandardHomeActivityUIController`, `ConnectDeliveryProgressFragment`,
  `ConnectViewUtils.kt`, `ConnectJobRecord.getCardMessageText`) to
  show/hide the relearn-pending card, apply green styling, and hide
  delivery progress. None of these consumers read individual task
  fields (`task_name`, `task_description`, `due_date`, `date_created`,
  `date_modified`) — they only ask "is any task pending?" and "should
  the completed-banner show?".
- `ICommCarePreferenceManager` (commcare-core) currently exposes only
  `putLong` / `getLong`. Extending it is possible but unnecessary for
  this ticket; the new per-job pending flag is stored as a long
  (`0`/`1`), consistent with how `RELEARN_TASKS_COMPLETED_TIME` already
  overloads a long (`-1` sentinel).

## Server payload

The delivery-progress response gains a new top-level field:

```
"assigned_tasks": [
  {
    "assigned_task_id": "<string, required>",
    "task_name":        "<string, required>",
    "task_description": "<string, optional>",
    "status":           "assigned" | "completed",
    "due_date":         "<datetime string, required>",
    "date_created":     "<datetime string, optional>",
    "date_modified":    "<datetime string, optional>"
  },
  ...
]
```

`status` has exactly two valid values:

- `"assigned"` — task has been assigned but not yet completed
- `"completed"` — task has been completed via a submitted form

Of these fields, only `status` and `date_modified` are used after
parsing; the remaining fields are read into local values during parse
and then discarded.

## Design

### 1. Persistence model

**No DB changes.** Two shared-pref keys drive the feature:

| Key                                                    | Scope    | Type  | Semantics                                                                                     |
| ------------------------------------------------------ | -------- | ----- | --------------------------------------------------------------------------------------------- |
| `RELEARN_TASKS_COMPLETED_TIME` (existing)              | global   | long  | `-1` = no banner; otherwise epoch ms when tasks flipped to all-completed; banner shows < 6h.  |
| `RELEARN_TASK_PENDING_PREFIX + jobUUID` (new)          | per-job  | long  | `1` = at least one task currently `assigned`; `0` (or missing) = none.                        |

The new constant lives in `ConnectConstants`:

```java
public final static String RELEARN_TASK_PENDING_PREFIX = "relearn_task_pending_";
```

Pref lifecycle matches existing behavior: keys are written when a
delivery-progress response arrives and never explicitly cleared on
logout or job deletion. A stale key reads as `0` (via the default in
`getLong(..., 0)`), which is the correct "no pending task" answer, so
the lack of cleanup is harmless. Pref cleanup is explicitly out of
scope for this ticket.

### 2. Parser-local data class: `ParsedConnectTask`

A plain Kotlin `data class` — **not** `Persisted`, not
`@Table`-annotated, no `@MetaField`s. Exists solely to give the sync
helper a testable input and to make `DeliveryAppProgressResponseModel`
self-contained.

New file:
`app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt`

```kotlin
package org.commcare.connect.network.connect.models

import java.util.Date

data class ParsedConnectTask(
    val status: String,
    val dateModified: Date?,
)
```

Rationale for the package: colocated with
`DeliveryAppProgressResponseModel.kt`, since that model now carries a
`List<ParsedConnectTask>`.

### 3. Parser wiring

**`DeliveryAppProgressResponseModel.kt`** gains two fields:

```kotlin
data class DeliveryAppProgressResponseModel(
    var updatedJob: Boolean = false,
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var hasTasks: Boolean = false,
    var parsedTasks: List<ParsedConnectTask> = emptyList(),
)
```

`parsedTasks` is a transient, non-persistent field — it exists only
long enough to be handed to the sync helper in `ConnectJobHelper`.

**`DeliveryAppProgressResponseParser.kt`** changes:

- Add `var hasTasks = false` alongside the existing flags.
- Add `val parsedTasks: MutableList<ParsedConnectTask> = mutableListOf()`.
- After the `payments` block, add a symmetric `assigned_tasks` block:

  ```kotlin
  if (json.has("assigned_tasks")) {
      hasTasks = true
      val array = json.getJSONArray("assigned_tasks")
      for (i in 0 until array.length()) {
          val obj = array[i] as JSONObject
          val status = obj.getString("status")
          val dateModified =
              if (obj.has("date_modified") && !obj.isNull("date_modified")) {
                  val raw = obj.getString("date_modified")
                  if (raw.isEmpty()) null else DateUtils.parseDateTime(raw)
              } else {
                  null
              }
          parsedTasks.add(ParsedConnectTask(status, dateModified))
      }
  }
  ```

- Return value:
  `DeliveryAppProgressResponseModel(updatedJob, hasDeliveries, hasPayment, hasTasks, parsedTasks)`.

Fields present in the JSON but not read (`assigned_task_id`,
`task_name`, `task_description`, `due_date`, `date_created`) are
intentionally ignored.

### 4. `ConnectJobRecord.syncRelearnTasksPrefs(...)`

New static method on `ConnectJobRecord`, called from `ConnectJobHelper`
after parsing:

```java
public static void syncRelearnTasksPrefs(String jobUUID, List<ParsedConnectTask> tasks) {
    ICommCarePreferenceManager prefs = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
    if (prefs == null || jobUUID == null) {
        return;
    }

    String pendingKey = RELEARN_TASK_PENDING_PREFIX + jobUUID;

    if (tasks == null || tasks.isEmpty()) {
        // Server sent zero tasks → provably nothing pending; leave the
        // completed-time pref alone (no "all completed" transition to
        // observe) but clear the per-job pending flag.
        prefs.putLong(pendingKey, 0);
        return;
    }

    boolean anyAssigned = false;
    Date latestModified = null;
    for (ParsedConnectTask t : tasks) {
        if (ConnectTaskStatus.ASSIGNED.equals(t.getStatus())) {
            anyAssigned = true;
        }
        Date modified = t.getDateModified();
        if (modified != null && (latestModified == null || modified.after(latestModified))) {
            latestModified = modified;
        }
    }

    prefs.putLong(pendingKey, anyAssigned ? 1 : 0);

    if (anyAssigned) {
        prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, -1);
    } else {
        long current = prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1);
        if (current == -1) {
            long ts = latestModified != null ? latestModified.getTime() : new Date().getTime();
            prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, ts);
        }
    }
}
```

Status constants live on a small Kotlin `object ConnectTaskStatus`
declared in the same file as `ParsedConnectTask`. `const val` in a
Kotlin `object` is accessible from Java as a compile-time constant,
so `ConnectTaskStatus.ASSIGNED` is usable directly in the Java code
in `ConnectJobRecord.syncRelearnTasksPrefs`:

```kotlin
object ConnectTaskStatus {
    const val ASSIGNED = "assigned"
    const val COMPLETED = "completed"
}
```

Semantics:

- `null` jobUUID or `null` prefs → no-op.
- Empty/null list → pending pref cleared to `0`; completed-time pref
  untouched.
- Any `"assigned"` task → pending pref set to `1`; completed-time
  pref reset to `-1`.
- All `"completed"` AND completed-time pref currently `-1` → pending
  pref set to `0`; completed-time pref set to the latest
  `date_modified` across records, falling back to current device time
  if none have one.
- All `"completed"` AND completed-time pref already set → pending
  pref set to `0`; completed-time pref left alone (preserves the
  6-hour banner window already in progress).

### 5. `ConnectJobRecord.isRelearnTaskPending()`

Replace the stub with a pref read:

```java
public boolean isRelearnTaskPending() {
    ICommCarePreferenceManager prefs = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
    if (prefs == null || jobUUID == null) {
        return false;
    }
    return prefs.getLong(RELEARN_TASK_PENDING_PREFIX + jobUUID, 0) == 1;
}
```

Remove the `// TODO: Not yet implemented` comment.

### 6. `ConnectJobRecord.shouldShowRelearnTasksCompletedMessage()`

Body stays identical. The multi-line `// TODO:` block above it is
removed — the write-side logic the TODO described now lives in
`syncRelearnTasksPrefs`.

### 7. `ConnectJobHelper.updateDeliveryProgress` wiring

Inside the `onSuccess` block, after the existing `hasPayment` handling:

```kotlin
if (deliveryAppProgressResponseModel.hasTasks) {
    ConnectJobRecord.syncRelearnTasksPrefs(
        job.jobUUID,
        deliveryAppProgressResponseModel.parsedTasks,
    )
}
```

No `ConnectJobUtils` changes; no `populateJobs` changes; no new DB
writes.

## Files touched

**Created:**

- `app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt`
  (data class + `ConnectTaskStatus` object)
- Unit test: `app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt`
- Unit test: `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`

**Modified:**

- `app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt`
  (add `hasTasks`, `parsedTasks`)
- `app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt`
  (parse `assigned_tasks` into `ParsedConnectTask`s)
- `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java`
  (new `isRelearnTaskPending()` impl via pref; new static
  `syncRelearnTasksPrefs(...)`; TODO cleanup)
- `app/src/org/commcare/connect/ConnectJobHelper.kt`
  (call `syncRelearnTasksPrefs` on `hasTasks`)
- `app/src/org/commcare/connect/ConnectConstants.java`
  (add `RELEARN_TASK_PENDING_PREFIX` constant)

**Not touched (explicit departure from the prior design):**

- `app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java`
- `app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java`
- `app/src/org/commcare/connect/database/ConnectJobUtils.java`
- No new Persisted model, no `connect_tasks` table, no Connect DB
  version bump.

## Testing

Unit tests (under `app/unit-tests/src/`):

- **`DeliveryAppProgressResponseParserTest`**:
  - Response with `assigned_tasks` present → `model.hasTasks == true`,
    `model.parsedTasks.size == N`, statuses map through,
    `dateModified` is `null` when absent and parsed when present.
  - Response without `assigned_tasks` → `hasTasks == false`,
    `parsedTasks.isEmpty()`.
  - Response with `assigned_tasks: []` (empty array) → `hasTasks ==
    true`, `parsedTasks.isEmpty()`. This exercises the
    "empty list clears pending flag" path in the sync helper.

- **`ConnectJobRecordRelearnTasksTest`** (MockK on
  `CommCarePreferenceManagerFactory.getCommCarePreferenceManager()`):

  For `syncRelearnTasksPrefs`:
  - Empty list → `putLong(pendingKey, 0)`; no completed-time write.
  - Any assigned task → `putLong(pendingKey, 1)` AND
    `putLong(RELEARN_TASKS_COMPLETED_TIME, -1)`.
  - All completed, completed-time pref currently `-1`, list has
    `date_modified` → `putLong(pendingKey, 0)` AND
    `putLong(RELEARN_TASKS_COMPLETED_TIME, latestDateModified.time)`.
  - All completed, completed-time pref currently `-1`, no
    `date_modified` on any record → `putLong(pendingKey, 0)` AND
    completed-time set to a long captured in
    `[System.currentTimeMillis() before, after]`.
  - All completed, completed-time pref already set (e.g.,
    `123456789L`) → `putLong(pendingKey, 0)`; completed-time NOT
    rewritten (`verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }`).
  - Two separate jobUUIDs → assert each `putLong(pendingKey, ...)`
    call targets the correctly-suffixed key.
  - `jobUUID == null` → no-op.
  - `prefs == null` → no-op.

  For `isRelearnTaskPending()`:
  - Pref returns `1` → `true`.
  - Pref returns `0` (or is missing, hits default) → `false`.
  - `jobUUID == null` → `false`.
  - `prefs == null` → `false`.

Ktlint + checkstyle scoped to the smaller set of modified files
(parser, data model, `ConnectJobRecord.java`, `ConnectJobHelper.kt`,
`ConnectConstants.java`).

## Out of scope

- No UI changes. The existing consumers of `isRelearnTaskPending()`
  and `shouldShowRelearnTasksCompletedMessage()` already render the
  right UI; this work just makes those methods reflect real task data.
- No server-side changes.
- No retention of per-task details (`task_name`, `task_description`,
  `due_date`, `date_created`, `assigned_task_id`) across sessions.
  The server is the source of truth; if a future ticket needs
  per-task UI, it will fetch again.
- No per-job / per-task pref cleanup on logout or job deletion.
  Matches the existing lifecycle of `RELEARN_TASKS_COMPLETED_TIME`; a
  stale `RELEARN_TASK_PENDING_PREFIX + jobUUID` pref reads as `0`,
  which is the correct "no pending task" answer.
- No extension of `ICommCarePreferenceManager` (no new
  `putBoolean`/`getBoolean`). The new pending flag uses the existing
  long API with a `0`/`1` convention.
