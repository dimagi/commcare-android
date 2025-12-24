package org.commcare.models.connect

sealed class ConnectListItem {
    data class SectionHeader(val text: String) : ConnectListItem()
    data class JobItem(
        val jobModel: ConnectLoginJobListModel,
        val isCorrupt: Boolean
    ) : ConnectListItem()
}