package org.commcare.connect

import android.app.Activity
import android.content.Context
import org.commcare.AppUtils
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.commcaresupportlibrary.CommCareLauncher
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.JobStoreManager
import org.commcare.connect.network.ApiConnect
import org.commcare.connect.network.ConnectNetworkHelper
import org.commcare.connect.network.IApiCallback
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.tasks.ResourceEngineListener
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom

object ConnectAppUtils {
    private const val APP_DOWNLOAD_TASK_ID: Int = 4
    private var primedAppIdForAutoLogin: String? = null

    fun wasAppLaunchedFromConnect(appId: String?): Boolean {
        val primed = primedAppIdForAutoLogin
        primedAppIdForAutoLogin = null
        return primed == appId
    }

    fun isAppInstalled(appId: String): Boolean {
        for (app in AppUtils.getInstalledAppRecords()) {
            if (appId == app.uniqueId) {
                return true
            }
        }
        return false
    }

    private var downloading = false
    private var downloadListener: ResourceEngineListener? = null

    fun downloadAppOrResumeUpdates(installUrl: String?, listener: ResourceEngineListener?) {
        downloadListener = listener
        if (!downloading) {
            downloading = true
            //Start a new download
            ResourceInstallUtils.startAppInstallAsync(
                false,
                APP_DOWNLOAD_TASK_ID,
                object : CommCareTaskConnector<ResourceEngineListener?> {
                    override fun <A : Any?, B : Any?, C : Any?>
                            connectTask(task:CommCareTask<A, B, C, ResourceEngineListener?>?) {
                    }

                    override fun startBlockingForTask(id: Int) {
                    }

                    override fun stopBlockingForTask(id: Int) {
                        downloading = false
                    }

                    override fun taskCancelled() {
                    }

                    override fun getReceiver(): ResourceEngineListener? {
                        return downloadListener
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

    fun checkAutoLoginAndOverridePassword(
        context: Context?, appId: String?, username: String?,
        passwordOrPin: String?, appLaunchedFromConnect: Boolean, uiInAutoLogin: Boolean
    ): String? {
        var updatedPasswordOrPin = passwordOrPin
        if (PersonalIdManager.getInstance().isloggedIn()) {
            if (appLaunchedFromConnect) {
                //Configure some things if we haven't already
                var record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                    context,
                    appId, username
                )
                if (record == null) {
                    record = prepareConnectManagedApp(context, appId, username)
                }

                updatedPasswordOrPin = record.password
            } else if (uiInAutoLogin) {
                val seatedAppId = CommCareApplication.instance().currentApp.uniqueId
                val record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                    context, seatedAppId,
                    username
                )
                updatedPasswordOrPin = record?.password

                if (record != null && record.isUsingLocalPassphrase) {
                    //Report to analytics so we know when this stops happening
                    FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(seatedAppId)
                }
            }
        }

        return updatedPasswordOrPin
    }

    private fun prepareConnectManagedApp(
        context: Context?,
        appId: String?,
        username: String?
    ): ConnectLinkedAppRecord {
        //Create app password
        val password = generatePassword()

        //Store ConnectLinkedAppRecord (note worker already linked)
        return ConnectAppDatabaseUtil.storeApp(
            context,
            appId,
            username,
            true,
            password,
            true,
            false
        )
    }

    private fun generatePassword(): String {
        val passwordLength = 20

        val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?"
        val secureRandom = SecureRandom()
        val password = StringBuilder(passwordLength)
        for (i in 0..<passwordLength) {
            password.append(charSet[secureRandom.nextInt(charSet.length)])
        }

        return password.toString()
    }

    fun launchApp(activity: Activity, isLearning: Boolean, appId: String) {
        CommCareApplication.instance().closeUserSession()

        val appType = if (isLearning) "Learn" else "Deliver"
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId)

        primedAppIdForAutoLogin = appId

        CommCareLauncher.launchCommCareForAppId(activity, appId)

        activity.finish()
    }

    /**
    * Retrieves Connect job opportunities for the current user and stores them locally.
    * Requires valid activity to fetch details.
    */
    fun retrieveConnectOpportunities(activity: Activity) {
        val user = ConnectUserDatabaseUtil.getUser(activity)
        ApiConnect.getConnectOpportunities(activity, user, object : IApiCallback {
            override fun processSuccess(responseCode: Int, responseData: InputStream?) {
                try {
                    val responseAsString = responseData?.let { input ->
                        String(StreamsUtil.inputStreamToByteArray(input))
                    } ?: return

                    if (responseAsString.isNotEmpty()) {
                        val json = JSONArray(responseAsString)
                        val jobs = mutableListOf<ConnectJobRecord>()

                        for (i in 0 until json.length()) {
                            try {
                                val obj = json.getJSONObject(i)
                                val job = ConnectJobRecord.fromJson(obj)
                                jobs.add(job)
                            } catch (e: JSONException) {
                                Logger.exception("Parsing return from Opportunities request", e)
                            }
                        }

                        JobStoreManager(activity).storeJobs(activity, jobs, true)
                    }

                } catch (e: JSONException) {
                    throw RuntimeException(e)
                } catch (e: IOException) {
                    Logger.exception("Parsing return from Opportunities request", e)
                }
            }

            override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
                Logger.log("ERROR", "Opportunities call failed: $responseCode")
            }

            override fun processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(activity)
            }

            override fun processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(activity)
            }

            override fun processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException()
            }

            override fun processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(activity)
            }
        })
    }

    /**
     * Returns true if there is any job available for the user.
     * Requires activity to fetch job
     */
    fun hasConnectJobs(activity: Activity): Boolean {
        return ConnectJobUtils.getCompositeJobs(
                activity,
                ConnectJobRecord.STATUS_ALL_JOBS,
                null
        ).isNotEmpty()
    }
}