# Parse Relearn Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse a new `assigned_tasks` array from the `delivery_progress` API response into a new persisted `ConnectTaskRecord` type, then use the parsed tasks to drive the already-stubbed `isRelearnTaskPending()` / relearn-tasks-completed UI logic on `ConnectJobRecord`.

**Architecture:** Mirror the existing `ConnectJobDeliveryRecord` pattern end-to-end — new Kotlin `@Table`-annotated `Persisted` class, JSON parsing in `DeliveryAppProgressResponseParser.kt`, `ConnectJobUtils.storeTasks` helper with prune-missing semantics, Connect DB version bump from 24 → 25 with a new-table migration, and transient `List<ConnectTaskRecord>` on `ConnectJobRecord`. Relearn-tasks-completed shared-pref updates live in a new static `ConnectJobRecord.syncRelearnTasksCompletedTime(...)` called from `ConnectJobHelper` after tasks are stored.

**Tech Stack:** Java + Kotlin, `Persisted`/`SqlStorage` framework (reflection on `@Persisting`/`@MetaField`), `org.json`, Robolectric + MockK for unit tests, Gradle.

---

## Spec reference

`docs/superpowers/specs/2026-04-22-parse-relearn-tasks-design.md`

## File map

**Created:**
- `app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt` — new Persisted model
- `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectTaskRecordTest.kt`
- `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`
- `app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt`

**Modified:**
- `app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt` — add `hasTasks`
- `app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt` — parse `assigned_tasks`
- `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java` — add `tasks` list, implement `isRelearnTaskPending()`, add static `syncRelearnTasksCompletedTime(...)`, clean up TODOs
- `app/src/org/commcare/connect/database/ConnectJobUtils.java` — add `storeTasks`, `getTasks`; update `populateJobs`
- `app/src/org/commcare/connect/ConnectJobHelper.kt` — call `storeTasks` + `syncRelearnTasksCompletedTime` inside `updateDeliveryProgress.onSuccess`
- `app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java` — register new table, bump `CONNECT_DB_VERSION`, add V.25 comment
- `app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java` — new `upgradeTwentyFourTwentyFive`

---

## Task 1: Create `ConnectTaskRecord` Kotlin model with `fromJson` (TDD)

**Files:**
- Create: `app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt`
- Test: `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectTaskRecordTest.kt`

Reference pattern: `PushNotificationRecord.kt` (Kotlin + `Persisted`) and `ConnectJobDeliveryRecord.java` (`fromJson` shape).

- [ ] **Step 1: Write the failing tests for `ConnectTaskRecord.fromJson`**

Create `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectTaskRecordTest.kt`:

```kotlin
package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectTaskRecordTest {

    private fun stubJob(): ConnectJobRecord {
        val job = ConnectJobRecord()
        job.jobId = 42
        job.jobUUID = "job-uuid-42"
        return job
    }

    @Test
    fun fromJson_allFieldsPresent_parsesEverything() {
        val json = JSONObject(
            """
            {
              "assigned_task_id": "task-1",
              "task_name": "Refresh module 3",
              "task_description": "Study the new protocol",
              "status": "assigned",
              "due_date": "2026-05-01T12:00:00.000",
              "date_created": "2026-04-20T09:00:00.000",
              "date_modified": "2026-04-21T10:30:00.000"
            }
            """.trimIndent()
        )

        val record = ConnectTaskRecord.fromJson(json, stubJob())

        assertEquals(42, record.jobId)
        assertEquals("job-uuid-42", record.jobUUID)
        assertEquals("task-1", record.assignedTaskId)
        assertEquals("Refresh module 3", record.taskName)
        assertEquals("Study the new protocol", record.taskDescription)
        assertEquals(ConnectTaskRecord.STATUS_ASSIGNED, record.status)
        assertEquals(DateUtils.parseDateTime("2026-05-01T12:00:00.000"), record.dueDate)
        assertEquals(DateUtils.parseDateTime("2026-04-20T09:00:00.000"), record.dateCreated)
        assertEquals(DateUtils.parseDateTime("2026-04-21T10:30:00.000"), record.dateModified)
        assertNotNull(record.lastUpdate)
    }

    @Test
    fun fromJson_optionalFieldsAbsent_usesDefaults() {
        val json = JSONObject(
            """
            {
              "assigned_task_id": "task-2",
              "task_name": "No description here",
              "status": "completed",
              "due_date": "2026-05-02T12:00:00.000"
            }
            """.trimIndent()
        )

        val record = ConnectTaskRecord.fromJson(json, stubJob())

        assertEquals("", record.taskDescription)
        assertNull(record.dateCreated)
        assertNull(record.dateModified)
        assertEquals(ConnectTaskRecord.STATUS_COMPLETED, record.status)
    }

    @Test
    fun fromJson_optionalFieldsExplicitlyNull_usesDefaults() {
        val json = JSONObject(
            """
            {
              "assigned_task_id": "task-3",
              "task_name": "Null fields",
              "task_description": null,
              "status": "assigned",
              "due_date": "2026-05-03T12:00:00.000",
              "date_created": null,
              "date_modified": null
            }
            """.trimIndent()
        )

        val record = ConnectTaskRecord.fromJson(json, stubJob())

        assertEquals("", record.taskDescription)
        assertNull(record.dateCreated)
        assertNull(record.dateModified)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectTaskRecordTest"`

