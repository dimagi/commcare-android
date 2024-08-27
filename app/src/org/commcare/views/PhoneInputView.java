package org.commcare.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.commcare.dalvik.R;

public class PhoneInputView extends LinearLayout {

    private TextView tvCountryCode;
    private View dividerView;
    private EditText etPhoneNumber;
    private GradientDrawable backgroundDrawable;

    public PhoneInputView(Context context) {
        super(context);
        init(context, null);
    }

    public PhoneInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PhoneInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_country_code_edittext, this, true);

        // Initialize views
        tvCountryCode = findViewById(R.id.tvCountryCode);
        dividerView = findViewById(R.id.dividerView);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);

        // Default background drawable
        backgroundDrawable = new GradientDrawable();

        // Retrieve and set custom attributes
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PhoneInputView);

            // Border attributes
            boolean borderVisible = a.getBoolean(R.styleable.PhoneInputView_borderVisible, true);
            int borderColor = a.getColor(R.styleable.PhoneInputView_borderColor, ContextCompat.getColor(context, R.color.connect_border_color));
            float borderRadius = a.getDimension(R.styleable.PhoneInputView_borderRadius, dpToPx(5));
            if (borderVisible) {
                backgroundDrawable.setStroke(dpToPx(1), borderColor);
            }
            backgroundDrawable.setCornerRadius(borderRadius);

            // Country code text attributes
            int countryCodeTextColor = a.getColor(R.styleable.PhoneInputView_countryCodeTextColor, ContextCompat.getColor(context, R.color.black));
            float countryCodeTextSize = a.getDimension(R.styleable.PhoneInputView_countryCodeTextSize, spToPx(16));
            tvCountryCode.setTextColor(countryCodeTextColor);
            tvCountryCode.setTextSize(TypedValue.COMPLEX_UNIT_PX, countryCodeTextSize);

            // EditText attributes
            int editTextHintColor = a.getColor(R.styleable.PhoneInputView_editTextHintColor, ContextCompat.getColor(context, R.color.connect_light_grey));
            float editTextHintSize = a.getDimension(R.styleable.PhoneInputView_editTextHintSize, spToPx(16));
            int editTextColor = a.getColor(R.styleable.PhoneInputView_editTextColor, ContextCompat.getColor(context, R.color.black));
            float editTextSize = a.getDimension(R.styleable.PhoneInputView_editTextSize, spToPx(16));
            etPhoneNumber.setHintTextColor(editTextHintColor);
            etPhoneNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, editTextHintSize);
            etPhoneNumber.setTextColor(editTextColor);
            etPhoneNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, editTextSize);

            // Divider attributes
            boolean dividerVisible = a.getBoolean(R.styleable.PhoneInputView_dividerVisible, true);
            int dividerColor = a.getColor(R.styleable.PhoneInputView_dividerColor, ContextCompat.getColor(context, R.color.connect_light_grey));
            float dividerWidth = a.getDimension(R.styleable.PhoneInputView_dividerWidth, dpToPx(1));
            float dividerLeftPadding = a.getDimension(R.styleable.PhoneInputView_dividerLeftPadding, dpToPx(10));
            float dividerRightPadding = a.getDimension(R.styleable.PhoneInputView_dividerRightPadding, dpToPx(10));

            if (dividerVisible) {
                dividerView.setVisibility(View.VISIBLE);
                dividerView.setBackgroundColor(dividerColor);

                LayoutParams params = (LayoutParams) dividerView.getLayoutParams();
                params.width = (int) dividerWidth;
                params.setMargins((int) dividerLeftPadding, 0, (int) dividerRightPadding, 0); // Set margins
                dividerView.setLayoutParams(params);
            } else {
                dividerView.setVisibility(View.GONE);
            }

            a.recycle();
        }

        // Apply the background drawable with border and radius
        setBackground(backgroundDrawable);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    // Method to set a TextWatcher on the EditText
    public void setOnEditTextChangedListener(TextWatcher textWatcher) {
        etPhoneNumber.addTextChangedListener(textWatcher);
    }

    // Method to get the current value of the EditText
    public String getEditTextValue() {
        return etPhoneNumber.getText().toString();
    }

    // Method to set the value of the EditText
    public void setEditTextValue(String value) {
        etPhoneNumber.setText(value);
    }

    // Method to set the value of the Country Code TextView
    public void setCountryCodeValue(String value) {
        tvCountryCode.setText(value);
    }

    // Method to get the current value of the Country Code TextView
    public String getCountryCodeValue() {
        return tvCountryCode.getText().toString();
    }

    // Method to set an OnFocusChangeListener on the EditText
    public void setOnEditTextFocusChangeListener(View.OnFocusChangeListener listener) {
        etPhoneNumber.setOnFocusChangeListener(listener);
    }
}
