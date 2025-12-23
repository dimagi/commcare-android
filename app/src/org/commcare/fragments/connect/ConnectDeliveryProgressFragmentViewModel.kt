package org.commcare.fragments.connect

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectJobHelper
import org.commcare.fragments.connect.base.BaseNetworkStateViewModel
import org.commcare.interfaces.base.BaseConnectView
import java.util.Date

class ConnectDeliveryProgressFragmentViewModel : BaseNetworkStateViewModel() {
    var previousLastUpdatedOn: Date? = null
    var showLoading = false // by default should be false, only true whenever user presses sync button

    fun fetchData(
        context: Context,
        job: ConnectJobRecord,
        view: BaseConnectView,
    ) {
//        TODO
//        lastModifiedOn = job.lastUpdate // this parameter is not coming currently from server.
        launchTask {
            ConnectJobHelper.updateDeliveryProgress(
                context,
                job,
                showLoading,
                view,
                object : ConnectActivityCompleteListener {
                    override fun connectActivityComplete(success: Boolean) {
                        showLoading = false // back to default value
                        //  TODO
                        //  Real check is `success && job.lastUpdate>previousLastUpdatedOn`
                        //  to be used whenever server starts sending lastModifiedOn
                        if (success) {
                            networkStateLiveData.value = NetworkState.Success(success)
                        } else {
                            networkStateLiveData.value = NetworkState.Error(Exception("UNKNOWN_EXCEPTION"))
                        }
                    }
                },
            )
        }
    }
}
