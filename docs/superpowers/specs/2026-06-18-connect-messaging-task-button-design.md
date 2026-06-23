# Conversation Tasks and CTA to open Messaging from the Home Screen


## Overview
https://dimagi.atlassian.net/browse/CCCT-2468

The Spec tries to ahieve following outcomes - 
1. Add an "Open Conversation" button to the Connect job tile on the home screen. The button is visible only when a `messaging`-type task is assigned and pending for the active job (in `STATUS_DELIVERING`). Tapping it launches `ConnectMessagingActivity` and navigates directly to the task's channel.
2. Provide DB persistence for tasks and remodel existing code to use the DB 
3. Trigger Sync on home screen after a change in task given the app usually needs to refresh the case data after a change in task to implement corresponding app side logic.
4. Define payload for notification sent on a task completion

## Data Layer

### `ParsedConnectTask` (delete)
`ParsedConnectTask` is removed entirely. All other Connect DB record models (`ConnectJobDeliveryRecord`, `ConnectJobPaymentRecord`, etc.) parse directly from JSON via a `fromJson(JSONObject, ConnectJobRecord)` static factory method — no intermediate transient model. `ConnectTaskRecord` follows the same pattern.

### New `ConnectTaskRecord` (DB-persisted, Java)
New table `connect_tasks` in the Connect encrypted DB, following the `@Table` / `@Persisting` / `@MetaField` pattern used by `ConnectJobDeliveryRecord`.

Fields:

| Field              | Type | Notes |
|--------------------|---|---|
| `opportunity_uuid` | String | Binds the task to Opp |
| `task_id`          | String | Unique task identifier from server |
| `name`             | String | Required |
| `description`      | String | Optional |
| `status`           | String | `"assigned"` or `"completed"` |
| `slug`             | String | Channel ID or task-specific ID; unique |
| `type`             | String | `"messaging"` or `"relearn"` |
| `due_date`         | Date | Nullable |
| `date_created`     | Date | Default to now |
| `date_modified`    | Date | Default to now |

### `ConnectTaskUtils` (new class)
Lives in `org.commcare.connect.database`, alongside `ConnectJobUtils`. Owns all task-specific DB operations so that `ConnectJobUtils` is not further enlarged.
  
- `storeTasks(context, List<ConnectTaskRecord>, jobUUID): Boolean` — fetches the existing tasks for the job from the DB, diffs them against the incoming list (comparing `taskId`, `type`, `status`, and `slug` per task). If anything changed (new task, removed task, or field change), replaces the stored list and returns `true`. Returns `false` if the lists are identical.
- `hasPendingTask(context, jobUUID): Boolean` — returns `true` if any task for the job has `status = "assigned"`, regardless of type. Used by `ConnectJobRecord.isTaskPending(context)` and `ConnectJobRecord.shouldShowTasksCompletedMessage(context)`. **Fallback:** if the DB has no task rows for the job, read `KEY_RELEARN_TASK_PENDING` from `ConnectJobPreferences`; if that pref is `true`, return `true`. 
- `hasPendingTaskOfType(context, jobUUID, type): Boolean` — returns `true` if a task of the given `type` has `status = "assigned"`. Used for the messaging button visibility check. Avoids the problem of a relearn task hiding the messaging CTA when both are pending. No preference fallback — messaging tasks have no prior pref equivalent.
- `getPendingTaskOfType(context, jobUUID, type): ConnectTaskRecord?` — returns the first pending task of the given `type`, or `null`. Used when the actual record is needed (e.g. messaging button needs the `slug` for navigation). `hasPendingTaskOfType` may delegate to this internally. No preference fallback.
- `getMostRecentlyCompletedTask(context, jobUUID): ConnectTaskRecord?` — returns the task with `status = "completed"` having the latest `dateModified`, regardless of type. **Fallback:** if the DB has no task rows for the job, read `KEY_RELEARN_TASKS_COMPLETED_TIME_MS` from `ConnectJobPreferences`; if set, synthesise a minimal `ConnectTaskRecord` with `dateModified` equal to that timestamp, return it. 

