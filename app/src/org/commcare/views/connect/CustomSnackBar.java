package org.commcare.views.connect;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.snackbar.Snackbar;

import org.commcare.dalvik.R;

public class CustomSnackBar {

    private final Snackbar snackbar;
    private final Context context;
    private final View customView;
    Snackbar.SnackbarLayout snackbarLayout;

    @SuppressLint("InflateParams")
    public CustomSnackBar(Context context, View anchorView) {
        this.context = context;
        snackbar = Snackbar.make(anchorView, "", Snackbar.LENGTH_LONG);
        customView = LayoutInflater.from(context).inflate(R.layout.custom_snack_bar, null);
        setupSnackBarView();
    }

    @SuppressLint("RestrictedApi")
    private void setupSnackBarView() {
        snackbar.getView().setBackgroundColor(Color.TRANSPARENT);
        snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
        snackbarLayout.setPadding(10, 10, 10, 10);
        snackbarLayout.addView(customView, 0);
    }

    // Helper method to update TextView properties
    private void updateTextView(TextUpdater updater) {
        if (customView != null) {
            TextView textView = customView.findViewById(R.id.tvMessage);
            if (textView != null) {
                updater.update(textView);
            }
        }
    }

    @FunctionalInterface
    private interface TextUpdater {
        void update(TextView textView);
    }

    // Set text on a TextView
    public void setText(CharSequence text) {
        updateTextView(textView -> textView.setText(text));
    }

    // Set text color on a TextView
    public void setTextColor(int color) {
        updateTextView(textView -> textView.setTextColor(color));
    }

    // Set text size on a TextView
    public void setTextSize(float size) {
        updateTextView(textView -> textView.setTextSize(size));
    }

    // Set text font on a TextView
    public void setTextFont(int fontId) {
        if (customView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TextView textView = customView.findViewById(R.id.tvMessage);
            if (textView != null) {
                textView.setTypeface(context.getResources().getFont(fontId));
            }
        }
    }

    // Helper method to update ImageView properties
    private void updateImageView(int iconId, ImageViewUpdater updater) {
        if (customView != null) {
            ImageView icon = customView.findViewById(iconId);
            if (icon != null) {
                updater.update(icon);
            }
        }
    }

    @FunctionalInterface
    private interface ImageViewUpdater {
        void update(ImageView imageView);
    }

    // Set left icon properties
    public void setLeftIcon(Drawable drawable) {
        ImageView icon = customView.findViewById(R.id.imgLeftIcon);
        if (icon != null) {
            icon.setImageDrawable(drawable);
        }
    }

    public void setLeftIconSize(int width, int height) {
        if (customView != null) {
            ImageView icon = customView.findViewById(R.id.imgLeftIcon);
            if (icon != null) {
                ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(width, height);
                icon.setLayoutParams(params);
            }
        }
    }

    public void setLeftIconColor(int color) {
        if (customView != null) {
            ImageView icon = customView.findViewById(R.id.imgLeftIcon);

            if (icon != null) {
                icon.setColorFilter(color);
            }
        }
    }

    public void setLeftIconClickListener(View.OnClickListener listener) {
        if (customView != null) {
            ImageView icon = customView.findViewById(R.id.imgLeftIcon);

            if (icon != null) {
                icon.setOnClickListener(listener);
            }
        }
    }

    // Set right icon properties
    public void setRightIcon(Drawable drawable) {
        ImageView icon = customView.findViewById(R.id.imgRightIcon);
        if (icon != null) {
            icon.setImageDrawable(drawable);
        }
    }

    public void setRightIconSize(int width, int height) {
        if (customView != null) {
            ImageView icon = customView.findViewById(R.id.imgRightIcon);
            if (icon != null) {
                ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(width, height);
                icon.setLayoutParams(params);
            }
        }
    }

    public void setRightIconColor(int color) {
        if (customView != null) {
            ImageView icon = customView.findViewById(R.id.imgRightIcon);
            if (icon != null) {
                icon.setColorFilter(color);
            }
        }
    }

    public void setRightIconClickListener(View.OnClickListener listener) {
        if (customView != null) {
            ImageView icon = customView.findViewById(R.id.imgRightIcon);

            if (icon != null) {
                icon.setOnClickListener(listener);
            }
        }
    }

    // Set the background color of the custom view
    @SuppressLint("RestrictedApi")
    public void setBackgroundColor(Drawable color) {
        if (customView != null) {
//            GradientDrawable background = new GradientDrawable();
//            background.setColor(color);  // Set background color
//            customView.setBackground(background);
            snackbarLayout.setBackground(color);
        }
    }

    // Set the corner radius of the background
    public void setCornerRadius(int radius) {
        if (customView != null) {
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(radius);
            customView.setBackground(background);
        }
    }

    // Set layout parameters for the custom view
    public void setCustomViewLayoutParams(int width, int height, int marginLeft, int marginTop, int marginRight, int marginBottom) {
        if (customView != null) {
            ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(width, height);
            layoutParams.setMargins(marginLeft, marginTop, marginRight, marginBottom);
            customView.setLayoutParams(layoutParams);
        }
    }

    // Show the Snackbar
    public void show() {
        snackbar.show();
    }
}
