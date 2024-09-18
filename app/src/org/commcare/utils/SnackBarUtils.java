package org.commcare.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.commcare.dalvik.R;
import org.commcare.views.connect.CustomSnackBar;

public class SnackBarUtils {

    public static void showErrorSnackBar(Activity activity, View anchorView) {
        // Create CustomSnackBar instance
        CustomSnackBar customSnackBar = new CustomSnackBar(activity, anchorView);

        // Set the text, icons, and other customizations
        customSnackBar.setText("This is a custom Snackbar");
        customSnackBar.setTextColor(ContextCompat.getColor(activity, android.R.color.white));
        customSnackBar.setTextSize(20f);

        // Set left icon
        Drawable leftIcon = ContextCompat.getDrawable(activity, R.drawable.ic_snackbar_done); // Replace with your drawable resource
        customSnackBar.setLeftIcon(leftIcon);
//        customSnackBar.setLeftIconColor(ContextCompat.getColor(activity, android.R.color.white));

        // Set right icon
        Drawable rightIcon = ContextCompat.getDrawable(activity, R.drawable.ic_snackbar_done); // Replace with your drawable resource
        customSnackBar.setRightIcon(rightIcon);
//        customSnackBar.setRightIconColor(ContextCompat.getColor(activity, android.R.color.white));

        // Set background color and corner radius
//        customSnackBar.setBackgroundColor(R.color.connect_blue_color);
        customSnackBar.setCornerRadius(16);

        // Set click listeners for icons
        customSnackBar.setLeftIconClickListener(view -> {
            // Handle left icon click event
        });

        customSnackBar.setRightIconClickListener(view -> {
            // Handle right icon click event
        });

        // Show the Snackbar
        customSnackBar.show();
    }
}
