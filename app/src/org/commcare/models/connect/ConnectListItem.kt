package org.commcare.models.connect

import androidx.annotation.StringRes

sealed class ConnectListItem {
    data class SectionHeader(
        @StringRes val textResID: Int,
    ) : ConnectListItem()

    data class JobItem(
        val jobModel: ConnectLoginJobListModel,
        val isCorrupt: Boolean,
    ) : ConnectListItem()
}
