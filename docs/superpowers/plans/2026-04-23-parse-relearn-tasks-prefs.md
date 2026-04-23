# Parse Relearn Tasks (shared-prefs revision) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Supersedes `2026-04-22-parse-relearn-tasks.md`. The prior plan persisted
> each parsed task to a new `connect_tasks` DB table (Connect DB v24 → v25
> migration). This revision avoids database changes: per-job "pending"
> state is stored as a long `0`/`1` shared-pref keyed by jobUUID, and the
> already-existing `RELEARN_TASKS_COMPLETED_TIME` pref keeps driving the
> 6-hour banner.

**Goal:** Parse a new `assigned_tasks` array from the `delivery_progress` API response and use it to drive `ConnectJobRecord.isRelearnTaskPending()` and `shouldShowRelearnTasksCompletedMessage()` via shared preferences — no new DB table, no migration.

**Architecture:** Parser produces a transient `List<ParsedConnectTask>` on the response model. `ConnectJobHelper` hands that list to a new static `ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, tasks)` which writes two long prefs: a per-job `RELEARN_TASK_PENDING_PREFIX + jobUUID` (`0`/`1` sentinel) and the existing global `RELEARN_TASKS_COMPLETED_TIME` (epoch-ms or `-1`). `isRelearnTaskPending()` reads the per-job pref. The full per-task fields (name, description, due_date, date_created, assigned_task_id) are read during parsing and discarded.

**Tech Stack:** Java + Kotlin, `org.json`, `ICommCarePreferenceManager` (long-only API) from commcare-core, Robolectric + MockK for unit tests, Gradle.

---

## Spec reference

`docs/superpowers/specs/2026-04-23-parse-relearn-tasks-prefs-design.md`

## File map

**Created:**
- `app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt` — plain Kotlin `data class` + `ConnectTaskStatus` object; not persisted
- `app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt`
- `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`

**Modified:**
- `app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt` — add `hasTasks`, `parsedTasks`
- `app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt` — parse `assigned_tasks`
- `app/src/org/commcare/connect/ConnectConstants.java` — add `RELEARN_TASK_PENDING_PREFIX`
- `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java` — new `isRelearnTaskPending()` impl reading the pref; new static `syncRelearnTasksPrefs(...)`; TODO cleanup
- `app/src/org/commcare/connect/ConnectJobHelper.kt` — call `syncRelearnTasksPrefs` on `hasTasks`

**Not touched (explicit departure from the prior plan):**
- `DatabaseConnectOpenHelper.java`, `ConnectDatabaseUpgrader.java`, `ConnectJobUtils.java` — no new table, no migration, no `storeTasks`/`getTasks`, no `populateJobs` changes. No transient `tasks` field on `ConnectJobRecord`.

---

## Task 1: Create `ParsedConnectTask` data class + `ConnectTaskStatus` object

**Files:**
- Create: `app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt`

Non-TDD: this is a pure data class with no logic. Parser tests in Task 3 exercise it.

- [ ] **Step 1: Create the file**

Create `app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt`:

```kotlin
package org.commcare.connect.network.connect.models

import java.util.Date

/**
 * Transient task record built by DeliveryAppProgressResponseParser. Not
 * persisted — used only to hand task status + dateModified to
 * ConnectJobRecord.syncRelearnTasksPrefs.
 */
data class ParsedConnectTask(
    val status: String,
    val dateModified: Date?,
)

object ConnectTaskStatus {
    const val ASSIGNED = "assigned"
    const val COMPLETED = "completed"
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCommcareDebugKotlin`

Expected: **SUCCESS**.

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt
git commit -m "$(cat <<'EOF'
CCCT-2294 Add ParsedConnectTask transient data class

[AI]
EOF
)"
```

---

## Task 2: Extend `DeliveryAppProgressResponseModel` with `hasTasks` + `parsedTasks`

**Files:**
- Modify: `app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package org.commcare.connect.network.connect.models

