package org.commcare.navdrawer

import androidx.annotation.DrawableRes

data class NavDrawerItem(
    val title: String,
    @DrawableRes val iconResId: Int,
    val type: BaseDrawerController.NavItemType,
    val badgeCount: Int? = null,
)