### `DeliveryAppProgressResponseModel` / Parser
- Replace `parsedTasks: List<ParsedConnectTask>` with `tasks: List<ConnectTaskRecord>` (empty list when `assigned_tasks` is absent or empty)
- Parser calls `ConnectTaskRecord.fromJson(obj, job)` for each item in `assigned_tasks`, matching how deliveries and payments are parsed.
- `applyToJob()` always calls `ConnectTaskUtils.storeTasks()` unconditionally, even when the list is empty — this ensures stale records are deleted when the server stops returning tasks. The `tasksChanged` return value gates the timestamp update only.

### DB Migration: V.26 → V.27
- `DatabaseConnectOpenHelper`: bump `CONNECT_DB_VERSION` to 27; register `ConnectTaskRecord` table in `onCreate`.
- `ConnectDatabaseUpgrader`: add upgrade case for version 26 that creates the `connect_tasks` table via `TableBuilder`.

### Relearn task migration to DB

The relearn task state moves fully from `ConnectJobPreferences` to the DB, so the new `ConnectTaskRecord` is the single source of truth for all task types, while `ConnectJobPreferences` 
are only used as a fallback to provide backward compatibility between releases. 

**`ConnectJobRecord` method changes**

**Removed methods** (two methods are deleted entirely):
- `shouldShowRelearnTasksCompletedMessage()` — removed; all callers migrate to `shouldShowTasksCompletedMessage(context)`.
- `syncRelearnTasksPrefs(List<ParsedConnectTask>)` — removed; task persistence is now handled entirely by `applyToJob()` calling `ConnectTaskUtils.storeTasks()`. The `applyToJob()` call to `job.syncRelearnTasksPrefs(parsedTasks)` is deleted. No `ParsedConnectTask` list exists anywhere in the new flow.

**New/renamed methods** — both gain a `Context` parameter since they now query the DB:
- `isTaskPending(context)` replaces `isRelearnTaskPending()`. Implementation: `ConnectTaskUtils.hasPendingTask(context, jobUUID) && status == STATUS_DELIVERING`.
- `shouldShowTasksCompletedMessage(context)` replaces `shouldShowRelearnTasksCompletedMessage()`. Implementation:
  1. Return `false` if any task is still assigned (`ConnectTaskUtils.hasPendingTask(context, jobUUID)` is true).
  2. Fetch `ConnectTaskUtils.getMostRecentlyCompletedTask(context, jobUUID)`.
  3. Return `true` if that task's `dateModified` is within `RELEARN_TASKS_COMPLETED_MESSAGE_WINDOW_MS` of now.

**Unchanged method** — `getCardMessageText(Context)` keeps its existing signature. Its internal call to `shouldShowRelearnTasksCompletedMessage()` becomes `shouldShowTasksCompletedMessage(context)` using the `context` it already receives.

**`ConnectJobPreferences`** — do **not** remove `KEY_RELEARN_TASK_PENDING`, `KEY_RELEARN_TASKS_COMPLETED_TIME_MS`, or their accessor methods yet. Mark them `@Deprecated` with a note: *"Remove after 2.64 release."*

**Call sites** — the following external callers of the removed/renamed methods must be updated:

| File | Old call | New call |
|---|---|---|
| `StandardHomeActivityUIController.java:123` | `job.isRelearnTaskPending()` | `job.isTaskPending(activity)` |
| `StandardHomeActivityUIController.java:198` | `job.isRelearnTaskPending()` | `job.isTaskPending(activity)` |
| `StandardHomeActivityUIController.java:157` | `job.shouldShowRelearnTasksCompletedMessage()` | `job.shouldShowTasksCompletedMessage(activity)` |
| `ConnectViewUtils.kt:35` | `job.isRelearnTaskPending` | `job.isTaskPending(context)` |
| `ConnectDeliveryProgressFragment.java:265` | `job.shouldShowRelearnTasksCompletedMessage()` | `job.shouldShowTasksCompletedMessage(requireContext())` |
| `DeliveryAppProgressResponseModel.kt:27` | `job.syncRelearnTasksPrefs(parsedTasks)` | deleted |

