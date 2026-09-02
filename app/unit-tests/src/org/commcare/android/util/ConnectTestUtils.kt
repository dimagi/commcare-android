package org.commcare.android.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectAppRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.models.database.connect.ConnectDatabaseSchemaManager
import org.javarosa.core.services.storage.Persistable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Fixtures for Connect/PersonalId state, written into the real Connect DB.
 *
 * Seating a job leaves the same rows a Connect sync would — a signed-in PersonalId user, the job,
 * and the app records linking it to a CommCare app — so production lookups resolve it rather than a
 * stub. Call [ConnectDatabaseHelper.teardown] between tests.
 */
object ConnectTestUtils {
    const val JOB_UUID = "test-job-uuid"
    const val MAX_VISITS = 100

    private const val PASSING_SCORE = 80
    private const val MAX_DAILY_VISITS = 10
    private const val UNSEATED_APP_ID = "connect-app-not-seated"
    private const val JOB_ID = 1

    /**
     * Seat [job] against the currently seated CommCare app. Pass `isLearning = true` to link the
     * seated app as the job's learn app rather than its delivery app.
     *
     * Both app records are written, as a Connect sync would leave them: `passedAssessment()` and
     * friends read the learn app unconditionally. Only one of the two is the seated CommCare app.
     */
    fun seatJob(
        job: ConnectJobRecord,
        isLearning: Boolean = false,
    ) {
        signInToPersonalId(hasConnectAccess = true)
        connectStorage(ConnectJobRecord::class.java).write(job)
        val apps = connectStorage(ConnectAppRecord::class.java)
        apps.write(appRecordFor(job, TestAppInstaller.seatedAppId(), isLearning))
        apps.write(appRecordFor(job, UNSEATED_APP_ID, !isLearning))
    }

    /**
     * A job with no warning to show: active, running, and well under both visit caps, so
     * `getCardMessageText()` returns null. Individual tests push it past one of those edges.
     */
    fun connectJob(
        jobUUID: String = JOB_UUID,
        status: Int = ConnectJobRecord.STATUS_DELIVERING,
        title: String = "Test Job",
        shortDescription: String = "Job description",
        endDate: Date = daysFromNow(30),
    ): ConnectJobRecord =
        ConnectJobRecord().apply {
            setJobUUID(jobUUID)
            setJobId(JOB_ID)
            setStatus(status)
            setTitle(title)
            setShortDescription(shortDescription)
            // Every persisted String has to be non-null: the record serialises all of them on write.
            setDescription("Long job description")
            setOrganization("Test Org")
            setCurrency("USD")
            setPaymentAccrued("0")
            setIsActive(true)
            setProjectStartDate(daysFromNow(-1))
            setProjectEndDate(endDate)
            setMaxVisits(MAX_VISITS)
            setMaxDailyVisits(MAX_DAILY_VISITS)
        }

    fun daysFromNow(days: Int): Date = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong()))

    /**
     * Write a signed-in PersonalId user, then let [PersonalIdManager.init] derive its status from
     * that record: no Connect lookup resolves unless PersonalId reports logged in.
     *
     * The stored token is what keeps Connect API calls off the PersonalId token endpoint —
     * `getConnectToken()` returns an unexpired stored token without a round-trip.
     *
     * The DB file is created because `getUser()` refuses to read unless `dbExists()` finds one on
     * disk, and the test Connect DB is in-memory. Reads and writes still go to that in-memory DB;
     * the empty file only makes the existence probe agree with it.
     */
    fun signInToPersonalId(hasConnectAccess: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createConnectDbFile()
        val user = ConnectUserRecord("", "", "", "Test User", "", Date(), null, false, "", hasConnectAccess)
        user.updateConnectToken("test-connect-token", daysFromNow(1))
        ConnectUserDatabaseUtil.storeUser(user)
        PersonalIdManager.getInstance().init(context)
    }

    private fun createConnectDbFile() {
        CommCareApplication.instance().getDatabasePath(ConnectDatabaseSchemaManager.DB_NAME).apply {
            parentFile?.mkdirs()
            createNewFile()
        }
    }

    private fun appRecordFor(
        job: ConnectJobRecord,
        appId: String,
        isLearning: Boolean,
    ): ConnectAppRecord =
        ConnectAppRecord
            .fromJson(
                JSONObject().apply {
                    put(ConnectAppRecord.META_DOMAIN, "test-domain")
                    put(ConnectAppRecord.META_APP_ID, appId)
                    put(ConnectAppRecord.META_NAME, "Test Connect App")
                    put(ConnectAppRecord.META_DESCRIPTION, "App description")
                    put(ConnectAppRecord.META_ORGANIZATION, "Test Org")
                    put(ConnectAppRecord.META_PASSING_SCORE, PASSING_SCORE)
                    put(ConnectAppRecord.META_INSTALL_URL, "https://example.org/app.ccz")
                    put(ConnectAppRecord.META_MODULES, JSONArray())
                },
                job,
                isLearning,
            ).apply { setLastUpdate(Date()) }

    private fun <T : Persistable> connectStorage(clazz: Class<T>) = ConnectDatabaseHelper.getConnectStorage(clazz)
}
