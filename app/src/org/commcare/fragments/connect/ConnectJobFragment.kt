package org.commcare.fragments.connect

import android.os.Bundle
import androidx.viewbinding.ViewBinding
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.fragments.base.BaseConnectFragment

abstract class ConnectJobFragment<T : ViewBinding> : BaseConnectFragment<T>() {
    protected lateinit var job: ConnectJobRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadActiveJob()
    }

    protected fun setActiveJob(updatedJob: ConnectJobRecord) {
        job = updatedJob
        (requireActivity() as ConnectActivity).activeJob = updatedJob
    }

    protected fun reloadActiveJob() {
        job = (requireActivity() as ConnectActivity).activeJob
    }

    /** Opens this opportunity's learn or delivery app, installing it first if need be. */
    protected fun launchApp(isLearning: Boolean) = launchApp(job, isLearning)

    override fun getEndpoint(): String? = null
}
