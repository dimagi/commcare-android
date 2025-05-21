package org.commcare.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.dalvik.R
import org.commcare.suite.model.AndroidPackageDependency
import org.commcare.utils.StringUtils.getStringRobust
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.NoLocalizedTextException

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
        val unsatisfiedDependencies = getUnsatisfiedDependencies()
        if (!unsatisfiedDependencies.isEmpty()) {
            val dependencyInstallDialog = getDependencyInstallDialog(commCareActivity, unsatisfiedDependencies)
            commCareActivity.showAlertDialog(dependencyInstallDialog)
            return false
        }
        return true
    }

    private fun getUnsatisfiedDependencies(): ArrayList<AndroidPackageDependency> {
        val unsatisfiedDependencies = ArrayList<AndroidPackageDependency>()
        val dependencies = CommCareApplication.instance().commCarePlatform.currentProfile.dependencies
        for (dependency in dependencies) {
            if (!CommCareApplication.instance().androidPackageUtils.isApkInstalled(dependency.id)) {
                unsatisfiedDependencies.add(dependency)
            }
        }
        return unsatisfiedDependencies
    }

    private fun getDependencyInstallDialog(
        context: Context,
        unsatisfiedDependencies: ArrayList<AndroidPackageDependency>
    ): StandardAlertDialog {
        var title = getStringRobust(context, R.string.dependency_missing_dialog_title)
        var message = getStringRobust(context, R.string.dependency_missing_dialog_message, getDependencyName(unsatisfiedDependencies[0]))
        if (unsatisfiedDependencies.size > 1) {
            title = getStringRobust(context, R.string.dependency_missing_dialog_title_plural)
            message = getStringRobust(context, R.string.dependency_missing_dialog_message_plural)
            message += "\n"
            unsatisfiedDependencies.forEachIndexed{index, dependency ->
                message += "\n" + (index+1) + ". " + getDependencyName(dependency)
            }
        }
        val alertDialog = StandardAlertDialog(title, message)
        val buttonText = getStringRobust(
            context,
            R.string.dependency_missing_dialog_go_to_store
        )
        alertDialog.setPositiveButton(buttonText) { dialog: DialogInterface, _: Int ->
            if (launchPlayStore(context, alertDialog, unsatisfiedDependencies[0].id)) {
                dialog.dismiss()
            }
        }
        alertDialog.dismissOnBackPress()
        return alertDialog
    }

    private fun getDependencyName(androidPackageDependency: AndroidPackageDependency): String {
        try {
            return Localization.get("android.package.name.${androidPackageDependency.id}")
        } catch (e: NoLocalizedTextException) {
            val name = AndroidPackageUtils().getPackageName(androidPackageDependency.id)
            if (!name.isNullOrEmpty()) {
                return name
            }
        }
        return androidPackageDependency.id
    }

    private fun launchPlayStore(context: Context, dialog: StandardAlertDialog, packageName: String): Boolean {
        return try {
            PlaystoreUtils.launchPlayStore(context, packageName)
            true
        } catch (e: ActivityNotFoundException) {
            showNoPlaystoreFoundError(context, dialog)
            false
        }
    }

    private fun showNoPlaystoreFoundError(context: Context, dialog: StandardAlertDialog) {
        val error = getStringRobust(context, R.string.dependency_missing_dialog_playstore_not_found)
        dialog.setEmphasizedMessage(error)
    }
}
