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
 * @author Daniel Luna (dcluna@dimagi.com)
 */
public class SquareButtonWithNotification extends RelativeLayout {
    private SquareButtonWithText buttonWithText;
    private TextView subText;
    private int backgroundColorButton = android.R.drawable.btn_default;
    private int backgroundColorNotification = R.color.solid_green;
    private int colorButtonText = R.color.white;
    private int colorNotificationText = R.color.black;

    public void setOnClickListener(OnClickListener l) {
        buttonWithText.setOnClickListener(l);
    }

    public void setNotificationText(String textNotification) {
        this.setNotificationText(textNotification == null ? null : new SpannableString(textNotification));
    }

    public void setNotificationText(Spannable textNotification) {
        if (textNotification != null && textNotification.length() != 0) {
            subText.setVisibility(VISIBLE);
            subText.setText(textNotification);
            subText.setBackgroundResource(backgroundColorNotification);
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

    public SquareButtonWithNotification(Context context, AttributeSet attrs) {
        super(context, attrs);

        setUI(context, attrs);
    }

    public SquareButtonWithNotification(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setUI(context, attrs);
    }

    private void setUI(Context context, AttributeSet attrs) {
        View view = inflate(context, R.layout.square_button_notification, this);
        buttonWithText = (SquareButtonWithText) view.findViewById(R.id.square_button_text);
        subText = (TextView) view.findViewById(R.id.button_subtext);

        if (attrs != null) {
            TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SquareButtonWithNotification, 0, 0);

            Drawable backgroundImg = typedArray.getDrawable(R.styleable.SquareButtonWithNotification_sbn_img);
            backgroundColorButton = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_backgroundcolorButton, backgroundColorButton);
            backgroundColorNotification = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_backgroundcolorNotification, backgroundColorNotification);
            String subtitleButton = typedArray.getString(R.styleable.SquareButtonWithNotification_sbn_subtitle);
            String textNotification = typedArray.getString(R.styleable.SquareButtonWithNotification_notificationText);
            colorButtonText = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_colorButtonText, colorButtonText);
            colorNotificationText = typedArray.getResourceId(R.styleable.SquareButtonWithNotification_colorNotificationText, colorNotificationText);

            typedArray.recycle();

            buttonWithText.setColor(getResources().getColor(backgroundColorButton));
            buttonWithText.setImage(backgroundImg);
            buttonWithText.setText(subtitleButton);
            setNotificationText(textNotification);
            buttonWithText.setTextColor(colorButtonText);
            subText.setTextColor(getResources().getColor(colorNotificationText));
        }
    }
}