data class DeliveryAppProgressResponseModel(
    var updatedJob: Boolean = false,
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var hasTasks: Boolean = false,
    var parsedTasks: List<ParsedConnectTask> = emptyList(),
)
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCommcareDebugKotlin`

Expected: **SUCCESS**. Existing call sites in the parser still compile: the new fields have defaults, and the parser will be updated in Task 3 to pass them explicitly.

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt
git commit -m "$(cat <<'EOF'
CCCT-2294 Add hasTasks and parsedTasks to DeliveryAppProgressResponseModel

[AI]
EOF
)"
```

---

## Task 3: Parse `assigned_tasks` in `DeliveryAppProgressResponseParser` (TDD)

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
import org.commcare.connect.network.connect.models.ConnectTaskStatus
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.javarosa.core.model.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val j = ConnectJobRecord()
        j.jobId = 1
        j.jobUUID = "job-uuid-1"
        return j
    }

    private fun parse(json: String, job: ConnectJobRecord): DeliveryAppProgressResponseModel {
        val parser = DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>()
        val stream = ByteArrayInputStream(json.toByteArray())
        return parser.parse(200, stream, job)
    }

    @Test
    fun parse_withAssignedTasks_populatesParsedTasksAndSetsFlag() {
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

        val model = parse(json, job())

        assertTrue(model.hasTasks)
        assertEquals(2, model.parsedTasks.size)
        assertEquals(ConnectTaskStatus.ASSIGNED, model.parsedTasks[0].status)
        assertNull(model.parsedTasks[0].dateModified)
        assertEquals(ConnectTaskStatus.COMPLETED, model.parsedTasks[1].status)
        assertEquals(
            DateUtils.parseDateTime("2026-04-21T09:00:00.000"),
            model.parsedTasks[1].dateModified,
        )
    }

    @Test
    fun parse_withoutAssignedTasks_leavesFlagFalseAndParsedTasksEmpty() {
        val model = parse("{}", job())

        assertFalse(model.hasTasks)
        assertTrue(model.parsedTasks.isEmpty())
    }

    @Test
    fun parse_withEmptyAssignedTasksArray_setsFlagTrueAndListEmpty() {
        val model = parse("""{ "assigned_tasks": [] }""", job())

        assertTrue(model.hasTasks)
        assertTrue(model.parsedTasks.isEmpty())
    }

    @Test
    fun parse_withDateModifiedExplicitlyNull_producesNullDate() {
        val json = """
            {
              "assigned_tasks": [
                {
                  "assigned_task_id": "t1",
                  "task_name": "X",
                  "status": "assigned",
                  "due_date": "2026-05-01T12:00:00.000",
                  "date_modified": null
                }
              ]
            }
        """.trimIndent()

        val model = parse(json, job())

        assertNull(model.parsedTasks[0].dateModified)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParserTest"`

Expected: **FAIL** — either compile error on `parsedTasks` (old return call passes only 3 args but default makes this compile; in that case) or assertion failures because the parser never populates `parsedTasks` / `hasTasks`.

- [ ] **Step 3: Update the parser**

Replace the contents of `app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt`:

```kotlin
package org.commcare.connect.network.connect.parser

import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.commcare.connect.network.connect.models.ParsedConnectTask
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.util.Date

class DeliveryAppProgressResponseParser<T> : BaseApiResponseParser<T> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        val job = anyInputObject as ConnectJobRecord

        var updatedJob = false
        var hasDeliveries = false
        var hasPayment = false
        var hasTasks = false
        val parsedTasks: MutableList<ParsedConnectTask> = mutableListOf()

        responseData.use { `in` ->

            try {
                val responseAsString = String(StreamsUtil.inputStreamToByteArray(`in`))
                if (responseAsString.length > 0) {
                    val json = JSONObject(responseAsString)

                    if (json.has("max_payments")) {
                        job.maxVisits = json.getInt("max_payments")
                        updatedJob = true
                    }

                    if (json.has("end_date")) {
                        job.projectEndDate = DateUtils.parseDate(json.getString("end_date"))
                        updatedJob = true
                    }

                    if (json.has("payment_accrued")) {
                        job.paymentAccrued = json.getInt("payment_accrued")
                        updatedJob = true
                    }

                    if (json.has("is_user_suspended")) {
                        job.isUserSuspended = json.getBoolean("is_user_suspended")
                        updatedJob = true
                    }

                    if (updatedJob) {
                        job.lastDeliveryUpdate = Date()
                    }

                    val deliveries: MutableList<ConnectJobDeliveryRecord> =
                        ArrayList(json.length())

                    if (json.has("deliveries")) {
                        hasDeliveries = true
                        val array = json.getJSONArray("deliveries")
                        for (i in 0 until array.length()) {
                            val obj = array[i] as JSONObject
                            deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job))
                        }

                        job.deliveries = deliveries
                    }

                    val payments: MutableList<ConnectJobPaymentRecord> = ArrayList()

                    if (json.has("payments")) {
                        hasPayment = true
                        val array = json.getJSONArray("payments")
                        for (i in 0 until array.length()) {
                            val obj = array[i] as JSONObject
                            payments.add(ConnectJobPaymentRecord.fromJson(obj, job))
                        }

                        job.payments = payments
                    }

                    if (json.has("assigned_tasks")) {
                        hasTasks = true
                        val array = json.getJSONArray("assigned_tasks")
                        for (i in 0 until array.length()) {
                            val obj = array[i] as JSONObject
                            parsedTasks.add(parseTask(obj))
                        }
                    }
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        return DeliveryAppProgressResponseModel(
            updatedJob,
            hasDeliveries,
            hasPayment,
            hasTasks,
            parsedTasks,
        ) as T
    }

    private fun parseTask(obj: JSONObject): ParsedConnectTask {
        val status = obj.getString("status")
        val dateModified: Date? =
            if (obj.has("date_modified") && !obj.isNull("date_modified")) {
                val raw = obj.getString("date_modified")
                if (raw.isEmpty()) null else DateUtils.parseDateTime(raw)
            } else {
                null
            }
        return ParsedConnectTask(status, dateModified)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParserTest"`

Expected: **PASS** — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt
git commit -m "$(cat <<'EOF'
CCCT-2294 Parse assigned_tasks into ParsedConnectTask list

[AI]
EOF
)"
```

---

## Task 4: Add `RELEARN_TASK_PENDING_PREFIX` to `ConnectConstants`

**Files:**
- Modify: `app/src/org/commcare/connect/ConnectConstants.java`

- [ ] **Step 1: Add the constant**

In `ConnectConstants.java`, directly under the existing `RELEARN_TASKS_COMPLETED_TIME` line (line 71), add:

```java
    public final static String RELEARN_TASK_PENDING_PREFIX = "relearn_task_pending_";
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCommcareDebugJavaWithJavac`

Expected: **SUCCESS**.

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/connect/ConnectConstants.java
git commit -m "$(cat <<'EOF'
CCCT-2294 Add RELEARN_TASK_PENDING_PREFIX pref key

[AI]
EOF
)"
```

