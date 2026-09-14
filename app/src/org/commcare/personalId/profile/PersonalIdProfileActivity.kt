package org.commcare.personalId.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import org.commcare.activities.DispatchActivity
import org.commcare.activities.NavigationHostCommCareActivity
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.fragments.personalId.PersonalIdProfileSendEmailOtpFragmentArgs
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.views.dialogs.CustomProgressDialog

class PersonalIdProfileActivity : NavigationHostCommCareActivity<PersonalIdProfileActivity>() {
    companion object {
        const val EXTRA_PENDING_BACKUP_CODE = "extra_pending_backup_code"
        const val EXTRA_INITIATE_BACKUP_CODE_RECOVERY = "extra_initiate_backup_code_recovery"
    }

    override fun getLayoutResource(): Int = R.layout.activity_personalid_profile

    override fun getHostFragment(): NavHostFragment = supportFragmentManager.findFragmentById(R.id.profile_nav_host) as NavHostFragment

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val appBarConfiguration = AppBarConfiguration(emptySet())
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        if (savedInstanceState == null) {
            initiateBackupCodeRecoveryIfRequired()
        }
    }

    private fun initiateBackupCodeRecoveryIfRequired() {
        val isPendingBackupCodeLaunch = intent.getBooleanExtra(EXTRA_PENDING_BACKUP_CODE, false)
        val isInitiateBackupCodeRecovery =
            intent.getBooleanExtra(EXTRA_INITIATE_BACKUP_CODE_RECOVERY, false)
        if (!isInitiateBackupCodeRecovery && !isPendingBackupCodeLaunch) return
        val workflow =
            if (isInitiateBackupCodeRecovery) EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER else EmailWorkFlow.PENDING_BACKUP_CODE
        val email = ConnectUserDatabaseUtil.getUser().email
        if (email != null) {
            val args =
                PersonalIdProfileSendEmailOtpFragmentArgs
                    .Builder(email, workflow)
                    .setMasked(true)
                    .build()
            val navOptions = NavOptions.Builder()
            if (workflow == EmailWorkFlow.PENDING_BACKUP_CODE) {
                navOptions.setPopUpTo(R.id.personalid_profile_fragment, true)
            }
            navController.navigate(
                R.id.personalid_send_email_otp_fragment,
                args.toBundle(),
                navOptions.build()
            )
        } else {
            if (workflow == EmailWorkFlow.PENDING_BACKUP_CODE) {
                throw IllegalStateException("EXTRA_PENDING_BACKUP_CODE launched but user has no email")
            }
            showAddEmailToast();
        }
    }

    override fun shouldShowBreadcrumbBar(): Boolean = false

    override fun generateProgressDialog(taskId: Int): CustomProgressDialog =
        CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId)

    fun forgetPersonalIdAccount() {
        PersonalIdManager
            .getInstance()
            .forgetUser(AnalyticsParamValue.PERSONAL_ID_FORGOT_USER_PROFILE_PAGE)
        val intent =
            Intent(this, DispatchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    fun showAddEmailToast() {
        Toast
            .makeText(
                this,
                R.string.personalid_profile_add_email_toast,
                Toast.LENGTH_LONG,
            ).show()
    }
}
