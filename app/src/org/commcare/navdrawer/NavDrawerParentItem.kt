package org.commcare.navdrawer

import androidx.annotation.DrawableRes

sealed class NavDrawerItem {
    data class ParentItem(
        val title: String,
        @DrawableRes val iconResId: Int,
        val type: BaseDrawerActivity.NavItemType,
        var isExpanded: Boolean = false,
        val children: List<ChildItem> = emptyList()
    ) : NavDrawerItem()

    data class ChildItem(
        val childTitle: String,
        val recordId: String
    ) : NavDrawerItem()
}