---

## Task 5: Implement `isRelearnTaskPending()` + `syncRelearnTasksPrefs(...)` on `ConnectJobRecord` (TDD)

**Files:**
- Modify: `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java`
- Test: `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt`:

```kotlin
package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectConstants.RELEARN_TASKS_COMPLETED_TIME
import org.commcare.connect.ConnectConstants.RELEARN_TASK_PENDING_PREFIX
import org.commcare.connect.network.connect.models.ConnectTaskStatus
import org.commcare.connect.network.connect.models.ParsedConnectTask
import org.commcare.core.services.CommCarePreferenceManagerFactory
import org.commcare.core.services.ICommCarePreferenceManager
import org.javarosa.core.model.utils.DateUtils
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobRecordRelearnTasksTest {

    private val jobUUID = "job-uuid-1"
    private val pendingKey = RELEARN_TASK_PENDING_PREFIX + jobUUID

    private lateinit var prefs: ICommCarePreferenceManager

    @Before
    fun setUp() {
        mockkStatic(CommCarePreferenceManagerFactory::class)
        prefs = mockk(relaxed = true)
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns prefs
    }

    @After
    fun tearDown() {
        unmockkStatic(CommCarePreferenceManagerFactory::class)
    }

    private fun task(status: String, dateModified: Date? = null) =
        ParsedConnectTask(status, dateModified)

    // --- syncRelearnTasksPrefs ---

    @Test
    fun sync_nullJobUUID_noop() {
        ConnectJobRecord.syncRelearnTasksPrefs(null, listOf(task(ConnectTaskStatus.ASSIGNED)))

        verify(exactly = 0) { prefs.putLong(any(), any()) }
    }

    @Test
    fun sync_nullPrefsManager_noop() {
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns null

        // Just asserting no exception — prefs is unreachable when the factory returns null.
        ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, listOf(task(ConnectTaskStatus.ASSIGNED)))
    }

    @Test
    fun sync_emptyList_clearsPendingAndLeavesCompletedTimeAlone() {
        ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, emptyList())

        verify { prefs.putLong(pendingKey, 0) }
        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun sync_nullList_clearsPendingAndLeavesCompletedTimeAlone() {
        ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, null)

        verify { prefs.putLong(pendingKey, 0) }
        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun sync_anyAssigned_setsPendingOneAndResetsCompletedTimeToMinusOne() {
        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(
                task(ConnectTaskStatus.COMPLETED),
                task(ConnectTaskStatus.ASSIGNED),
            ),
        )

        verify { prefs.putLong(pendingKey, 1) }
        verify { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, -1) }
    }

    @Test
    fun sync_allCompletedAndCompletedTimeUnset_writesLatestDateModified() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns -1L
        val earlier = DateUtils.parseDateTime("2026-04-20T09:00:00.000")
        val later = DateUtils.parseDateTime("2026-04-21T11:00:00.000")

        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(
                task(ConnectTaskStatus.COMPLETED, earlier),
                task(ConnectTaskStatus.COMPLETED, later),
            ),
        )

        verify { prefs.putLong(pendingKey, 0) }
        verify { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, later.time) }
    }

    @Test
    fun sync_allCompletedAndCompletedTimeUnsetNoDateModified_writesCurrentTime() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns -1L
        val captured = slot<Long>()
        every { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, capture(captured)) } returns Unit

        val before = System.currentTimeMillis()
        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(task(ConnectTaskStatus.COMPLETED)),
        )
        val after = System.currentTimeMillis()

        verify { prefs.putLong(pendingKey, 0) }
        assertTrue(captured.captured in before..after)
    }

    @Test
    fun sync_allCompletedAndCompletedTimeAlreadySet_leavesCompletedTimeAlone() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns 123456789L

        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(task(ConnectTaskStatus.COMPLETED)),
        )

        verify { prefs.putLong(pendingKey, 0) }
        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun sync_usesJobUUIDAsKeySuffix() {
        val otherJobUUID = "job-uuid-2"
        val otherPendingKey = RELEARN_TASK_PENDING_PREFIX + otherJobUUID

        ConnectJobRecord.syncRelearnTasksPrefs(
            otherJobUUID,
            listOf(task(ConnectTaskStatus.ASSIGNED)),
        )

        verify { prefs.putLong(otherPendingKey, 1) }
        verify(exactly = 0) { prefs.putLong(pendingKey, any()) }
    }

    // --- isRelearnTaskPending ---

    @Test
    fun isRelearnTaskPending_prefOne_returnsTrue() {
        val job = ConnectJobRecord()
        job.jobUUID = jobUUID
        every { prefs.getLong(pendingKey, 0) } returns 1L

        assertTrue(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_prefZero_returnsFalse() {
        val job = ConnectJobRecord()
        job.jobUUID = jobUUID
        every { prefs.getLong(pendingKey, 0) } returns 0L

        assertFalse(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_nullJobUUID_returnsFalse() {
        val job = ConnectJobRecord()
        job.jobUUID = null

        assertFalse(job.isRelearnTaskPending)
        verify(exactly = 0) { prefs.getLong(any(), any()) }
    }

    @Test
    fun isRelearnTaskPending_nullPrefsManager_returnsFalse() {
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns null
        val job = ConnectJobRecord()
        job.jobUUID = jobUUID

        assertFalse(job.isRelearnTaskPending)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest"`

