package org.commcare.personalId.profile

import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import org.commcare.activities.DispatchActivity
import org.commcare.activities.NavigationHostCommCareActivity
import org.commcare.connect.PersonalIdManager
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.views.dialogs.CustomProgressDialog

class PersonalIdProfileActivity : NavigationHostCommCareActivity<PersonalIdProfileActivity>() {
    companion object {
        const val EXTRA_PENDING_BACKUP_CODE = "extra_pending_backup_code"
    }

    override fun getLayoutResource(): Int = R.layout.activity_personalid_profile

    override fun getHostFragment(): NavHostFragment = supportFragmentManager.findFragmentById(R.id.profile_nav_host) as NavHostFragment

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // No top-level destinations, so a back arrow shows on every screen, including the start.
        val appBarConfiguration = AppBarConfiguration(emptySet())
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        if (savedInstanceState == null) {
            checkForPendingBackupCode()
        }
    }

    override fun shouldShowBreadcrumbBar(): Boolean = false

    override fun generateProgressDialog(taskId: Int): CustomProgressDialog =
        CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId)

    private fun checkForPendingBackupCode() {
        if (!intent.getBooleanExtra(EXTRA_PENDING_BACKUP_CODE, false)) return
        navController.navigate(
            R.id.personalid_profile_set_new_backup_code_fragment,
            null,
            NavOptions.Builder()
                .setPopUpTo(R.id.personalid_profile_fragment, true)
                .build()
        )
    }

    fun forgetPersonalIdAccount() {
        PersonalIdManager.getInstance().forgetUser(AnalyticsParamValue.PERSONAL_ID_FORGOT_USER_PROFILE_PAGE)
        val intent =
            Intent(this, DispatchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