Expected: **FAIL** — compilation error: `Unresolved reference: ConnectTaskRecord`.

- [ ] **Step 3: Create `ConnectTaskRecord.kt`**

Create `app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt`:

```kotlin
package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.commcare.utils.optStringSafe
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.util.Date

@Table(ConnectTaskRecord.STORAGE_KEY)
class ConnectTaskRecord : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_JOB_ID)
    var jobId: Int = 0

    @Persisting(2)
    @MetaField(META_JOB_UUID)
    var jobUUID: String = ""

    @Persisting(3)
    @MetaField(META_ASSIGNED_TASK_ID)
    var assignedTaskId: String = ""

    @Persisting(4)
    @MetaField(META_TASK_NAME)
    var taskName: String = ""

    @Persisting(5)
    @MetaField(META_TASK_DESCRIPTION)
    var taskDescription: String = ""

    @Persisting(6)
    @MetaField(META_STATUS)
    var status: String = ""

    @Persisting(7)
    @MetaField(META_DUE_DATE)
    var dueDate: Date = Date(0)

    @Persisting(value = 8, nullable = true)
    @MetaField(META_DATE_CREATED)
    var dateCreated: Date? = null

    @Persisting(value = 9, nullable = true)
    @MetaField(META_DATE_MODIFIED)
    var dateModified: Date? = null

    @Persisting(10)
    var lastUpdate: Date = Date()

    companion object {
        const val STORAGE_KEY: String = "connect_tasks"

        const val STATUS_ASSIGNED: String = "assigned"
        const val STATUS_COMPLETED: String = "completed"

        const val META_JOB_ID: String = "job_id"
        const val META_JOB_UUID: String = ConnectJobRecord.META_JOB_UUID
        const val META_ASSIGNED_TASK_ID: String = "assigned_task_id"
        const val META_TASK_NAME: String = "task_name"
        const val META_TASK_DESCRIPTION: String = "task_description"
        const val META_STATUS: String = "status"
        const val META_DUE_DATE: String = "due_date"
        const val META_DATE_CREATED: String = "date_created"
        const val META_DATE_MODIFIED: String = "date_modified"

        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject, job: ConnectJobRecord): ConnectTaskRecord {
            val record = ConnectTaskRecord()
            record.jobId = job.jobId
            record.jobUUID = job.jobUUID
            record.assignedTaskId = json.getString(META_ASSIGNED_TASK_ID)
            record.taskName = json.getString(META_TASK_NAME)
            record.taskDescription = json.optStringSafe(META_TASK_DESCRIPTION, "") ?: ""
            record.status = json.getString(META_STATUS)
            record.dueDate = DateUtils.parseDateTime(json.getString(META_DUE_DATE))
            record.dateCreated = parseOptionalDateTime(json, META_DATE_CREATED)
            record.dateModified = parseOptionalDateTime(json, META_DATE_MODIFIED)
            record.lastUpdate = Date()
            return record
        }

        private fun parseOptionalDateTime(json: JSONObject, key: String): Date? {
            if (!json.has(key) || json.isNull(key)) return null
            val raw = json.getString(key)
            if (raw.isEmpty()) return null
            return DateUtils.parseDateTime(raw)
        }
    }
}
```

