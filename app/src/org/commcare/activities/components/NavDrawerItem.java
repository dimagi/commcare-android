package org.commcare.activities.components;

import android.support.annotation.DrawableRes;
import android.view.View;

/**
 * Created by amstone326 on 11/10/16.
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

}
