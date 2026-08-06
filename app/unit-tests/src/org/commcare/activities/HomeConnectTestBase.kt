package org.commcare.activities

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
 * Shared fixtures for the home screen's PersonalId/Connect tests (`HomeConnect*`).
 *
 * Seating a job writes the same rows into the real Connect DB that a Connect sync would leave
 * behind — a signed-in PersonalId user, the job, and the app record linking it to the seated
 * CommCare app — so the home screen resolves it through the production lookups rather than a stub.
 */
abstract class HomeConnectTestBase : HomeScreenActivityTest() {
    /**
     * Seat [job] against the currently seated CommCare app. Pass `isLearning = true` to link the
     * seated app as the job's learn app rather than its delivery app.
     *
     * Both app records are written, as a Connect sync would leave them: a job carries a learn app
     * and a delivery app, and code such as `passedAssessment()` reads the learn app's passing score
     * unconditionally. Only one of the two is the seated CommCare app.
     */
    protected fun seatJob(
        job: ConnectJobRecord,
        isLearning: Boolean = false,
    ) {
        signInToPersonalId(hasConnectAccess = true)
        connectStorage(ConnectJobRecord::class.java).write(job)
        val apps = connectStorage(ConnectAppRecord::class.java)
        apps.write(appRecordFor(job, seatedAppId(), isLearning))
        apps.write(appRecordFor(job, UNSEATED_APP_ID, !isLearning))
    }

    /**
     * A job with no warning to show: active, running, and well under both visit caps, so
     * `getCardMessageText()` returns null. Individual tests push it past one of those edges.
     */
    protected fun connectJob(
        jobUUID: String = "test-job-uuid",
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

    protected fun daysFromNow(days: Int): Date = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong()))

    /**
     * Write a signed-in PersonalId user, then let [PersonalIdManager.init] derive its status from
     * that record. `ConnectJobUtils.getAppRecord()` returns null unless PersonalId reports logged
     * in, so no Connect lookup resolves without this.
     *
     * The DB file is created because `ConnectUserDatabaseUtil.getUser()` refuses to read unless
     * `dbExists()` finds one on disk, and the test Connect DB is in-memory
     * (`DatabaseConnectOpenHelperMock` passes a null name). Reads and writes still go to the real
     * in-memory DB; the empty file only makes that existence probe agree with it.
     */
    protected fun signInToPersonalId(hasConnectAccess: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createConnectDbFile()
        ConnectUserDatabaseUtil.storeUser(
            context,
            ConnectUserRecord("", "", "", "Test User", "", Date(), null, false, "", hasConnectAccess),
        )
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

    private fun <T : Persistable> connectStorage(clazz: Class<T>) =
        ConnectDatabaseHelper.getConnectStorage(ApplicationProvider.getApplicationContext(), clazz)

    companion object {
        private const val UNSEATED_APP_ID = "connect-app-not-seated"
        private const val JOB_ID = 1
        private const val MAX_VISITS = 100
        private const val MAX_DAILY_VISITS = 10
        private const val PASSING_SCORE = 80
    }
}
