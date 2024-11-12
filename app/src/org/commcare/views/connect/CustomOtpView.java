package org.commcare.views.connect;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;

public class CustomOtpView extends LinearLayout {

    private int digitCount = 4;
    private int borderColor = Color.BLACK;
    private int borderRadius = 5;
    private int borderWidth = 2;
    private int textColor = Color.BLACK;
    private int textSize = 18;
    private OtpCompleteListener otpCompleteListener;

    public CustomOtpView(Context context) {
        super(context);
        init();
    }

    public CustomOtpView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        createOtpFields();
    }

    private void createOtpFields() {
        removeAllViews();
        for (int i = 0; i < digitCount; i++) {
            EditText otpEditText = createOtpEditText(i);
            addView(otpEditText);
        }
    }

    private EditText createOtpEditText(int index) {
        EditText editText = new EditText(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1.0f
        );
        params.setMargins(8, 8, 8, 8);
        editText.setLayoutParams(params);
        editText.setGravity(Gravity.CENTER);
        editText.setTextColor(textColor);
        editText.setTextSize(textSize);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setBackground(createBackgroundDrawable());
        editText.setId(index);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) {
                    if (index < digitCount - 1) {
                        getChildAt(index + 1).requestFocus();
                    } else {
                        checkOtpCompletion();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0 && index > 0) {
                    getChildAt(index - 1).requestFocus();
                }
            }
        });

        return editText;
    }

    private GradientDrawable createBackgroundDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(borderRadius);
        drawable.setStroke(borderWidth, borderColor);
        return drawable;
    }

    private void checkOtpCompletion() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            otp.append(editText.getText().toString());
        }
        if (otp.length() == digitCount && otpCompleteListener != null) {
            otpCompleteListener.onOtpComplete(otp.toString());
        }
    }

    // Public methods for customization
    public void setDigitCount(int count) {
        this.digitCount = count;
        createOtpFields();
    }

    public void setBorderColor(int color) {
        this.borderColor = color;
        updateUi();
    }

    public void setBorderRadius(int radius) {
        this.borderRadius = radius;
        updateUi();
    }

    public void setBorderWidth(int width) {
        this.borderWidth = width;
        updateUi();
    }

    public void setTextColor(int color) {
        this.textColor = color;
        updateUi();
    }

    public void setTextSize(int size) {
        this.textSize = size;
        updateUi();
    }

    public void setOtpCompleteListener(OtpCompleteListener listener) {
        this.otpCompleteListener = listener;
    }

    private void updateUi() {
        for (int i = 0; i < getChildCount(); i++) {
            EditText editText = (EditText) getChildAt(i);
            editText.setTextColor(textColor);
            editText.setTextSize(textSize);
            editText.setBackground(createBackgroundDrawable());
        }
    }

    public interface OtpCompleteListener {
        void onOtpComplete(String otp);
    }
}
