package org.commcare.views.connect;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.commcare.dalvik.R;

public class RoundedButton extends androidx.appcompat.widget.AppCompatButton {

    private static final int DEFAULT_BUTTON_HEIGHT = 100;
    private static final int DEFAULT_RADIUS = 30;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXT_COLOR = Color.BLACK;
    private static final int DEFAULT_ICON_TINT_COLOR = Color.WHITE;
    private static final int DEFAULT_PADDING = 24;
    private static final boolean DEFAULT_ICON_LEFT_ALIGN = false;
    private static final int DEFAULT_FONT_RES_ID = R.font.roboto_regular;
    private static final boolean DEFAULT_BORDER_VISIBLE = false;
    private static final int DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final int DEFAULT_BORDER_RADIUS = DEFAULT_RADIUS;
    private static final int DEFAULT_BORDER_WIDTH = 1;
    private static final int DEFAULT_TEXT_SIZE = 7;

    public RoundedButton(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public RoundedButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RoundedButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.RoundedButton,
                    0, 0);

            try {
                int buttonHeight = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonHeight, DEFAULT_BUTTON_HEIGHT);
                int radius = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonRadius, dpToPx(DEFAULT_RADIUS));
                int backgroundColor = a.getColor(R.styleable.RoundedButton_roundButtonBackgroundColor, DEFAULT_BACKGROUND_COLOR);
                int textColor = a.getColor(R.styleable.RoundedButton_roundButtonTextColor, DEFAULT_TEXT_COLOR);
                int iconTintColor = a.getColor(R.styleable.RoundedButton_roundButtonIconTintColor, DEFAULT_ICON_TINT_COLOR);
                int leftPadding = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonLeftPadding, dpToPx(DEFAULT_PADDING));
                int rightPadding = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonRightPadding, dpToPx(DEFAULT_PADDING));
                boolean iconLeftAlign = a.getBoolean(R.styleable.RoundedButton_roundButtonIconLeftAlign, DEFAULT_ICON_LEFT_ALIGN);
                int fontFamily = a.getResourceId(R.styleable.RoundedButton_roundButtonFontFamily, DEFAULT_FONT_RES_ID);
                boolean borderVisible = a.getBoolean(R.styleable.RoundedButton_roundButtonBorderVisible, DEFAULT_BORDER_VISIBLE);
                int borderColor = a.getColor(R.styleable.RoundedButton_roundButtonBorderColor, DEFAULT_BORDER_COLOR);
                int borderRadius = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonBorderRadius, dpToPx(DEFAULT_BORDER_RADIUS));
                int borderWidth = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonBorderWidth, dpToPx(DEFAULT_BORDER_WIDTH));
                float textSize = a.getDimension(R.styleable.RoundedButton_roundButtonTextSize, spToPx(DEFAULT_TEXT_SIZE));
                Drawable icon = a.getDrawable(R.styleable.RoundedButton_roundButtonIcon);

                int iconPadding = a.getDimensionPixelSize(R.styleable.RoundedButton_roundButtonIconPadding, dpToPx(16));
                setCompoundDrawablePadding(iconPadding);

                setBackgroundDrawable(radius, backgroundColor, borderVisible, borderColor, borderRadius,borderWidth);
                setTextColor(textColor);
                setButtonHeight(buttonHeight);
                setPadding(leftPadding, 0, rightPadding, 0);
                setFontFamily(fontFamily);
                setTextSize(textSize);
                if (icon != null) {
                    setIcon(icon, iconTintColor, iconLeftAlign);
                }

            } finally {
                a.recycle();
            }
        } else {
            setBackgroundDrawable(dpToPx(DEFAULT_RADIUS), DEFAULT_BACKGROUND_COLOR, DEFAULT_BORDER_VISIBLE, DEFAULT_BORDER_COLOR, dpToPx(DEFAULT_BORDER_RADIUS), dpToPx(DEFAULT_BORDER_WIDTH));
            setTextColor(DEFAULT_TEXT_COLOR);
            setPadding(dpToPx(DEFAULT_PADDING), 0, dpToPx(DEFAULT_PADDING), 0);
            setTextSize(spToPx(DEFAULT_TEXT_SIZE));
        }

        setAllCaps(false);

    }

    private float spToPx(float sp) {
        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        return sp * scaledDensity;
    }

    public void setTextSize(float textSizeInSp) {
        super.setTextSize(textSizeInSp);
    }

    private void setBackgroundDrawable(int radius, int backgroundColor, boolean borderVisible, int borderColor, int borderRadius,int borderWidth) {
        RoundedButtonDrawable drawable = new RoundedButtonDrawable(radius, backgroundColor, borderVisible, borderColor, borderRadius,borderWidth);
        setBackground(drawable);
    }

    public void setTextColor(int color) {
        super.setTextColor(color);
    }

    public void setIcon(@DrawableRes int iconResId, int tintColor, boolean isIconLeftSide) {
        Drawable icon = ContextCompat.getDrawable(getContext(), iconResId);
        if (icon != null) {
            icon.setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (isIconLeftSide) {
            setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        } else {
            setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
        }
    }

    public void setIcon(Drawable icon, int tintColor, boolean isIconLeftSide) {
        if (icon != null) {
            icon.setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (isIconLeftSide) {
            setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        } else {
            setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
        }
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public void setButtonHeight(int heightInPx) {
        int height = dpToPx(heightInPx);
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    height
            );
        } else {
            params.height = height;
        }
        if (params instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams constraintParams = (ConstraintLayout.LayoutParams) params;
            constraintParams.height = height;
        }
        setLayoutParams(params);
    }

    // Method to set font family by resource ID
    public void setFontFamily(int fontResId) {
        Typeface typeface = ResourcesCompat.getFont(getContext(), fontResId);
        setTypeface(typeface);
    }
}
