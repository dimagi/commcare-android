package org.commcare.utils;

import android.view.View;
import android.widget.ImageButton;

import org.commcare.dalvik.R;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;

public class ConnectIdAppBarUtils {
    /**
     * Sets the title for the AppBar.
     *
     * @param view   The root view where the AppBar is located.
     * @param title  The title text to set.
     */
    public static void setTitle(View view, String title) {
        ConnectMediumTextView titleView = view.findViewById(R.id.title);
        titleView.setText(title);
    }

    /**
     * Sets the menu icon properties including visibility, icon resource, and click listener.
     *
     * @param view           The root view where the menu icon is located.
     * @param iconResId      The drawable resource ID for the menu icon (use 0 to keep the existing icon).
     * @param isVisible      If true, the menu icon will be visible; otherwise, it will be hidden.
     * @param clickListener  The click listener for the menu icon (can be null).
     */
    public static void setBackButtonWithCallBack(View view, int iconResId, boolean isVisible, View.OnClickListener clickListener) {
        ImageButton menu_button = view.findViewById(R.id.menu_button);

        if (menu_button != null) {
            menu_button.setVisibility(isVisible ? View.VISIBLE : View.GONE);

            // Set the icon resource if provided
            if (iconResId != 0) {
                menu_button.setImageResource(iconResId);
            }

            // Set the click listener if provided
            if (clickListener != null) {
                menu_button.setOnClickListener(clickListener);
            }
        }
    }

    /**
     * Sets the notification icon properties including visibility and click listener.
     *
     * @param view          The root view where the notification icon is located.
     * @param isVisible     If true, the notification icon will be visible; otherwise, it will be hidden.
     * @param clickListener The click listener for the notification icon (can be null).
     */
    public static void setNotificationIconWithCallback(View view, boolean isVisible, View.OnClickListener clickListener) {
        ImageButton notificationIcon = view.findViewById(R.id.notification_icon);

        if (notificationIcon != null) {
            notificationIcon.setVisibility(isVisible ? View.VISIBLE : View.GONE);

            if (clickListener != null) {
                notificationIcon.setOnClickListener(clickListener);
            }
        }
    }

    /**
     * Sets the back button properties including visibility and click listener.
     *
     * @param view          The root view where the back button is located.
     * @param isVisible     If true, the back button will be visible; otherwise, it will be hidden.
     * @param clickListener The click listener for the back button (can be null).
     */
    public static void setMenuIconWithCallback(View view, boolean isVisible, View.OnClickListener clickListener) {
        ImageButton menu_icon = view.findViewById(R.id.menu_icon);

        if (menu_icon != null) {
            menu_icon.setVisibility(isVisible ? View.VISIBLE : View.GONE);

            if (clickListener != null) {
                menu_icon.setOnClickListener(clickListener);
            }
        }
    }
}
