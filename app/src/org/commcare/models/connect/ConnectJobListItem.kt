package org.commcare.models.connect

import androidx.annotation.StringRes

sealed class ConnectJobListItem {
    data class SectionHeader(
        @StringRes val textResID: Int,
    ) : ConnectJobListItem()

    data class JobItem(
        val jobModel: ConnectLoginJobListModel,
        val isCorrupt: Boolean,
    ) : ConnectJobListItem()
}
