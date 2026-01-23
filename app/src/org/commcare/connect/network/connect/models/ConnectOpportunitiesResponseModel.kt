package org.commcare.connect.network.connect.models

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.models.connect.ConnectLoginJobListModel

data class ConnectOpportunitiesResponseModel (
    val validJobs: ArrayList<ConnectJobRecord> = ArrayList(),
    val corruptJobs: ArrayList<ConnectLoginJobListModel> = ArrayList()

)