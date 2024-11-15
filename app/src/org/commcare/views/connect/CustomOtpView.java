package org.commcare.views.connect;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.commcare.dalvik.R;

public class CustomOtpView extends LinearLayout {

    private int digitCount = 4;
    private int borderColor = Color.BLACK;
    private int errorBorderColor = Color.RED;
    private int borderRadius = 5;
    private int borderWidth = 2;
    private int textColor = Color.BLACK;
    private int errorTextColor = Color.RED;
    private float textSize = 10;
    private OtpCompleteListener otpCompleteListener;
    private OnOtpChangedListener otpChangedListener;
    private boolean isErrorState = false;

    public CustomOtpView(Context context) {
        super(context);
        init(null);
    }

    public CustomOtpView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Read attributes from XML
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.CustomOtpView);
            digitCount = typedArray.getInt(R.styleable.CustomOtpView_otpViewDigitCount, digitCount);
            borderColor = typedArray.getColor(R.styleable.CustomOtpView_otpViewBorderColor, borderColor);
            errorBorderColor = typedArray.getColor(R.styleable.CustomOtpView_otpViewErrorBorderColor, errorBorderColor);
            borderRadius = typedArray.getDimensionPixelSize(R.styleable.CustomOtpView_otpViewBorderRadius, borderRadius);
            borderWidth = typedArray.getDimensionPixelSize(R.styleable.CustomOtpView_otpViewBorderWidth, borderWidth);
            textColor = typedArray.getColor(R.styleable.CustomOtpView_otpViewTextColor, textColor);
            errorTextColor = typedArray.getColor(R.styleable.CustomOtpView_otpViewErrorTextColor, errorTextColor);
            textSize = typedArray.getDimension(R.styleable.CustomOtpView_otpViewTextSize, textSize);
            typedArray.recycle();
        }

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
        editText.setTextColor(isErrorState ? errorTextColor : textColor);
        editText.setTextSize(textSize);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setBackground(createBackgroundDrawable());
        editText.setId(index);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && index > 0 && ((EditText) getChildAt(index)).getText().length() == 0) {
                // Move to the first empty edit text if it's focused and empty
                for (int i = 0; i < digitCount; i++) {
                    if (((EditText) getChildAt(i)).getText().length() == 0) {
                        getChildAt(i).requestFocus();
                        break;
                    }
                }
            }
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) {
                    // Automatically move to the next EditText if current is filled
                    if (index < digitCount - 1) {
                        getChildAt(index + 1).requestFocus();
                    } else {
                        checkOtpCompletion();
                    }
                    // Notify listener whenever OTP changes
                    if (otpChangedListener != null) {
                        Log.e("DEBUG_TESTING", "otpChangedListener: called"+ s);

                        String otp = getOtpValue();
                        otpChangedListener.onOtpChanged(otp);
                    }else {
                        Log.e("DEBUG_TESTING", "otpChangedListener: not called"+ s);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // If backspace is pressed, focus on previous EditText if the current is empty
                if (s.length() == 0 && index > 0) {
                    getChildAt(index - 1).requestFocus();
                }
            }
        });

        return editText;
    }

    private String getOtpValue() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            otp.append(editText.getText().toString());
        }
        return otp.toString();
    }

    private GradientDrawable createBackgroundDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(borderRadius);
        drawable.setStroke(borderWidth, isErrorState ? errorBorderColor : borderColor);
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
    public void setOtpCompleteListener(OtpCompleteListener listener) {
        this.otpCompleteListener = listener;
    }

    public void setOnOtpChangedListener(OnOtpChangedListener listener) {
        this.otpChangedListener = listener;
    }

    public void setErrorState(boolean isError) {
        this.isErrorState = isError;
        updateUi();
    }

    private void updateUi() {
        for (int i = 0; i < getChildCount(); i++) {
            EditText editText = (EditText) getChildAt(i);
            editText.setTextColor(isErrorState ? errorTextColor : textColor);
            editText.setBackground(createBackgroundDrawable());
        }
    }

    public interface OtpCompleteListener {
        void onOtpComplete(String otp);
    }

    public interface OnOtpChangedListener {
        void onOtpChanged(String otp);
    }
}
