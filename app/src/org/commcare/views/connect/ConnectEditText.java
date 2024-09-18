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
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.commcare.dalvik.R;

public class ConnectEditText extends AppCompatEditText {

    private static final int DEFAULT_BACKGROUND_COLOR = R.color.white;
    private static final double DEFAULT_BORDER_WIDTH = 1.0;
    private static final double DEFAULT_CORNER_RADIUS = 5.0;
    private static final int DEFAULT_BORDER_COLOR = R.color.connect_light_grey;
    private static final int DEFAULT_HINT_COLOR = Color.BLACK;
    private static final int DEFAULT_TINT_COLOR = R.color.connect_light_grey;
    private static final int DEFAULT_ERROR_COLOR = R.color.connect_error_color;
    private static final float DEFAULT_HINT_SIZE = 6f;
    private static final float DEFAULT_TEXT_SIZE = 6f;
    private static final int DEFAULT_FONT_RES_ID = R.font.roboto_regular;
    // Global variables to store attribute values
    private boolean drawableStartVisible = false, drawableEndVisible = false;
    private int borderWidth;
    private int cornerRadius;
    private int drawableTintColor;
    private int drawableStartPaddingLeft;
    private int drawableEndPaddingRight;
    private int drawableEndPadding;

    private Drawable drawableStart;
    private Drawable drawableEnd;

    private int paddingTop;
    private int paddingBottom;
    private int paddingStart;
    private int paddingEnd;

    private GradientDrawable backgroundDrawable;

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
        backgroundDrawable = new GradientDrawable();

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.CustomEditText,
                    0, 0);

            try {
                // Initialize global variables with attribute values
                borderWidth = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextBorderWidth, dpToPx(DEFAULT_BORDER_WIDTH));
                cornerRadius = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextCornerRadius, dpToPx(DEFAULT_CORNER_RADIUS));
                int borderColor = a.getColor(R.styleable.CustomEditText_editTextBorderColor, ContextCompat.getColor(getContext(), DEFAULT_BORDER_COLOR));
                int hintColor = a.getColor(R.styleable.CustomEditText_editTextHintColor, DEFAULT_HINT_COLOR);
                float hintSize = a.getDimension(R.styleable.CustomEditText_editTextHintSize, spToPx(DEFAULT_HINT_SIZE));
                boolean isEditable = a.getBoolean(R.styleable.CustomEditText_editTextEditable, true);
                drawableStart = a.getDrawable(R.styleable.CustomEditText_editTextDrawableStart);
                drawableEnd = a.getDrawable(R.styleable.CustomEditText_editTextDrawableEnd);
                drawableStartVisible = a.getBoolean(R.styleable.CustomEditText_editTextDrawableStartVisible, false);
                drawableEndVisible = a.getBoolean(R.styleable.CustomEditText_editTextDrawableEndVisible, false);
                drawableTintColor = a.getColor(R.styleable.CustomEditText_editTextDrawableTint, ContextCompat.getColor(getContext(), DEFAULT_TINT_COLOR));

                drawableStartPaddingLeft = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextDrawableStartPaddingLeft, dpToPx(8));
                drawableEndPaddingRight = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextDrawableEndPaddingRight, dpToPx(14));
                drawableEndPadding = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextDrawablePadding, dpToPx(8));

                // Padding attributes
                paddingTop = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingTop, dpToPx(20));
                paddingBottom = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingBottom, dpToPx(20));
                paddingStart = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingStart, dpToPx(10));
                paddingEnd = a.getDimensionPixelSize(R.styleable.CustomEditText_editTextPaddingEnd, dpToPx(0));
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
                        dpToPx(20),
                        dpToPx(20),
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
        backgroundDrawable.setCornerRadius(cornerRadius);
        backgroundDrawable.setStroke(borderWidth, borderColor);
        setBackground(backgroundDrawable);
    }

    private void setHintAttributes(int color, float size) {
        setHintTextColor(color);
        setTextSize(size);
    }

    public void showErrorState() {
        int errorColor = ContextCompat.getColor(getContext(), DEFAULT_ERROR_COLOR);
        backgroundDrawable.setStroke(borderWidth, errorColor);
        backgroundDrawable.setColor(ContextCompat.getColor(getContext(), DEFAULT_BACKGROUND_COLOR));
        setTextColor(errorColor);
        setHintTextColor(errorColor);
        setDrawables(
                drawableStart,
                drawableEnd,
                drawableStartVisible,
                drawableEndVisible,
                dpToPx(20),
                dpToPx(20),
                errorColor,
                drawableStartPaddingLeft,
                drawableEndPaddingRight,
                drawableEndPadding
        );
        setBackground(backgroundDrawable);
    }

    public void setNormalBorder(){
        int borderColor = ContextCompat.getColor(getContext(), DEFAULT_BORDER_COLOR);
        backgroundDrawable.setStroke(borderWidth, borderColor);
        backgroundDrawable.setColor(ContextCompat.getColor(getContext(), DEFAULT_BACKGROUND_COLOR));
        setTextColor(DEFAULT_HINT_COLOR);
        setHintTextColor(DEFAULT_HINT_COLOR);
        setDrawables(
                drawableStart,
                drawableEnd,
                drawableStartVisible,
                drawableEndVisible,
                dpToPx(20),
                dpToPx(20),
                borderColor,
                drawableStartPaddingLeft,
                drawableEndPaddingRight,
                drawableEndPadding
        );
        setBackground(backgroundDrawable);
    }

    public void setGreyBackground(){
        int borderColor = ContextCompat.getColor(getContext(), R.color.connect_light_grey);
        backgroundDrawable.setStroke(borderWidth, borderColor);
        backgroundDrawable.setColor(borderColor);
        setTextColor(DEFAULT_HINT_COLOR);
        setHintTextColor(DEFAULT_HINT_COLOR);
        setDrawables(
                drawableStart,
                drawableEnd,
                drawableStartVisible,
                drawableEndVisible,
                dpToPx(20),
                dpToPx(20),
                DEFAULT_HINT_COLOR,
                drawableStartPaddingLeft,
                drawableEndPaddingRight,
                drawableEndPadding
        );
        setBackground(backgroundDrawable);
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
            Drawable[] drawables = getCompoundDrawables();
            Drawable drawableStart = drawables[0];
            Drawable drawableEnd = drawables[2];

            if (drawableStartVisible && drawableStart != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getX() <= drawableStart.getBounds().width()) {
                    if (onDrawableStartClickListener != null) {
                        onDrawableStartClickListener.onDrawableStartClick();
                    }
                    return true;
                }
            }

            if (drawableEndVisible && drawableEnd != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getX() >= (getWidth() - getPaddingRight() - drawableEnd.getBounds().width())) {
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