Expected: **FAIL** — compile error: `syncRelearnTasksPrefs` does not exist on `ConnectJobRecord`; `isRelearnTaskPending` still returns the stub `false`.

- [ ] **Step 3: Update `ConnectJobRecord.java`**

Add the new import near the existing static import:

```java
import static org.commcare.connect.ConnectConstants.RELEARN_TASK_PENDING_PREFIX;
```

And add these imports with the other model imports:

```java
import org.commcare.connect.network.connect.models.ConnectTaskStatus;
import org.commcare.connect.network.connect.models.ParsedConnectTask;
```

Locate the existing `isRelearnTaskPending()` method (around lines 862-871) and replace the whole method (including the old Javadoc) with:

```java
    public boolean isRelearnTaskPending() {
        ICommCarePreferenceManager prefs = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        if (prefs == null || jobUUID == null) {
            return false;
        }
        return prefs.getLong(RELEARN_TASK_PENDING_PREFIX + jobUUID, 0) == 1;
    }
```

Locate `shouldShowRelearnTasksCompletedMessage()` (around lines 873-886) and replace it with the cleaned-up version (remove the stale multi-line TODO block — method body is unchanged):

```java
    public boolean shouldShowRelearnTasksCompletedMessage() {
        ICommCarePreferenceManager preferenceManager = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        assert preferenceManager != null;
        long relearnTasksCompletedTimeMs = preferenceManager.getLong(RELEARN_TASKS_COMPLETED_TIME, -1);
        long timeElapsedSinceTasksCompleted = new Date().getTime() - relearnTasksCompletedTimeMs;

        return relearnTasksCompletedTimeMs != -1 && timeElapsedSinceTasksCompleted < DateUtils.HOUR_IN_MS * 6;
    }
```

