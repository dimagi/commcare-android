# Parse Relearn Tasks From Server — Design

## Goal

Extend the Connect delivery-progress API integration to parse a new `assigned_tasks`
array from the server response, persist each task in a new DB table, and use the
parsed tasks to drive the already-stubbed relearn-tasks UI behaviors on
`ConnectJobRecord`.

## Context

- `DeliveryAppProgressResponseParser.kt` currently parses `deliveries` and
  `payments` from the delivery-progress response into `ConnectJobDeliveryRecord`
  and `ConnectJobPaymentRecord` instances. Tasks follow the same shape.
- `ConnectJobRecord.isRelearnTaskPending()` is currently a stub returning `false`.
- `ConnectJobRecord.shouldShowRelearnTasksCompletedMessage()` already reads the
  `RELEARN_TASKS_COMPLETED_TIME` shared pref correctly; what's missing is the
  write-side logic that updates the pref when task data arrives.
- Both methods are consumed by UI code
  (`StandardHomeActivityUIController`, `ConnectDeliveryProgressFragment`,
  `getCardMessageText`) to show/hide the relearn-pending card, apply green
  styling, and hide delivery progress.
- The Connect DB is at version 24; adding a new table requires a bump to 25 with
  a migration.

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

## Design

### 1. New model: `ConnectTaskRecord` (Kotlin)

New file: `app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt`

- `@Table("connect_tasks")`, extends `Persisted`, implements `Serializable`
- Status constants:
  - `STATUS_ASSIGNED = "assigned"`
  - `STATUS_COMPLETED = "completed"`
- `@MetaField` column names: `META_JOB_ID`, `META_JOB_UUID` (reuses
  `ConnectJobRecord.META_JOB_UUID`), `META_ASSIGNED_TASK_ID`, `META_TASK_NAME`,
  `META_TASK_DESCRIPTION`, `META_STATUS`, `META_DUE_DATE`, `META_DATE_CREATED`,
  `META_DATE_MODIFIED`
- Persisted fields (each with `@Persisting(N)` + `@MetaField`):
  1. `jobId: Int`
  2. `jobUUID: String`
  3. `assignedTaskId: String`
  4. `taskName: String`
  5. `taskDescription: String` — empty string when absent in JSON
  6. `status: String`
  7. `dueDate: Date`
  8. `dateCreated: Date?` — nullable
  9. `dateModified: Date?` — nullable
  10. `lastUpdate: Date` — internal; set on persist
- `companion object { fun fromJson(json: JSONObject, job: ConnectJobRecord): ConnectTaskRecord }`
  - Required fields read directly; throw `JSONException` on absence (matches
    existing record patterns).
  - Optional string read via `JsonExtensions.optStringSafe(..., "")`.
  - Optional datetimes read via `DateUtils.parseDateTime(...)` guarded by
    `json.has(key) && !json.isNull(key)`; otherwise `null`.
  - Sets `jobId`/`jobUUID` from the `job` parameter.

**Kotlin + `Persisted` framework caveat:** all existing `ConnectJob*Record`
models are Java. The `Persisted` framework uses reflection on `@Persisting` /
`@MetaField` annotations over Java-visible fields, so Kotlin should work (fields
are Java-visible by default). To be verified at implementation time; if a
framework constraint is discovered, flag and revisit.

### 2. Parser wiring

Modify `DeliveryAppProgressResponseParser.kt`:

- Add local `var hasTasks = false`.
- After the `payments` block, add a symmetric `assigned_tasks` block that
  populates a `MutableList<ConnectTaskRecord>`, sets `job.tasks = tasks`, and
  sets `hasTasks = true`.
- Return value: `DeliveryAppProgressResponseModel(updatedJob, hasDeliveries,
  hasPayment, hasTasks)`.

Modify `DeliveryAppProgressResponseModel`:
- Add `hasTasks: Boolean` field (new required constructor arg).

Modify `ConnectJobRecord.java`:
- Add transient (non-`@Persisting`) field `private List<ConnectTaskRecord>
  tasks;` alongside `deliveries`/`payments`.
- Add `getTasks()` and `setTasks(List<ConnectTaskRecord>)`.

Modify `ConnectJobHelper.kt` inside `updateDeliveryProgress`'s `onSuccess`:

```kotlin
if (deliveryAppProgressResponseModel.hasTasks) {
    ConnectJobUtils.storeTasks(context, job.tasks, job.jobUUID, true)
    ConnectJobRecord.syncRelearnTasksCompletedTime(job.tasks)
}
```

