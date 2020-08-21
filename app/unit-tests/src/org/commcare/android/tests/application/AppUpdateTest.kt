package org.commcare.android.tests.application

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.mocks.ModernHttpRequesterMock
import org.commcare.android.util.TestAppInstaller
import org.commcare.android.util.UpdateUtils
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.models.database.AndroidSandbox
import org.commcare.tasks.InstallStagedUpdateTask
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AppUpdateTest {
    @Before
    fun setup() {
        TestAppInstaller.installAppAndLogin(
                UpdateUtils.buildResourceRef(REF_BASE_DIR, "base_app", "profile.ccpr"),
                "test", "123")
        val p = CommCareApplication.instance().commCarePlatform.currentProfile
        Assert.assertTrue(p.version == 6)
    }

    @Test
    fun testAppUpdate() {
        Log.d(TAG, "Applying a valid app update")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed)
        checkUpdateComplete(9, true)
    }

    private fun checkUpdateComplete(expectedVersion: Int, expectedResetUpgrade: Boolean) {
        val p = CommCareApplication.instance().commCarePlatform.currentProfile
        Assert.assertTrue(p.version == expectedVersion)

        // check that update table has been cleared
        val upgradeTable = CommCareApplication.instance().commCarePlatform.upgradeResourceTable
        Assert.assertTrue(upgradeTable.isEmpty == expectedResetUpgrade)
    }

    @Test
    fun testAppIsUpToDate() {
        Log.d(TAG, "Try updating to the same app.")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "base_app", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpToDate,
                AppInstallStatus.UnknownFailure)
        checkUpdateComplete(6, true)
    }

    @Test
    fun testAppUpdateWithoutLocalStorage() {
        Log.d(TAG, "Try updating after removing local filesystem temp dirs.")

        // nuke local folder that CommCare uses to stage updates.
        val dir = File(CommCareApplication.instance().androidFsTemp)
        Assert.assertTrue(dir.delete())
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.NoLocalStorage,
                AppInstallStatus.UnknownFailure)
        checkUpdateComplete(6, false)
    }

    @Test
    fun testUpdateToBrokenApp() {
        Log.d(TAG, "Applying a broken app update")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "invalid_update", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.InvalidResource,
                AppInstallStatus.UnknownFailure)
        checkUpdateComplete(6, true)
    }

    @Test
    fun testUpdateToAppWithMultimedia() {
        Log.d(TAG, "updating to an app that has multimedia present")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update_with_multimedia_present", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed)
        checkUpdateComplete(14, true)
    }

    @Test
    fun testUpdateToAppMissingMultimedia() {
        Log.d(TAG, "updating to an app that has missing multimedia")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "valid_update_without_multimedia_present", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.MissingResourcesWithMessage,
                AppInstallStatus.UnknownFailure)
        checkUpdateComplete(6, true)
    }

    @Test
    fun testUpdateToAppWithIncompatibleVersion() {
        Log.d(TAG, "updating to an app that requires an newer CommCare version")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "invalid_version", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.IncompatibleReqs,
                AppInstallStatus.UnknownFailure)
        checkUpdateComplete(6, true)
    }

    @Test
    fun testValidUpdateWithNetworkFailureInBetween() {
        ModernHttpRequesterMock.setRequestPayloads(arrayOf("null", "null", "null", "null"))
        val profileRef = UpdateUtils.buildResourceRef(
                REF_BASE_DIR,
                "update_with_a_fake_http_resource",
                "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.NetworkFailure,
                AppInstallStatus.UnknownFailure)
        checkUpdateComplete(6, false)

        // todo check for request age

        // Retry and return a valid response this time
        val formRef = UpdateUtils.buildResourceRef(
                REF_BASE_DIR,
                "update_with_a_fake_http_resource",
                "modules-0/forms-1.xml")
        ModernHttpRequesterMock.setRequestPayloads(arrayOf(formRef))
        ModernHttpRequesterMock.setResponseCodes(arrayOf(200))
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed)
        checkUpdateComplete(9, true)
    }

    @Test
    fun testAppUpdateWithSuiteFixture() {
        Log.d(TAG, "Applying a app update with a suite fixture")
        val sandbox = AndroidSandbox(CommCareApplication.instance())
        val appFixtureStorage = sandbox.appFixtureStorage
        Assert.assertEquals(1, appFixtureStorage.numRecords)
        Assert.assertEquals(1, appFixtureStorage.read(1).root.numChildren)
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "update_with_suite_fixture", "profile.ccpr")
        val updateTask = UpdateUtils.stageUpdate(profileRef, AppInstallStatus.UpdateStaged)

        // ensure suite fixture didn't change if you only staged an update but haven't applied it
        Assert.assertEquals(1, appFixtureStorage.numRecords)
        Assert.assertEquals(1, appFixtureStorage.read(1).root.numChildren)
        Assert.assertEquals(AppInstallStatus.Installed,
                InstallStagedUpdateTask.installStagedUpdate())
        updateTask.clearTaskInstance()

        // ensure suite fixture updated after actually applying a staged update
        Assert.assertEquals(1, appFixtureStorage.numRecords)
        Assert.assertEquals(2, appFixtureStorage.read(1).root.numChildren)
        checkUpdateComplete(9, true)
    }

    companion object {
        private val TAG = AppUpdateTest::class.java.simpleName
        private const val REF_BASE_DIR = "jr://resource/commcare-apps/update_tests/"
    }
}