Directly after that method, add the new static sync method:

```java
    /**
     * Writes the two relearn-task shared prefs from a freshly-parsed task list:
     *  - RELEARN_TASK_PENDING_PREFIX + jobUUID: 1 when any task is assigned, 0 otherwise
     *  - RELEARN_TASKS_COMPLETED_TIME: reset to -1 when any task is assigned; set to the
     *    latest dateModified (or current time) on the all-completed transition; left
     *    alone when already set.
     * No-op when prefs or jobUUID are null.
     */
    public static void syncRelearnTasksPrefs(String jobUUID, List<ParsedConnectTask> tasks) {
        ICommCarePreferenceManager prefs = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        if (prefs == null || jobUUID == null) {
            return;
        }

        String pendingKey = RELEARN_TASK_PENDING_PREFIX + jobUUID;

        if (tasks == null || tasks.isEmpty()) {
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

Expected: **PASS** — 13 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt
git commit -m "$(cat <<'EOF'
CCCT-2294 Drive relearn-task prefs from parsed tasks

isRelearnTaskPending() now reads a per-job long pref
(RELEARN_TASK_PENDING_PREFIX + jobUUID). New static
syncRelearnTasksPrefs(jobUUID, tasks) writes that pref plus keeps the
existing RELEARN_TASKS_COMPLETED_TIME banner logic.

[AI]
EOF
)"
```

---

## Task 6: Wire `syncRelearnTasksPrefs` into `ConnectJobHelper.updateDeliveryProgress`

**Files:**
- Modify: `app/src/org/commcare/connect/ConnectJobHelper.kt`

- [ ] **Step 1: Add the new onSuccess block**

