package org.commcare.activities.components;

import android.support.annotation.DrawableRes;

/**
 * Represents a single item in the RootMenuHomeActivity's nav drawer
 *
 * @author Aliza Stone
 */
public class NavDrawerItem {

    public final String id;
    public final String text;
    public String subtext;
    @DrawableRes
    public final int iconResource;

    public NavDrawerItem(String id, String text, @DrawableRes int iconResource, String subtext) {
        this.id = id;
        this.text = text;
        this.subtext = subtext;
        this.iconResource = iconResource;
    }

}