**Note on `@Persisting(value = N, nullable = true)`:** the `Persisted` framework supports nullable persisted fields via `nullable = true`. If (and only if) a compilation error surfaces here, fall back to storing these as empty-string/long `-1` sentinels and adapt `parseOptionalDateTime` accordingly — but first verify the annotation argument exists by inspecting `org.commcare.models.framework.Persisting`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectTaskRecordTest"`

Expected: **PASS** — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt app/unit-tests/src/org/commcare/android/database/connect/models/ConnectTaskRecordTest.kt
git commit -m "Add ConnectTaskRecord model with fromJson parsing [AI]"
```

---

## Task 2: Add transient `tasks` field to `ConnectJobRecord`

**Files:**
- Modify: `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java`

Non-persisted — mirrors the `deliveries` / `payments` / `learnings` transient list pattern.

- [ ] **Step 1: Add the field and accessors**

In `ConnectJobRecord.java`, find the existing block (around line 176):

```java
    private List<ConnectJobDeliveryRecord> deliveries;
    private List<ConnectJobPaymentRecord> payments;
    private List<ConnectJobLearningRecord> learnings;
    private List<ConnectJobAssessmentRecord> assessments;
```

Add after `assessments`:

```java
    private List<ConnectTaskRecord> tasks;
```

Then, next to `getDeliveries` / `setDeliveries` (around line 428-437), add:

```java
    public List<ConnectTaskRecord> getTasks() {
        return tasks;
    }

    public void setTasks(List<ConnectTaskRecord> tasks) {
        this.tasks = tasks;
    }
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCommcareDebugJavaWithJavac`

Expected: **SUCCESS**. If the import for `ConnectTaskRecord` is missing, add it at the top of `ConnectJobRecord.java` alongside the other model imports (Kotlin classes are Java-interoperable; same import style).

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java
git commit -m "Add tasks list field to ConnectJobRecord [AI]"
```

---

## Task 3: Extend `DeliveryAppProgressResponseModel` with `hasTasks`

**Files:**
- Modify: `app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt`

- [ ] **Step 1: Add the field**

Replace the file contents:

```kotlin
package org.commcare.connect.network.connect.models

data class DeliveryAppProgressResponseModel(
    var updatedJob: Boolean = false,
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var hasTasks: Boolean = false,
)
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCommcareDebugKotlin`

