package org.commcare.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.dalvik.R
import org.commcare.suite.model.AndroidPackageDependency
import org.commcare.utils.PlaystoreUtils.isApkInstalled
import org.commcare.views.dialogs.StandardAlertDialog

/**
 * Utility methods to handle missing app dependencies
 */
object ApkDependenciesUtils {

    /**
     * Checks whether all dependencies are satisfied and trigger the Dependency Install Workflow if not
     * @return whether all dependencies are satisified
     */
    @JvmStatic
    fun performDependencyCheckFlow(commCareActivity: CommCareActivity<*>): Boolean {
        val dependency = getUnsatisfiedDependency()
        if (dependency != null) {
            val dependencyInstallDialog = getDependencyInstallDialog(commCareActivity, dependency)
            commCareActivity.showAlertDialog(dependencyInstallDialog)
            return false
        }
        return true
    }

    private fun getUnsatisfiedDependency(): AndroidPackageDependency? {
        val dependencies = CommCareApplication.instance().commCarePlatform.currentProfile.dependencies
        for (dependency in dependencies) {
            if (!isApkInstalled(dependency.id)) {
                return dependency
            }
        }
        return null
    }

    private fun getDependencyInstallDialog(context: Context, dependency: AndroidPackageDependency): StandardAlertDialog {
        val title = StringUtils.getStringRobust(context, R.string.dependency_missing_dialog_title)
        val message = StringUtils.getStringRobust(context, R.string.dependency_missing_dialog_message)
        val alertDialog = StandardAlertDialog(context, title, message)
        val buttonText = StringUtils.getStringRobust(
            context,
            R.string.dependency_missing_dialog_go_to_store
        )
        alertDialog.setPositiveButton(buttonText) { dialog: DialogInterface, _: Int ->
            if(launchPlayStore(context, alertDialog, dependency.id)) {
                dialog.dismiss()
            }
        }
        alertDialog.dismissOnBackPress()
        return alertDialog
    }

    private fun launchPlayStore(context: Context, dialog: StandardAlertDialog, packageName: String): Boolean {
        return try {
            PlaystoreUtils.launchPlayStore(context,packageName)
            true
        } catch (e: ActivityNotFoundException) {
            showNoPlaystoreFoundError(context, dialog)
            false
        }
    }

    private fun showNoPlaystoreFoundError(context: Context, dialog: StandardAlertDialog) {
        val error = StringUtils.getStringRobust(context, R.string.dependency_missing_dialog_playstore_not_found)
        dialog.addEmphasizedMessage(error)
    }
}