In `ConnectJobHelper.kt`, inside `updateDeliveryProgress(...)` → the anonymous `object : ConnectApiHandler<DeliveryAppProgressResponseModel>` → `onSuccess(...)`, immediately after the existing `if (deliveryAppProgressResponseModel.hasPayment) { ... }` block (around lines 115-120) and before `events.forEach { ... }`, add:

```kotlin
if (deliveryAppProgressResponseModel.hasTasks) {
    ConnectJobRecord.syncRelearnTasksPrefs(
        job.jobUUID,
        deliveryAppProgressResponseModel.parsedTasks,
    )
}
```

`ConnectJobRecord` is already in scope in this file (imported via its usage elsewhere).

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileCommcareDebugKotlin :app:compileCommcareDebugJavaWithJavac`

Expected: **SUCCESS**.

- [ ] **Step 3: Run the full relevant test suite to confirm no regressions**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.android.database.connect.models.ConnectJobRecordRelearnTasksTest" --tests "org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParserTest"`

Expected: **PASS** — 17 tests total (13 + 4).

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/connect/ConnectJobHelper.kt
git commit -m "$(cat <<'EOF'
CCCT-2294 Sync relearn-task prefs from delivery progress response

[AI]
EOF
)"
```

---

## Task 7: Post-implementation cleanup

**Files:**
- Files touched in Tasks 1-6

- [ ] **Step 1: Format the Kotlin files**

```bash
ktlint --format "app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt" \
               "app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt" \
               "app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt" \
               "app/src/org/commcare/connect/ConnectJobHelper.kt" \
               "app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt" \
               "app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt"
```

Verify:

```bash
ktlint "app/src/org/commcare/connect/network/connect/models/ParsedConnectTask.kt" \
       "app/src/org/commcare/connect/network/connect/models/DeliveryAppProgressResponseModel.kt" \
       "app/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParser.kt" \
       "app/src/org/commcare/connect/ConnectJobHelper.kt" \
       "app/unit-tests/src/org/commcare/connect/network/connect/parser/DeliveryAppProgressResponseParserTest.kt" \
       "app/unit-tests/src/org/commcare/android/database/connect/models/ConnectJobRecordRelearnTasksTest.kt"
```

Expected: no ktlint violations.

- [ ] **Step 2: Scan modified Java files for unused imports**

Open each and remove any unused imports:
- `app/src/org/commcare/connect/ConnectConstants.java`
- `app/src/org/commcare/android/database/connect/models/ConnectJobRecord.java`

- [ ] **Step 3: Run checkstyle on the module**

```bash
./gradlew :app:checkstyleMain 2>&1 | tail -40
```

Fix any violations introduced by the modified files. Pre-existing violations in untouched files are out of scope.

- [ ] **Step 4: Run all relevant tests one more time**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests "*ConnectJobRecordRelearnTasks*" --tests "*DeliveryAppProgressResponseParser*"
```

Expected: **PASS** — 17 tests.

- [ ] **Step 5: Commit cleanup**

```bash
git add -A
git commit -m "$(cat <<'EOF'
CCCT-2294 Cleanup: ktlint, unused imports, checkstyle

[AI]
EOF
)"
```

(If nothing is staged, skip — no empty commits.)

---

## Done

At this point:
- `assigned_tasks` from the `delivery_progress` API flows into an in-memory `List<ParsedConnectTask>` on `DeliveryAppProgressResponseModel`, used only long enough to sync two prefs and then discarded.
- `ConnectJobRecord.isRelearnTaskPending()` reads the per-job pref `RELEARN_TASK_PENDING_PREFIX + jobUUID` — driving the existing relearn-pending UI in `StandardHomeActivityUIController`, `ConnectViewUtils`, `ConnectDeliveryProgressFragment`, and `getCardMessageText`.
- `ConnectJobRecord.syncRelearnTasksPrefs(...)` writes both that per-job pref and the existing global `RELEARN_TASKS_COMPLETED_TIME` — enabling `shouldShowRelearnTasksCompletedMessage()` and its 6-hour banner to reflect real data.
- No Connect DB schema change, no migration, no persisted task model.
