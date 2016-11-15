package org.commcare.activities.components;

import android.support.annotation.DrawableRes;

/**
 * Represents a single item in the RootMenuHomeActivity's nav drawer
 *
 * @author Aliza Stone
 */
public class NavDrawerItem {

    public String id;
    public String text;
    public String subtext;
    @DrawableRes
    public int iconResource;

    public NavDrawerItem(String id, String text, @DrawableRes int iconResource, String subtext) {
        this.id = id;
        this.text = text;
        this.subtext = subtext;
        this.iconResource = iconResource;
    }

    public void updateSubtext(String subtext) {
        this.subtext = subtext;
    }

}
