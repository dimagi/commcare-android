package org.commcare.interfaces

import org.commcare.models.connect.ConnectLoginJobListModel

fun interface OnJobCardClick {
    fun onClick(job: ConnectLoginJobListModel)
}