## UI Layer

### `view_progress_job_card.xml`
Add a new `AppCompatButton`:
- `id`: `acb_open_conversation`
- `text`: `@string/connect_open_conversation_button_text` ("Open Conversation")
- `drawableStart`: `@drawable/ic_connect_message_large` (existing icon)
- `visibility`: `gone` by default
- Style: matches `acb_resume` (rounded lavender background, `connect_blue_color` text, no elevation/state animator, `textAllCaps="false"`)
- Constrained below `@id/cv_relearn_tasks_pending`, start-aligned to parent

### `StandardHomeActivityUIController`
- Add field: `AppCompatButton acbOpenConversation`, found in `setupConnectJobTile()` via `viewJobCard.findViewById(R.id.acb_open_conversation)`.
- In `syncJobCardVisibility(job)`:
  - If `job.getStatus() == STATUS_DELIVERING`: call `ConnectTaskUtils.hasPendingTaskOfType(activity, job.getJobUUID(), "messaging")` to determine visibility.
  - When visible and the button is tapped: call `ConnectTaskUtils.getPendingTaskOfType(activity, job.getJobUUID(), "messaging")` to retrieve the task and pass its `slug` to `ConnectNavHelper.unlockAndGoToMessagingWithChannel(activity, task.getSlug(), listener)`.
  - Button is hidden when no pending messaging task exists or the task's `slug` is empty.
- Button is hidden in all non-`STATUS_DELIVERING` states.

## Navigation

### `ConnectNavHelper`
Add:
```kotlin
fun goToMessagingWithChannel(context: Context, channelId: String) {
    val intent = Intent(context, ConnectMessagingActivity::class.java)
    intent.putExtra(ConnectMessagingActivity.CHANNEL_ID, channelId)
    context.startActivity(i)
}

fun unlockAndGoToMessagingWithChannel(
    activity: CommCareActivity<*>,
    channelId: String,
    listener: ConnectActivityCompleteListener,
) {
    unlockAndGoTo(activity, UnlockPolicy.SESSION_WITH_TIME_THRESHOLD, listener) { context ->
        goToMessagingWithChannel(context, channelId)
    }
}
```

### `ConnectMessagingActivity` Refactor

**Extract shared method:**
```java
private void navigateToChannel(String channelId) {
    handleChannelForValidity(channelId);
}
```

`handleChannelForValidity` (and `handleNoChannel`, `handleValidChannel`) are unchanged — `navigateToChannel` is simply a named entry point into that existing chain.

**`handleRedirectIfAny()` becomes thin** — it still owns the PersonalId unlock (needed for the push notification path), but after a successful unlock it just extracts `channelId` from the notification intent extras and calls `navigateToChannel(channelId)`.

**`onCreate()` direct-launch path** — the direct-launch path is already unlocked by `ConnectNavHelper.unlockAndGoToMessagingWithChannel` before the activity starts, so no second unlock is needed. After the existing `handleRedirectIfAny()` call, add:
```java
String directChannelId = getIntent().getStringExtra(CHANNEL_ID);
if (!TextUtils.isEmpty(directChannelId)) {
    navigateToChannel(directChannelId);
}
```

Both paths converge on `handleChannelForValidity`, which remains unchanged.

## Sync Trigger on Task Change

The button's visibility depends on task data from the delivery progress API (`assigned_tasks`). If tasks changed on the server since the last CommCare sync, local case/form data may be stale. A sync should be automatically triggered in that situation.

### Track last task update time
In `ConnectJobPreferences`, add:
- `getLastTaskUpdateTimeMs(): Long` (default `TIMESTAMP_NOT_SET`)
- `setLastTaskUpdateTimeMs(timeMs: Long)`