Modify `ConnectJobUtils.java`:
- Add `storeTasks(context, tasks, jobUUID, pruneMissing)` mirroring
  `storeDeliveries`, keyed on `assignedTaskId` (string) for the
  still-exists check.
- Add `getTasks(context, jobUUID, storage)` helper, and include it in
  `populateJobs(...)` so DB reads rehydrate the list the same way deliveries
  are rehydrated.

### 3. `syncRelearnTasksCompletedTime` helper

Static method on `ConnectJobRecord`, called from `ConnectJobHelper` after
`storeTasks`:

```java
public static void syncRelearnTasksCompletedTime(List<ConnectTaskRecord> tasks) {
    ICommCarePreferenceManager prefs = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
    if (prefs == null) return;
    if (tasks == null || tasks.isEmpty()) return;

    boolean anyAssigned = false;
    Date latestModified = null;
    for (ConnectTaskRecord t : tasks) {
        if (ConnectTaskRecord.STATUS_ASSIGNED.equals(t.getStatus())) {
            anyAssigned = true;
        }
        Date modified = t.getDateModified();
        if (modified != null && (latestModified == null || modified.after(latestModified))) {
            latestModified = modified;
        }
    }

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

Semantics:
- Empty/null list → no signal; leave pref untouched.
- Any `"assigned"` task → reset to `-1`.
- All `"completed"` AND pref currently `-1` → set to the latest `date_modified`
  across records, falling back to current device time if none have one.
- All `"completed"` AND pref already set → leave alone (preserves the 6-hour
  banner window already in progress).

### 4. Addressing the TODOs in `ConnectJobRecord`

**`isRelearnTaskPending()`** — replace the stub body:

```java
public boolean isRelearnTaskPending() {
    if (tasks == null) return false;
    for (ConnectTaskRecord t : tasks) {
        if (ConnectTaskRecord.STATUS_ASSIGNED.equals(t.getStatus())) {
            return true;
        }
    }
    return false;
}
```

Remove the `// TODO: Not yet implemented` comment.

**`shouldShowRelearnTasksCompletedMessage()`** — the read-side logic
(pref != -1 AND elapsed < 6 hours) is already correct. The write-side logic
the TODO described now lives in `syncRelearnTasksCompletedTime(...)`. Change
here is purely: remove the multi-line `// TODO:` block. Method body stays.

### 5. DB schema

- `DatabaseConnectOpenHelper.onCreate`: add
  ```java
  builder = new TableBuilder(ConnectTaskRecord.class);
  database.execSQL(builder.getTableCreateString());
  ```
- Bump `CONNECT_DB_VERSION` from `24` to `25`.
- Add `V.25 - Added ConnectTaskRecord table` to the version comment block.
- `ConnectDatabaseUpgrader`: add
  ```java
  if (oldVersion == 24) {
      upgradeTwentyFourTwentyFive(db);
      oldVersion = 25;
  }
  ```
  and
  ```java
  private void upgradeTwentyFourTwentyFive(IDatabase db) {
      addTableForNewModel(db, ConnectTaskRecord.STORAGE_KEY, new ConnectTaskRecord());
  }
  ```

### 6. Testing

Unit tests (under `app/unit-tests/src/`):

- `ConnectTaskRecord.fromJson`:
  - all required fields present; optional strings/dates absent → parses with
    empty description, null created/modified
  - optional fields present as nulls → parses as null
  - optional fields present with values → parses through
- `ConnectJobRecord.syncRelearnTasksCompletedTime`:
  - empty list → pref untouched
  - any assigned task → pref reset to `-1`
  - all completed, pref `-1`, records have `date_modified` → pref set to
    latest `date_modified`
  - all completed, pref `-1`, no records have `date_modified` → pref set to
    current time (verified via clock abstraction or approximate match)
  - all completed, pref already set → pref untouched
- `ConnectJobRecord.isRelearnTaskPending`:
  - null tasks → false
  - empty tasks → false
  - all completed → false
  - at least one assigned → true
- `DeliveryAppProgressResponseParser`:
  - fixture with `assigned_tasks` → `job.tasks` populated and `hasTasks == true`
  - fixture without `assigned_tasks` → `hasTasks == false`, `job.tasks`
    untouched

## Out of scope

- No UI changes; the existing consumers of `isRelearnTaskPending()` and
  `shouldShowRelearnTasksCompletedMessage()` already render the right UI —
  this work just makes those methods reflect real task data.
- No server-side changes.
- No changes to relearn-task scheduling/creation logic; we only consume
  records the server sends.
