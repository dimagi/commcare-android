package org.commcare.views.connect;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.commcare.dalvik.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

public class ConnectEditText extends AppCompatEditText {

    private static final double DEFAULT_BORDER_WIDTH = 1.0;
    private static final double DEFAULT_CORNER_RADIUS = 5.0;
    private static final int DEFAULT_BORDER_COLOR = R.color.connect_light_grey;
    private static final int DEFAULT_HINT_COLOR = Color.BLACK;
    private static final int DEFAULT_TINT_COLOR = R.color.connect_light_grey;
    private static final float DEFAULT_HINT_SIZE = 7f;
    private static final float DEFAULT_TEXT_SIZE = 7f;
    private static final int DEFAULT_FONT_RES_ID = R.font.roboto_regular;
    private boolean drawableStartVisible = false, drawableEndVisible = false;
    private OnDrawableStartClickListener onDrawableStartClickListener;
    private OnDrawableEndClickListener onDrawableEndClickListener;

    public ConnectEditText(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public ConnectEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ConnectEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.CustomEditText,
                    0, 0);

            try {
                int borderWidth = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextBorderWidth, dpToPx(DEFAULT_BORDER_WIDTH));
                int cornerRadius = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextCornerRadius, dpToPx(DEFAULT_CORNER_RADIUS));
                int borderColor = a.getColor(R.styleable.CustomEditText_editTextBorderColor, ContextCompat.getColor(getContext(), DEFAULT_BORDER_COLOR));
                int hintColor = a.getColor(R.styleable.CustomEditText_editTextHintColor, DEFAULT_HINT_COLOR);
                float hintSize = a.getDimension(R.styleable.CustomEditText_editTextHintSize, spToPx(DEFAULT_HINT_SIZE));
                boolean isEditable = a.getBoolean(R.styleable.CustomEditText_editTextEditable, true);
                Drawable drawableStart = a.getDrawable(R.styleable.CustomEditText_editTextDrawableStart);
                Drawable drawableEnd = a.getDrawable(R.styleable.CustomEditText_editTextDrawableEnd);
                drawableStartVisible = a.getBoolean(R.styleable.CustomEditText_editTextDrawableStartVisible, false);
                drawableEndVisible = a.getBoolean(R.styleable.CustomEditText_editTextDrawableEndVisible, false);
                int drawableTintColor = a.getColor(R.styleable.CustomEditText_editTextDrawableTint, ContextCompat.getColor(getContext(), DEFAULT_TINT_COLOR));

                int drawableStartPaddingLeft = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextDrawableStartPaddingLeft, dpToPx(15));
                int drawableEndPaddingRight = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextDrawableEndPaddingRight, dpToPx(15));
                int drawableEndPadding = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextDrawablePadding, dpToPx(12));

                // New padding attribute handling
                int paddingTop = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingTop, dpToPx(20));
                int paddingBottom = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingBottom, dpToPx(20));
                int paddingStart = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingStart, dpToPx(10));
                int paddingEnd = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingEnd, dpToPx(0));
                int fontFamily = a.getResourceId(R.styleable.CustomEditText_editTextFontFamily, DEFAULT_FONT_RES_ID);

                // New attributes for text, textSize, hint, and hintSize
                String text = a.getString(R.styleable.CustomEditText_editTextText);
                if (text != null) {
                    setText(text);
                }

                float textSize = a.getDimension(R.styleable.CustomEditText_editTextTextSize, spToPx(DEFAULT_TEXT_SIZE));
                setTextSize(textSize);

                String hint = a.getString(R.styleable.CustomEditText_editTextHint);
                if (hint != null) {
                    setHint(hint);
                }

                float hintSizeNew = a.getDimension(R.styleable.CustomEditText_editTextHintSize, hintSize);
                setHintTextSize(hintSizeNew);

                setBorder(borderWidth, cornerRadius, borderColor);
                setHintAttributes(hintColor, hintSize);
                setEditable(isEditable);

                setDrawables(
                        drawableStart,
                        drawableEnd,
                        drawableStartVisible,
                        drawableEndVisible,
                        dpToPx(25),
                        dpToPx(25),
                        drawableTintColor,
                        drawableStartPaddingLeft,
                        drawableEndPaddingRight,
                        drawableEndPadding
                );

                setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom);

                setFontFamily(fontFamily);
            } finally {
                a.recycle();
            }
        } else {
            setBorder(dpToPx(DEFAULT_BORDER_WIDTH), dpToPx(DEFAULT_CORNER_RADIUS), DEFAULT_BORDER_COLOR);
            setHintAttributes(DEFAULT_HINT_COLOR, spToPx(DEFAULT_HINT_SIZE));
            setEditable(true);
        }

        setupDrawableClickListeners();
    }

    private void setFontFamily(int fontResId) {
        Typeface typeface = ResourcesCompat.getFont(getContext(), fontResId);
        setTypeface(typeface);
    }

    // New method to set hint size directly
    public void setHintTextSize(float size) {
        setTextSize(size);
    }

    private void setBorder(int borderWidth, int cornerRadius, int borderColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(cornerRadius);
        drawable.setStroke(borderWidth, borderColor);
        setBackground(drawable);
    }

    private void setHintAttributes(int color, float size) {
        setHintTextColor(color);
        setTextSize(size);
    }

    public void setEditable(boolean isEditable) {
        setFocusable(isEditable);
        setFocusableInTouchMode(isEditable);
        setInputType(isEditable ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_NULL);
    }

    private void setDrawables(
            Drawable drawableStart,
            Drawable drawableEnd,
            boolean drawableStartVisible,
            boolean drawableEndVisible,
            int drawableWidth,
            int drawableHeight,
            int tintColor,
            int paddingLeft,
            int paddingRight,
            int drawablePadding
    ) {
        if (drawableStart != null && drawableStartVisible) {
            drawableStart.setBounds(paddingLeft, 0, drawableWidth + paddingLeft, drawableHeight);
            drawableStart.setTint(tintColor);
        }

        if (drawableEnd != null && drawableEndVisible) {
            drawableEnd.setBounds(-paddingRight, 0, drawableWidth - paddingRight, drawableHeight);
            drawableEnd.setTint(tintColor);
        }

        setCompoundDrawables(drawableStartVisible ? drawableStart : null, null, drawableEndVisible ? drawableEnd : null, null);

        setCompoundDrawablePadding(dpToPx(drawablePadding));
    }


    @SuppressLint("ClickableViewAccessibility")
    private void setupDrawableClickListeners() {
        setOnTouchListener((v, event) -> {
            if (drawableStartVisible && event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getX() <= getCompoundDrawables()[0].getBounds().width()) {
                    if (onDrawableStartClickListener != null) {
                        onDrawableStartClickListener.onDrawableStartClick();
                    }
                    return true;
                }
            }

            if (drawableEndVisible && event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getX() >= (getWidth() - getPaddingRight() - getCompoundDrawables()[2].getBounds().width())) {
                    if (onDrawableEndClickListener != null) {
                        onDrawableEndClickListener.onDrawableEndClick();
                    }
                    return true;
                }
            }

            return false;
        });
    }

    public interface OnDrawableStartClickListener {
        void onDrawableStartClick();
    }

    public interface OnDrawableEndClickListener {
        void onDrawableEndClick();
    }

    public void setOnDrawableStartClickListener(OnDrawableStartClickListener listener) {
        this.onDrawableStartClickListener = listener;
    }

    public void setOnDrawableEndClickListener(OnDrawableEndClickListener listener) {
        this.onDrawableEndClickListener = listener;
    }

    private int dpToPx(double dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) Math.round(dp * density);
    }

    private float spToPx(float sp) {
        float scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        return sp * scaledDensity;
    }
}
