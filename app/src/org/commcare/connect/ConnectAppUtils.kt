package org.commcare.connect

import android.app.Activity
import android.content.Context
import org.commcare.CommCareApplication
import org.commcare.commcaresupportlibrary.CommCareLauncher
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.ConnectCredentialResolver
import org.commcare.tasks.ResourceEngineListener
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import java.util.Date

object ConnectAppUtils {
    private const val APP_DOWNLOAD_TASK_ID: Int = 4
    const val IS_LAUNCH_FROM_CONNECT = "is_launch_from_connect"

    @Volatile
    private var isAppDownloading = false

    fun downloadApp(
        installUrl: String?,
        listener: ResourceEngineListener?,
    ) {
        if (!isAppDownloading) {
            isAppDownloading = true
            // Start a new download
            ResourceInstallUtils.startAppInstallAsync(
                false,
                APP_DOWNLOAD_TASK_ID,
                object : CommCareTaskConnector<ResourceEngineListener?> {
                    override fun <A : Any?, B : Any?, C : Any?> connectTask(task: CommCareTask<A, B, C, ResourceEngineListener?>?) {
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

                    override fun getReceiver(): ResourceEngineListener? = listener

                    override fun startTaskTransition() {
                    }

                    override fun stopTaskTransition(taskId: Int) {
                    }

                    override fun hideTaskCancelButton() {
                    }
                },
                installUrl,
            )
        }
    }

    fun shouldOverridePassword(personalIdManagedLogin: Boolean): Boolean =
        PersonalIdManager.getInstance().isloggedIn() && personalIdManagedLogin

    fun getPasswordOverride(
        context: Context,
        username: String,
        createIfNeeded: Boolean,
    ): String {
        val seatedAppId = CommCareApplication.instance().currentApp.uniqueId
        return ConnectCredentialResolver(context).resolve(seatedAppId, username, createIfNeeded).password
    }

    fun updateLastAccessed(
        context: Context,
        appId: String,
        username: String,
    ) {
        val record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, appId, username)
        if (record != null) {
            record.lastAccessed = Date()
            ConnectAppDatabaseUtil.storeApp(context, record)
        }
    }

    fun launchApp(
        activity: Activity,
        isLearning: Boolean,
        appId: String,
    ) {
        CommCareApplication.instance().closeUserSession()
        val appType = if (isLearning) "Learn" else "Deliver"
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId)
        HashMap<String, Any>()
            .apply {
                put(IS_LAUNCH_FROM_CONNECT, true)
            }.also { CommCareLauncher.launchCommCareForAppId(activity, appId, it) }
        activity.finish()
    }

    /**
     * Launches an already-seated, already-authenticated app from Connect without tearing down the
     * active session, so DispatchActivity routes straight to Home (no LoginActivity) while still
     * flagging the launch as Connect-managed for back-to-Connect handling. Used by the silent-launch
     * success path.
     */
    fun launchSeatedAppFromConnect(
        activity: Activity,
        appId: String,
    ) {
        HashMap<String, Any>()
            .apply {
                put(IS_LAUNCH_FROM_CONNECT, true)
            }.also { CommCareLauncher.launchCommCareForAppId(activity, appId, it) }
        activity.finish()
    }
}
