package org.commcare.views.connect;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.commcare.dalvik.R;

/**
 * CustomOtpView is a customizable OTP input field view that consists of multiple EditText fields.
 * Users can configure the number of digits, border properties, text color, and other attributes via XML.
 */
public class CustomOtpView extends LinearLayout {

    private int digitCount;
    private int borderColor;
    private int errorBorderColor;
    private int borderRadius;
    private int borderWidth;
    private int textColor;
    private int errorTextColor;
    private float textSize;
    private OtpCompleteListener otpCompleteListener;
    private OnOtpChangedListener otpChangedListener;
    private boolean isErrorState = false;

    /**
     * Constructor for programmatic instantiation.
     */
    public CustomOtpView(Context context) {
        super(context);
        init(null);
    }

    /**
     * Constructor used when inflating from XML.
     */
    public CustomOtpView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    /**
     * Initializes the view and reads XML attributes if provided.
     */
    private void init(AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Set default values
        digitCount = 4;
        borderColor = Color.BLACK;
        errorBorderColor = Color.RED;
        borderRadius = 5;
        borderWidth = 2;
        textColor = Color.BLACK;
        errorTextColor = Color.RED;
        textSize = 14;

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

    /**
     * Creates the OTP input fields dynamically based on the digit count.
     */
    private void createOtpFields() {
        removeAllViews();
        for (int i = 0; i < digitCount; i++) {
            EditText otpEditText = createOtpEditText(i);
            addView(otpEditText);
        }
    }

    /**
     * Creates an individual EditText for OTP input.
     */
    private EditText createOtpEditText(int index) {
        EditText editText = new EditText(getContext());
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(8, 8, 8, 8);
        editText.setLayoutParams(params);
        editText.setGravity(Gravity.CENTER);
        editText.setTextColor(isErrorState ? errorTextColor : textColor);
        editText.setTextSize(textSize);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setBackground(createBackgroundDrawable());
        editText.setId(index);
        return editText;
    }

    /**
     * Retrieves the entered OTP as a string.
     */
    public String getOtpValue() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            otp.append(editText.getText().toString());
        }
        return otp.toString();
    }

    /**
     * Creates a background drawable for OTP fields.
     */
    private GradientDrawable createBackgroundDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(borderRadius);
        drawable.setStroke(borderWidth, isErrorState ? errorBorderColor : borderColor);
        return drawable;
    }

    /**
     * Sets an OTP completion listener.
     */
    public void setOtpCompleteListener(OtpCompleteListener listener) {
        this.otpCompleteListener = listener;
    }

    /**
     * Sets an OTP changed listener.
     */
    public void setOnOtpChangedListener(OnOtpChangedListener listener) {
        this.otpChangedListener = listener;
    }
    /**
     * Sets the error in the view make the view red in case of error.
     * Dont forgot to set it to false when the error is resolved
     */
    public void setErrorState(boolean isError) {
        this.isErrorState = isError;
        post(this::updateUi);  // Ensure UI updates happen on the main thread
    }

    private void updateUi() {
        for (int i = 0; i < getChildCount(); i++) {
            EditText editText = (EditText) getChildAt(i);
            if (editText != null) {
                editText.setTextColor(isErrorState ? errorTextColor : textColor);
                editText.setBackground(createBackgroundDrawable());
            }
        }
    }

    /**
     * Sets the OTP in the fields programmatically.
     */
    public void setOtp(String otp) {
        if (otp.length() > digitCount) {
            throw new IllegalArgumentException("OTP length exceeds the digit count");
        }
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            if (i < otp.length()) {
                editText.setText(String.valueOf(otp.charAt(i)));
            } else {
                editText.setText("");
            }
            editText.clearFocus();
        }
    }

    /**
     * Interface for OTP completion event.
     */
    public interface OtpCompleteListener {
        void onOtpComplete(String otp);
    }

    /**
     * Interface for OTP changed event.
     */
    public interface OnOtpChangedListener {
        void onOtpChanged(String otp);
    }
}
