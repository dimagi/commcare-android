package org.commcare.androidTests

import android.Manifest
import android.os.Build
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import org.commcare.CommCareApplication
import org.commcare.activities.DispatchActivity
import org.commcare.utils.InstrumentationUtility
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
abstract class BaseTest {

    @Rule
    @JvmField
    var intentsRule = IntentsTestRule(DispatchActivity::class.java)

    @Rule
    @JvmField
    var permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*getAppPermissions())

    protected open fun installApp(appName: String, ccz: String, force: Boolean = false) {
        if (CommCareApplication.instance().currentApp == null) {
            InstrumentationUtility.installApp(ccz)
        } else if (appName != CommCareApplication.instance().currentApp.appRecord.displayName || force) {
            // We already have an installed app, But not the one we need for this test.
            InstrumentationUtility.uninstallCurrentApp()
            InstrumentationUtility.installApp(ccz)
            // App installation doesn't take back to login screen. Is this an issue?
            Espresso.pressBack()
        }
    }

    private fun getAppPermissions(): Array<String> {
        var appPermissions = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appPermissions.addAll(arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS))
        } else {
            appPermissions.addAll(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE))
        }
        return appPermissions.toTypedArray()
    }
}
