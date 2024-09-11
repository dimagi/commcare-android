package org.commcare.utils;

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

    public static void showErrorSnackBar(Context context, View anchorView) {
        // Create CustomSnackBar instance
        CustomSnackBar customSnackBar = new CustomSnackBar(context, anchorView);

        // Set text properties
        customSnackBar.setText("This is a custom Snackbar!");
        customSnackBar.setTextColor(Color.WHITE);
        customSnackBar.setTextSize(16f);

        // Set font (ensure you have a valid font resource)
        customSnackBar.setTextFont(R.font.roboto_medium);

        // Set left icon properties
        Drawable leftIcon = ContextCompat.getDrawable(context, R.drawable.ic_connect_delivery);
        customSnackBar.setLeftIcon(leftIcon);
        customSnackBar.setLeftIconSize(48, 48);
        customSnackBar.setLeftIconColor(Color.YELLOW);
        customSnackBar.setLeftIconClickListener(v -> {
            Toast.makeText(context, "Left icon clicked", Toast.LENGTH_SHORT).show();
        });

        // Set right icon properties
        Drawable rightIcon = ContextCompat.getDrawable(context, R.drawable.ic_connect_delivery);
        customSnackBar.setRightIcon(rightIcon);
        customSnackBar.setRightIconSize(48, 48);
        customSnackBar.setRightIconColor(Color.GREEN);
        customSnackBar.setRightIconClickListener(v -> {
            Toast.makeText(context, "Right icon clicked", Toast.LENGTH_SHORT).show();
        });

        // Set background color
        customSnackBar.setBackgroundColor(Color.BLACK);

        // Set corner radius
        customSnackBar.setCornerRadius(16);

        // Set layout parameters
        customSnackBar.setCustomViewLayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                16, 16, 16, 16
        );

        // Show the Snackbar
        customSnackBar.show();
    }
}
