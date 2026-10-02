package org.commcare.connect

import android.content.Context
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.tasks.ResourceEngineListener
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import java.util.Date

object ConnectAppUtils {
    private const val APP_DOWNLOAD_TASK_ID: Int = 4

    @Volatile
    private var isAppDownloading = false

    /**
     * Starts downloading an app, reporting to [listener]. Answers false when a download is already
     * running, in which case [listener] is never called and the caller must not wait on it.
     */
    fun downloadApp(
        installUrl: String?,
        listener: ResourceEngineListener?,
    ): Boolean {
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
            return true
        }
        return false
    }

    fun updateLastAccessed(
        context: Context,
        appId: String,
        username: String,
    ) {
        val record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, username)
        if (record != null) {
            record.lastAccessed = Date()
            ConnectAppDatabaseUtil.storeApp(record)
        }
    }
}