In `DeliveryAppProgressResponseModel.applyToJob()`, always call `storeTasks()` — the outer `hasTasks` guard is removed. Only the inner check gates the timestamp:
```kotlin
val tasksChanged = ConnectTaskUtils.storeTasks(context, tasks, job.jobUUID)
if (tasksChanged) {
    ConnectJobUtils.getJobPreferences(job.jobUUID).setLastTaskUpdateTimeMs(System.currentTimeMillis())
}
```

### Add else-if block in `checkForPendingAppHealthActions`
Add a new `else if` branch at the end of the existing chain in `HomeScreenBaseActivity.checkForPendingAppHealthActions()`, before the log-submission block:

```java
} else if (shouldTriggerSyncForConnectTaskUpdate()) {
    triggerSync(true);
    kickedOff = true;
}
```

Where `shouldTriggerSyncForConnectTaskUpdate()` is a new private helper in `HomeScreenBaseActivity`:
```java
private boolean shouldTriggerSyncForConnectTaskUpdate() {
    ConnectJobRecord job = ConnectJobHelper.getJobForSeatedApp(this);
    if (job == null || job.getStatus() != ConnectJobRecord.STATUS_DELIVERING) {
        return false;
    }
    ConnectJobPreferences prefs = ConnectJobUtils.getJobPreferences(job.getJobUUID());
    long lastTaskUpdate = prefs.getLastTaskUpdateTimeMs();
    if (lastTaskUpdate == ConnectJobPreferences.TIMESTAMP_NOT_SET) {
        return false;
    }
    return lastTaskUpdate > SyncDetailCalculations.getLastSyncTime();
}
```

Note: `shouldTriggerSyncForConnectTaskUpdate` is unrelated to task type — it fires whenever any task changes, regardless of type.
}
```

The check only fires when no earlier health action already kicked off a sync. It short-circuits immediately for jobs not in `STATUS_DELIVERING`, and when no task change has been recorded yet.

## Task Completion Push Notification

### Payload

```json
{
  "notifications": [
    {
      "action": "ccc_generic_opportunity",
      "title": "Notification Title",
      "body": "Notification description",
      "notification_id": "uuid",
      "created": "2024-05-21T10:00:00Z",
      "status": "unread",
      "opportunity_uuid": "opp_uuid",
      "opportunity_status": "delivery",
      "key": "task_completion",
      "session_endpoint_id": "cc_app_home"
    }
  ]
}
```

### How the app handles it

`action = "ccc_generic_opportunity"` is already handled by `NotificationsSyncWorkerManager` — it calls `startOpportunitiesSyncWorker` and `startJobProgressSyncWorker`. The job progress sync fetches the delivery progress endpoint, which now includes `assigned_tasks`. With the new DB layer, updated tasks are stored as `ConnectTaskRecord` rows and `storeTasks()` returns `true` if anything changed, stamping `lastTaskUpdateTimeMs` in `ConnectJobPreferences`. On the next `checkForPendingAppHealthActions` call (when the user views the home screen), `shouldTriggerSyncForConnectTaskUpdate()` detects the change and triggers a CommCare app sync.

`key = "task_completion"` is stored in `PushNotificationRecord.key` (field added in V.24). No new DB or parse changes are needed — the field is already supported. The `PushNotificationActivity` list uses `key` to identify and display the notification type to the user.

`session_endpoint_id = "cc_app_home"` routes the user to the CommCare app home screen when the notification is tapped, using the existing session endpoint dispatch flow in `DispatchActivity`. No new handling needed.

## Constraints & Edge Cases
- Button is hidden when the job is not in `STATUS_DELIVERING`, even if a messaging task exists in the DB.
- A pending relearn task does not suppress the messaging button — `hasPendingTaskOfType` checks the type independently, so both task types can be pending simultaneously without interference.
- If `getPendingTaskOfType` returns a task with a null/empty `slug`, the button is hidden (defensive guard before navigation).
- `ConnectMessagingActivity` already handles the case where a channel is not yet in the local DB (`handleNoChannel` fetches it from the server before navigating).