Expected: **SUCCESS** (existing call sites pass named defaults so the new positional argument is backward-compatible — but the parser will be updated in Task 4 regardless).

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt
git commit -m "Add hasTasks to DeliveryAppProgressResponseModel [AI]"
```

---

## Task 4: Parse `assigned_tasks` in `DeliveryAppProgressResponseParser` (TDD)

**Files:**
- Modify: `app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt`
- Test: `app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt`

- [ ] **Step 1: Write the failing parser tests**

Create `app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt`:

```kotlin
package org.commcare.connect.network.connect.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DeliveryAppProgressResponseParserTest {

    private fun job(): ConnectJobRecord {
        val job = ConnectJobRecord()
        job.jobId = 1
        job.jobUUID = "job-uuid-1"
        return job
    }

    private fun parse(json: String, job: ConnectJobRecord): DeliveryAppProgressResponseModel {
        val parser = DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>()
        val stream = ByteArrayInputStream(json.toByteArray())
        return parser.parse(200, stream, job)
    }

    @Test
    fun parse_withAssignedTasks_populatesTasksAndSetsFlag() {
        val job = job()
        val json = """
            {
              "assigned_tasks": [
                {
                  "assigned_task_id": "t1",
                  "task_name": "Review module",
                  "status": "assigned",
                  "due_date": "2026-05-01T12:00:00.000"
                },
                {
                  "assigned_task_id": "t2",
                  "task_name": "Complete quiz",
                  "task_description": "Short quiz",
                  "status": "completed",
                  "due_date": "2026-05-02T12:00:00.000",
                  "date_modified": "2026-04-21T09:00:00.000"
                }
              ]
            }
        """.trimIndent()

        val model = parse(json, job)

        assertTrue(model.hasTasks)
        assertNotNull(job.tasks)
        assertEquals(2, job.tasks.size)
        assertEquals("t1", job.tasks[0].assignedTaskId)
        assertEquals(ConnectTaskRecord.STATUS_ASSIGNED, job.tasks[0].status)
        assertEquals("", job.tasks[0].taskDescription)
        assertNull(job.tasks[0].dateModified)
        assertEquals(ConnectTaskRecord.STATUS_COMPLETED, job.tasks[1].status)
        assertNotNull(job.tasks[1].dateModified)
    }

    @Test
    fun parse_withoutAssignedTasks_leavesTasksUntouchedAndFlagFalse() {
        val job = job()
        val json = "{}"

        val model = parse(json, job)

        assertFalse(model.hasTasks)
        assertNull(job.tasks)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParserTest"`

Expected: **FAIL** — `hasTasks` is not populated by the parser (either compiles-but-fails-assertions, or `job.tasks` is never set).

- [ ] **Step 3: Update the parser**

In `DeliveryAppProgressResponseParser.kt`:

1. Add the import at the top:
   ```kotlin
   import org.commcare.android.database.connect.models.ConnectTaskRecord
   ```

2. Add `var hasTasks = false` alongside the existing flags (replace the block at lines 25-27):
   ```kotlin
   var updatedJob = false
   var hasDeliveries = false
   var hasPayment = false
   var hasTasks = false
   ```

3. After the `payments` block (just before the outer `catch`), insert:
   ```kotlin
   val tasks: MutableList<ConnectTaskRecord> = ArrayList()

   if (json.has("assigned_tasks")) {
       hasTasks = true
       val array = json.getJSONArray("assigned_tasks")
       for (i in 0 until array.length()) {
           val obj = array[i] as JSONObject
           tasks.add(ConnectTaskRecord.fromJson(obj, job))
       }

       job.tasks = tasks
   }
   ```

4. Update the return statement at the bottom to pass the new flag:
   ```kotlin
   return DeliveryAppProgressResponseModel(updatedJob, hasDeliveries, hasPayment, hasTasks) as T
   ```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParserTest"`

Expected: **PASS** — 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt
git commit -m "Parse assigned_tasks into ConnectTaskRecord list [AI]"
```

---

## Task 5: Register `ConnectTaskRecord` table + DB migration (version 24 → 25)

**Files:**
- Modify: `app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java`
- Modify: `app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java`

- [ ] **Step 1: Register new table in `DatabaseConnectOpenHelper.onCreate`**

In `DatabaseConnectOpenHelper.java`:

1. Add the import near the other model imports:
   ```java
   import org.commcare.android.database.connect.models.ConnectTaskRecord;
   ```

2. In `onCreate(...)`, after the block that creates the `ConnectReleaseToggleRecord` table (ends around line 148, just before `DbUtil.createNumbersTable(database);`), add:
   ```java
           builder = new TableBuilder(ConnectTaskRecord.class);
           database.execSQL(builder.getTableCreateString());
   ```

3. Bump the version at line 70:
   ```java
   private static final int CONNECT_DB_VERSION = 25;
   ```

4. Add a line to the Javadoc version list above `CONNECT_DB_VERSION` (after the `V.24 - ...` line):
   ```
    * V.25 - Added ConnectTaskRecord table
   ```

- [ ] **Step 2: Add the migration to `ConnectDatabaseUpgrader`**

In `ConnectDatabaseUpgrader.java`:

1. Add the import near the other model imports:
   ```java
   import org.commcare.android.database.connect.models.ConnectTaskRecord;
   ```

2. Extend the `upgrade(...)` if-chain. Find the final block:
   ```java
   if (oldVersion == 23) {
       upgradeTwentyThreeTwentyFour(db);
   }
   ```
   Replace it with:
   ```java
   if (oldVersion == 23) {
       upgradeTwentyThreeTwentyFour(db);
       oldVersion = 24;
   }

   if (oldVersion == 24) {
       upgradeTwentyFourTwentyFive(db);
   }
   ```

3. Add the new migration method near the existing `upgradeTwentyTwentyOne(...)` / other new-table migrations (follows the same shape):
   ```java
   private void upgradeTwentyFourTwentyFive(IDatabase db) {
       addTableForNewModel(db, ConnectTaskRecord.STORAGE_KEY, new ConnectTaskRecord());
   }
   ```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileCommcareDebugJavaWithJavac`

Expected: **SUCCESS**.

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java
git commit -m "Add connect_tasks table + DB v25 migration [AI]"
```

---

## Task 6: Add `storeTasks` / `getTasks` helpers in `ConnectJobUtils` and load tasks in `populateJobs`

**Files:**
- Modify: `app/src/org/commcare/connect/database/ConnectJobUtils.java`

- [ ] **Step 1: Add the import**

At the top of `ConnectJobUtils.java`, with the other `org.commcare.android.database.connect.models` imports, add:

```java
import org.commcare.android.database.connect.models.ConnectTaskRecord;
```

- [ ] **Step 2: Add a storage lookup + load call in `populateJobs`**

Inside `populateJobs(...)`, next to the other `SqlStorage` locals at the top of the method (alongside `deliveryStorage`, etc.), add:

```java
SqlStorage<ConnectTaskRecord> taskStorage = ConnectDatabaseHelper.getConnectStorage(
        context,
        ConnectTaskRecord.class
);
```

At the bottom of the per-job loop — the block that currently ends with:
```java
//Retrieve related data
job.setDeliveries(getDeliveries(context, job.getJobUUID(), deliveryStorage));
job.setPayments(getPayments(context, job.getJobUUID(), paymentStorage));
job.setLearnings(getLearnings(context, job.getJobUUID(), learningStorage));
job.setAssessments(getAssessments(context, job.getJobUUID(), assessmentStorage));
```

— add a trailing line:
```java
job.setTasks(getTasks(context, job.getJobUUID(), taskStorage));
```

- [ ] **Step 3: Add `getTasks` helper**

Add this method (place it near `getDeliveries`, around line 323 in the original file):

```java
public static List<ConnectTaskRecord> getTasks(
        Context context,
        String jobUUID,
        SqlStorage<ConnectTaskRecord> taskStorage
) {
    if (taskStorage == null) {
        taskStorage = ConnectDatabaseHelper.getConnectStorage(
                context,
                ConnectTaskRecord.class
        );
    }

    Vector<ConnectTaskRecord> tasks = taskStorage.getRecordsForValues(
            new String[]{ConnectTaskRecord.META_JOB_UUID},
            new Object[]{jobUUID}
    );

    return new ArrayList<>(tasks);
}
```

- [ ] **Step 4: Add `storeTasks` helper**

Add this method (place it near `storeDeliveries`, around line 182):

```java
public static void storeTasks(
        Context context,
        List<ConnectTaskRecord> tasks,
        String jobUUID,
        boolean pruneMissing
) {
    SqlStorage<ConnectTaskRecord> storage = ConnectDatabaseHelper.getConnectStorage(
            context,
            ConnectTaskRecord.class
    );

    List<ConnectTaskRecord> existingTasks = getTasks(context, jobUUID, storage);

    Vector<Integer> recordIdsToDelete = new Vector<>();
    for (ConnectTaskRecord existing : existingTasks) {
        boolean stillExists = false;
        for (ConnectTaskRecord incoming : tasks) {
            if (existing.getAssignedTaskId().equals(incoming.getAssignedTaskId())) {
                incoming.setID(existing.getID());
                stillExists = true;
                break;
            }
        }

        if (!stillExists && pruneMissing) {
            recordIdsToDelete.add(existing.getID());
        }
    }

    if (pruneMissing) {
        storage.removeAll(recordIdsToDelete);
    }

    for (ConnectTaskRecord incomingRecord : tasks) {
        incomingRecord.setLastUpdate(new Date());
        storage.write(incomingRecord);
    }
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileCommcareDebugJavaWithJavac`

Expected: **SUCCESS**. (Kotlin-defined `getAssignedTaskId()` / `setLastUpdate(Date)` / `setID(int)` are all Java-accessible via Kotlin's default getter/setter generation and the inherited `Persisted.setID`.)

- [ ] **Step 6: Commit**

```bash
git add app/src/org/commcare/connect/database/ConnectJobUtils.java
git commit -m "Add storeTasks/getTasks and load tasks in populateJobs [AI]"
```

---

## Task 7: Implement `ConnectJobRecord.isRelearnTaskPending()` (TDD, remove TODO)

**Files:**
- Modify: `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java`
- Test: `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`:

```kotlin
package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.javarosa.core.model.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobRecordRelearnTasksTest {

    private fun task(id: String, status: String, dateModified: Date? = null): ConnectTaskRecord {
        val t = ConnectTaskRecord()
        t.assignedTaskId = id
        t.status = status
        t.dueDate = DateUtils.parseDateTime("2026-05-01T12:00:00.000")
        t.dateModified = dateModified
        return t
    }

    @Test
    fun isRelearnTaskPending_nullTasks_returnsFalse() {
        val job = ConnectJobRecord()
        job.tasks = null
        assertFalse(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_emptyTasks_returnsFalse() {
        val job = ConnectJobRecord()
        job.tasks = emptyList()
        assertFalse(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_allCompleted_returnsFalse() {
        val job = ConnectJobRecord()
        job.tasks = listOf(
            task("a", ConnectTaskRecord.STATUS_COMPLETED),
            task("b", ConnectTaskRecord.STATUS_COMPLETED),
        )
        assertFalse(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_atLeastOneAssigned_returnsTrue() {
        val job = ConnectJobRecord()
        job.tasks = listOf(
            task("a", ConnectTaskRecord.STATUS_COMPLETED),
            task("b", ConnectTaskRecord.STATUS_ASSIGNED),
        )
        assertTrue(job.isRelearnTaskPending)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest"`

Expected: **FAIL** — `isRelearnTaskPending_atLeastOneAssigned_returnsTrue` fails because the stub always returns `false`.

- [ ] **Step 3: Replace the stub implementation**

In `ConnectJobRecord.java`, replace the existing method (lines 862-871):

```java
    /**
     * This is a temporary dummy method implementation to show new UI that blocks delivery progress
     * when there is a pending relearn task for a delivery app.
     *
     * @return false until the real method is implemented.
     */
    public boolean isRelearnTaskPending() {
        // TODO: Not yet implemented
        return false;
    }
```

With:

```java
    public boolean isRelearnTaskPending() {
        if (tasks == null) {
            return false;
        }
        for (ConnectTaskRecord t : tasks) {
            if (ConnectTaskRecord.STATUS_ASSIGNED.equals(t.getStatus())) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest"`

Expected: **PASS** — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt
git commit -m "Implement ConnectJobRecord.isRelearnTaskPending from parsed tasks [AI]"
```

---

## Task 8: Add `ConnectJobRecord.syncRelearnTasksCompletedTime` (TDD, remove read-method TODO)

**Files:**
- Modify: `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java`
- Test: `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt` (extend)

- [ ] **Step 1: Append the failing tests**

Append these test methods to `ConnectJobRecordRelearnTasksTest.kt`. Add the imports at the top if missing:

```kotlin
import org.commcare.connect.ConnectConstants.RELEARN_TASKS_COMPLETED_TIME
import org.commcare.core.services.CommCarePreferenceManagerFactory
import org.commcare.core.services.ICommCarePreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.slot
import org.junit.After
import org.junit.Before
```

Add setup/teardown + tests:

```kotlin
    private lateinit var prefs: ICommCarePreferenceManager

    @Before
    fun setUpPrefs() {
        mockkStatic(CommCarePreferenceManagerFactory::class)
        prefs = mockk(relaxed = true)
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns prefs
    }

    @After
    fun tearDownPrefs() {
        unmockkStatic(CommCarePreferenceManagerFactory::class)
    }

    @Test
    fun syncRelearn_nullOrEmptyTasks_doesNotTouchPref() {
        ConnectJobRecord.syncRelearnTasksCompletedTime(null)
        ConnectJobRecord.syncRelearnTasksCompletedTime(emptyList())

        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun syncRelearn_anyAssignedTask_resetsPrefToMinusOne() {
        ConnectJobRecord.syncRelearnTasksCompletedTime(
            listOf(
                task("a", ConnectTaskRecord.STATUS_COMPLETED),
                task("b", ConnectTaskRecord.STATUS_ASSIGNED),
            )
        )

        verify { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, -1) }
    }

    @Test
    fun syncRelearn_allCompletedAndPrefUnset_writesLatestDateModified() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns -1L
        val earlier = DateUtils.parseDateTime("2026-04-20T09:00:00.000")
        val later = DateUtils.parseDateTime("2026-04-21T11:00:00.000")

        ConnectJobRecord.syncRelearnTasksCompletedTime(
            listOf(
                task("a", ConnectTaskRecord.STATUS_COMPLETED, dateModified = earlier),
                task("b", ConnectTaskRecord.STATUS_COMPLETED, dateModified = later),
            )
        )

        verify { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, later.time) }
    }

    @Test
    fun syncRelearn_allCompletedAndPrefUnsetAndNoDateModified_writesCurrentTime() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns -1L
        val slot = slot<Long>()
        every { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, capture(slot)) } returns Unit

        val before = System.currentTimeMillis()
        ConnectJobRecord.syncRelearnTasksCompletedTime(
            listOf(task("a", ConnectTaskRecord.STATUS_COMPLETED))
        )
        val after = System.currentTimeMillis()

        assertTrue(slot.captured in before..after)
    }

    @Test
    fun syncRelearn_allCompletedAndPrefAlreadySet_leavesPrefAlone() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns 123456789L

        ConnectJobRecord.syncRelearnTasksCompletedTime(
            listOf(task("a", ConnectTaskRecord.STATUS_COMPLETED))
        )

        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest"`

Expected: **FAIL** — `syncRelearnTasksCompletedTime` does not yet exist (compile error).

- [ ] **Step 3: Implement `syncRelearnTasksCompletedTime` and clean up the TODO on `shouldShowRelearnTasksCompletedMessage`**

In `ConnectJobRecord.java`, locate `shouldShowRelearnTasksCompletedMessage()` (lines 873-886) and replace with the cleaned-up version (remove the multi-line `// TODO:` block — the method body stays identical):

```java
    public boolean shouldShowRelearnTasksCompletedMessage() {
        ICommCarePreferenceManager preferenceManager = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        assert preferenceManager != null;
        long relearnTasksCompletedTimeMs = preferenceManager.getLong(RELEARN_TASKS_COMPLETED_TIME, -1);
        long timeElapsedSinceTasksCompleted = new Date().getTime() - relearnTasksCompletedTimeMs;

        return relearnTasksCompletedTimeMs != -1 && timeElapsedSinceTasksCompleted < DateUtils.HOUR_IN_MS * 6;
    }
```

Then add this new static method directly below:

```java
    /**
     * Updates the RELEARN_TASKS_COMPLETED_TIME shared preference based on the current task list:
     *  - null/empty list: no signal, pref untouched
     *  - any task with status == assigned: pref reset to -1
     *  - all tasks completed AND pref currently -1: pref set to latest date_modified across
     *    records, falling back to current device time when no record has a date_modified
     *  - all tasks completed AND pref already set: pref untouched (preserves the 6-hour banner
     *    window that's already in flight)
     */
    public static void syncRelearnTasksCompletedTime(List<ConnectTaskRecord> tasks) {
        ICommCarePreferenceManager prefs = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        if (prefs == null) {
            return;
        }
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

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
            return;
        }

        long current = prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1);
        if (current == -1) {
            long ts = latestModified != null ? latestModified.getTime() : new Date().getTime();
            prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, ts);
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest"`

Expected: **PASS** — 9 total tests in this class (4 from Task 7 + 5 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt
git commit -m "Add syncRelearnTasksCompletedTime to ConnectJobRecord [AI]"
```

---

## Task 9: Wire `storeTasks` + `syncRelearnTasksCompletedTime` into `ConnectJobHelper.updateDeliveryProgress`

**Files:**
- Modify: `app/src/org/commcare/connect/ConnectJobHelper.kt`

- [ ] **Step 1: Add the new onSuccess block**

In `ConnectJobHelper.kt`, inside `updateDeliveryProgress(...)` → the anonymous `object : ConnectApiHandler<...>` → `onSuccess(...)` method, immediately after the existing `if (deliveryAppProgressResponseModel.hasPayment) { ... }` block (around lines 115-120) and before `events.forEach { ... }`, add:

```kotlin
if (deliveryAppProgressResponseModel.hasTasks) {
    ConnectJobUtils.storeTasks(context, job.tasks, job.jobUUID, true)
    ConnectJobRecord.syncRelearnTasksCompletedTime(job.tasks)
}
```

No new imports are required — `ConnectJobRecord` and `ConnectJobUtils` are already in scope in this file.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileCommcareDebugKotlin :app:compileCommcareDebugJavaWithJavac`

Expected: **SUCCESS**.

- [ ] **Step 3: Run the full test class suite to confirm no regressions**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectTaskRecordTest" --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest" --tests "org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParserTest"`

Expected: **PASS** — all task-related tests green (3 + 9 + 2 = 14 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/connect/ConnectJobHelper.kt
git commit -m "Wire storeTasks and syncRelearnTasksCompletedTime into delivery progress [AI]"
```

---

## Task 10: Post-implementation cleanup

**Files:**
- Modify files touched above (unused imports, ktlint)

- [ ] **Step 1: Format the Kotlin files**

```bash
ktlint --format "app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt" \
               "app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt" \
               "app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt" \
               "app/src/org/commcare/connect/ConnectJobHelper.kt" \
               "app/unit-tests/src/org/commcare/android/database/connect/models/ConnectTaskRecordTest.kt" \
               "app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt" \
               "app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt"
```

Verify:

```bash
ktlint "app/src/org/commcare/android/database/connect/models/ConnectTaskRecord.kt" \
       "app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt" \
       "app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt" \
       "app/src/org/commcare/connect/ConnectJobHelper.kt" \
       "app/unit-tests/src/org/commcare/android/database/connect/models/ConnectTaskRecordTest.kt" \
       "app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt" \
       "app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt"
```

Expected: no ktlint violations printed.

- [ ] **Step 2: Check for unused imports in modified Java files**

Open each modified Java file and remove any unused imports:
- `ConnectJobRecord.java`
- `ConnectJobUtils.java`
- `DatabaseConnectOpenHelper.java`
- `ConnectDatabaseUpgrader.java`

- [ ] **Step 3: Run checkstyle on modified Java files**

Run (adapt to how checkstyle is invoked in this project — if unsure, inspect `checkstyle.xml` usage in `build.gradle`):

```bash
./gradlew :app:checkstyleMain 2>&1 | tail -40
```

Fix any violations introduced by the modified files. Pre-existing violations in untouched files are out of scope.

- [ ] **Step 4: Run all relevant tests one more time**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests "*ConnectTaskRecord*" --tests "*ConnectJobRecordRelearnTasks*" --tests "*DeliveryAppProgressResponseParser*"
```

Expected: **PASS**.

- [ ] **Step 5: Commit cleanup**

```bash
git add -A
git commit -m "Cleanup: ktlint, unused imports, checkstyle [AI]"
```

(If no changes are staged, skip the commit — no empty commits.)

---

## Done

At this point:
- `assigned_tasks` from the `delivery_progress` API flows into a new `connect_tasks` DB table via `DeliveryAppProgressResponseParser` → `ConnectJobUtils.storeTasks`.
- `ConnectJobRecord.isRelearnTaskPending()` returns `true` iff at least one parsed task has `status == "assigned"` — enabling the existing relearn-pending UI in `StandardHomeActivityUIController` and the card-message text in `getCardMessageText`.
- `ConnectJobRecord.syncRelearnTasksCompletedTime(...)` updates the `RELEARN_TASKS_COMPLETED_TIME` shared pref when all tasks flip to completed, enabling `shouldShowRelearnTasksCompletedMessage()` (and its 6-hour banner) to reflect real data.
- Connect DB migrates cleanly from v24 to v25.
