package org.commcare.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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
            int borderColor = a.getColor(R.styleable.PhoneInputView_borderColor, R.color.);
            float borderRadius = a.getDimension(R.styleable.PhoneInputView_borderRadius, dpToPx(5));
            if (borderVisible) {
                backgroundDrawable.setStroke(dpToPx(1), borderColor);
            }
            backgroundDrawable.setCornerRadius(borderRadius);

            // Country code text attributes
            int countryCodeTextColor = a.getColor(R.styleable.PhoneInputView_countryCodeTextColor, Color.BLACK);
            float countryCodeTextSize = a.getDimension(R.styleable.PhoneInputView_countryCodeTextSize, spToPx(16));
            tvCountryCode.setTextColor(countryCodeTextColor);
            tvCountryCode.setTextSize(TypedValue.COMPLEX_UNIT_PX, countryCodeTextSize);

            // EditText attributes
            int editTextHintColor = a.getColor(R.styleable.PhoneInputView_editTextHintColor, Color.GRAY);
            float editTextHintSize = a.getDimension(R.styleable.PhoneInputView_editTextHintSize, spToPx(16));
            int editTextColor = a.getColor(R.styleable.PhoneInputView_editTextColor, Color.BLACK);
            float editTextSize = a.getDimension(R.styleable.PhoneInputView_editTextSize, spToPx(16));
            etPhoneNumber.setHintTextColor(editTextHintColor);
            etPhoneNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, editTextHintSize);
            etPhoneNumber.setTextColor(editTextColor);
            etPhoneNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, editTextSize);

            // Divider attributes
            boolean dividerVisible = a.getBoolean(R.styleable.PhoneInputView_dividerVisible, true);
            int dividerColor = a.getColor(R.styleable.PhoneInputView_dividerColor, Color.GRAY);
            float dividerWidth = a.getDimension(R.styleable.PhoneInputView_dividerWidth, dpToPx(1));
            float dividerLeftPadding = a.getDimension(R.styleable.PhoneInputView_dividerLeftPadding, dpToPx(8));
            float dividerRightPadding = a.getDimension(R.styleable.PhoneInputView_dividerRightPadding, dpToPx(8));

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

    // Add getter and setter methods if needed
}
