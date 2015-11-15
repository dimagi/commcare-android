package org.commcare.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * Square button with subtext and notification text
 *
 * @author Daniel Luna (dcluna@dimagi.com)
 */
public class SquareButtonWithNotification extends RelativeLayout {
    private SquareButtonWithText buttonWithText;
    private TextView subText;

    public SquareButtonWithNotification(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflateAndExtractCustomParams(context, attrs);
    }

    public SquareButtonWithNotification(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflateAndExtractCustomParams(context, attrs);
    }

    private void inflateAndExtractCustomParams(Context context, AttributeSet attrs) {
        View view = inflate(context, R.layout.square_button_notification, this);

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SquareButtonWithNotification, 0, 0);

        setupButton(view, typedArray);
        setupNotification(view, typedArray);

        typedArray.recycle();
    }

    private void setupButton(View view, TypedArray typedArray) {
        Drawable backgroundImg = typedArray.getDrawable(R.styleable.SquareButtonWithNotification_img);
        int backgroundColor = getResources().getColor(typedArray.getResourceId(R.styleable.SquareButtonWithNotification_backgroundColor, android.R.drawable.btn_default));
        String buttonText = typedArray.getString(R.styleable.SquareButtonWithNotification_subtitle);
        int colorButtonText = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_textColor, R.color.white);

        buttonWithText = (SquareButtonWithText)view.findViewById(R.id.square_button_text);
        buttonWithText.setUI(backgroundColor, backgroundImg, buttonText, colorButtonText);
    }

    private void setupNotification(View view, TypedArray typedArray) {
        int notificationBgColor = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_notificationBackgroundColor, R.color.solid_green);
        String notificationText = typedArray.getString(R.styleable.SquareButtonWithNotification_notificationText);
        int notificationTextColor = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_notificationTextColor, R.color.black);

        subText = (TextView)view.findViewById(R.id.button_subtext);
        subText.setTextColor(getResources().getColor(notificationTextColor));
        subText.setBackgroundResource(notificationBgColor);
        setNotificationText(notificationText);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        buttonWithText.setOnClickListener(l);
    }

    public void setNotificationText(String notificationText) {
        this.setNotificationText(notificationText == null ? null : new SpannableString(notificationText));
    }

    public void setNotificationText(Spannable notificationText) {
        if (notificationText != null && notificationText.length() != 0) {
            subText.setVisibility(VISIBLE);
            subText.setText(notificationText);
        } else {
            subText.setVisibility(GONE);
        }
    }

    public void setText(String text) {
        buttonWithText.setText(text);
    }

    public void setText(Spannable text) {
        buttonWithText.setText(text.toString());
    }
    public SquareButtonWithText getButtonWithText() {
        return buttonWithText;
    }
    public String getSubText() {
        return subText.getText().toString();
    }
}
