package org.commcare

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.commcare.android.database.global.models.ApplicationRecord
import org.commcare.android.logging.ReportingUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.utils.CrashUtil
import org.commcare.utils.MultipleAppsUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Crash-reporting app data must follow the seated app, because a host can switch seated apps without
 * being recreated. Each registration is captured as the key values it would send.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CrashAppDataRegistrationTest {
    private data class AppData(
        val domain: String,
        val version: Int,
        val name: String,
    )

    private val registered = mutableListOf<AppData>()
    private lateinit var navApp: ApplicationRecord
    private lateinit var archiveApp: ApplicationRecord

    @Before
    fun setUp() {
        navApp = install(NAV_APP_PATH)
        archiveApp = install(ARCHIVE_APP_PATH)
        mockkStatic(CrashUtil::class)
        every { CrashUtil.registerAppData() } answers {
            registered += AppData(ReportingUtils.getDomain(), ReportingUtils.getAppVersion(), ReportingUtils.getAppName())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(CrashUtil::class)
    }

    @Test
    fun `seating an app registers its data`() {
        seat(navApp)

        assertEquals(listOf(NAV_APP_DATA), registered)
    }

    @Test
    fun `switching the seated app re-registers the new app's data`() {
        seat(navApp)
        seat(archiveApp)

        assertEquals(listOf(NAV_APP_DATA, ARCHIVE_APP_DATA), registered)
    }

    @Test
    fun `unseating the app clears its data`() {
        seat(archiveApp)
        CommCareApplication.instance().unseat(archiveApp)

        assertEquals(listOf(ARCHIVE_APP_DATA, NO_APP_DATA), registered)
    }

    @Test
    fun `unseating an app that is not seated leaves the data alone`() {
        seat(navApp)
        CommCareApplication.instance().unseat(archiveApp)

        assertEquals(listOf(NAV_APP_DATA), registered)
    }

    private fun install(appPath: String): ApplicationRecord {
        TestAppInstaller.installApp(appPath)
        return MultipleAppsUtil.getAppById(TestAppInstaller.seatedAppId())
    }

    private fun seat(record: ApplicationRecord) {
        CommCareApplication.instance().initializeAppResources(CommCareApp(record))
    }

    companion object {
        private const val NAV_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
        private const val ARCHIVE_APP_PATH = "jr://resource/commcare-apps/archive_form_tests/profile.ccpr"
        private val NAV_APP_DATA = AppData("flipper.commcarehq.org", 95, "Untitled Application")
        private val ARCHIVE_APP_DATA = AppData("odk-unit-tests.commcarehq.org", 8, "ODK unit tests")
        private val NO_APP_DATA = AppData("", -1, "")
    }
}
