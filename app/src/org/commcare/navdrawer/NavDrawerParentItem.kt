package org.commcare.navdrawer

sealed class NavDrawerItem {
    data class NavDrawerParentItem(
        val title: String,
        val iconResId: Int,
        val type: BaseDrawerActivity.NavItemType,
        var isExpanded: Boolean = false,
        val children: List<NavDrawerChildItem> = emptyList()
    ) : NavDrawerItem()

    data class NavDrawerChildItem(
        val childTitle: String,
        val recordId: String
    ) : NavDrawerItem()
}