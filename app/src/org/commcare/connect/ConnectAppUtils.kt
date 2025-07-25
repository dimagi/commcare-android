package org.commcare.connect

import android.app.Activity
import android.content.Context
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.commcaresupportlibrary.CommCareLauncher
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.tasks.ResourceEngineListener
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import java.security.SecureRandom

object ConnectAppUtils {
    private const val APP_DOWNLOAD_TASK_ID: Int = 4
    const val IS_LAUNCH_FROM_CONNECT = "is_launch_from_connect"

    @Volatile
    private var isAppDownloading = false

    fun downloadApp(installUrl: String?, listener: ResourceEngineListener?) {
        if (!isAppDownloading) {
            isAppDownloading = true
            //Start a new download
            ResourceInstallUtils.startAppInstallAsync(
                false,
                APP_DOWNLOAD_TASK_ID,
                object : CommCareTaskConnector<ResourceEngineListener?> {
                    override fun <A : Any?, B : Any?, C : Any?>
                            connectTask(task:CommCareTask<A, B, C, ResourceEngineListener?>?) {
                    }

                    override fun startBlockingForTask(id: Int) {
                        isAppDownloading = true
                    }

                    override fun stopBlockingForTask(id: Int) {
                        isAppDownloading = false
                    }

                    override fun taskCancelled() {
                        isAppDownloading = false
                    }

                    override fun getReceiver(): ResourceEngineListener? {
                        return listener
                    }

                    override fun startTaskTransition() {
                    }

                    override fun stopTaskTransition(taskId: Int) {
                    }

                    override fun hideTaskCancelButton() {
                    }
                },
                installUrl
            )
        }
    }

    fun shouldOverridePassword(personalIdManagedLogin: Boolean): Boolean {
        return PersonalIdManager.getInstance().isloggedIn() && personalIdManagedLogin
    }

    fun getPasswordOverride(context: Context?, username: String?,
                            createIfNeeded: Boolean): String {
        val seatedAppId = CommCareApplication.instance().currentApp.uniqueId

        var record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, seatedAppId, username)
        if (record == null) {
            if(createIfNeeded) {
                record = storeNewConnectLinkedAppRecord(context, seatedAppId, username)
            } else {
                throw RuntimeException(
                    "No ConnectLinkedAppRecord found for appId: $seatedAppId and username: $username"
                )
            }
        }

        if (record.isUsingLocalPassphrase) {
            //Report to analytics so we know when this stops happening
            FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(seatedAppId)
        }

        return record.password
    }

    private fun storeNewConnectLinkedAppRecord(
        context: Context?,
        appId: String?,
        username: String?
    ): ConnectLinkedAppRecord {
        return ConnectAppDatabaseUtil.storeApp(
            context,
            appId,
            username,
            true,
            generateAppPassword(),
            true,
            false
        )
    }

    private fun generateAppPassword(): String {
        val passwordLength = 20
        val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?"
        val secureRandom = SecureRandom()
        return (1..passwordLength)
            .map { charSet[secureRandom.nextInt(charSet.length)] }
            .joinToString("")
    }

    fun launchApp(activity: Activity, isLearning: Boolean, appId: String) {
        CommCareApplication.instance().closeUserSession()
        val appType = if (isLearning) "Learn" else "Deliver"
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId)
        HashMap<String, Any>().apply {
            put(IS_LAUNCH_FROM_CONNECT, true)
        }.also { CommCareLauncher.launchCommCareForAppId(activity, appId, it) }
        activity.finish()
    }
}
