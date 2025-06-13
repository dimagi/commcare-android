package org.commcare.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import androidx.core.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.util.StateSet;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * Custom button that allows setting image, text, and color
 *
 * @author Daniel Luna (dluna@dimagi.com)
 * @author Phillip Mates (pmates@dimagi.com).
 */
public abstract class CustomButtonWithText extends RelativeLayout {
    private static final int DEFAULT_TEXT_COLOR = R.color.cc_core_bg;
    private ImageButton button;
    private TextView textView;

    public CustomButtonWithText(Context context) {
        super(context);
    }

    public CustomButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflateAndExtractCustomParams(context, attrs);
    }

    public CustomButtonWithText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        inflateAndExtractCustomParams(context, attrs);
    }

    private void inflateAndExtractCustomParams(Context context, AttributeSet attrs) {
        inflate(context, getLayoutResourceId(), this);
        this.setClickable(true);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CustomButtonWithText);

        Drawable backgroundImg = typedArray.getDrawable(R.styleable.CustomButtonWithText_img);
        int backgroundColor = getResources().getColor(typedArray.getResourceId(R.styleable.CustomButtonWithText_backgroundColor, android.R.color.transparent));
        String text = typedArray.getString(R.styleable.CustomButtonWithText_subtitle);
        int colorButtonText = typedArray.getResourceId(R.styleable.CustomButtonWithText_textColor, DEFAULT_TEXT_COLOR);

        typedArray.recycle();

        button = findViewById(R.id.button);
        textView = findViewById(R.id.text_view);

        if (isInEditMode()) {
            setUI(R.color.cc_brand_color, getResources().getDrawable(R.drawable.barcode), "Your text goes here", colorButtonText);
        }

        setUI(backgroundColor, backgroundImg, text, colorButtonText);
    }

    abstract int getLayoutResourceId();

    private void setUI(int backgroundColor, Drawable backgroundImg, String text, int colorButtonText) {
        setColor(backgroundColor);
        setImage(backgroundImg);
        setText(text);
        setTextColor(colorButtonText);
    }

    public void setText(String text) {
        if (textView != null) {
            textView.setText(text);
        }
    }

    public void setImage(Drawable backgroundImg) {
        button.setImageDrawable(backgroundImg);
    }

    private void setColor(int backgroundColor) {
        ColorDrawable colorDrawable = new ColorDrawable(backgroundColor);
        ColorDrawable disabledColor = new ColorDrawable(getResources().getColor(R.color.grey));

        int color = ViewUtil.getColorDrawableColor(colorDrawable);

        float[] hsvOutput = new float[3];
        Color.colorToHSV(color, hsvOutput);

        hsvOutput[2] = (float)(hsvOutput[2] / 1.5);

        int selectedColor = Color.HSVToColor(hsvOutput);

        ColorDrawable pressedBackground = new ColorDrawable(selectedColor);

        StateListDrawable sld = new StateListDrawable();

        sld.addState(new int[]{-android.R.attr.state_enabled}, disabledColor);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
        sld.addState(StateSet.WILD_CARD, colorDrawable);
        this.setBackground(sld);
    }

    private void setTextColor(int textColor) {
        textView.setTextColor(getResources().getColor(textColor));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        button.setEnabled(enabled);

        if (!enabled) {
            // add gray tint to button's image
            Drawable wrapDrawable = DrawableCompat.wrap(button.getDrawable());
            DrawableCompat.setTint(wrapDrawable, Color.GRAY);
        } else {
            // clear tint from button's image
            Drawable wrapDrawable = DrawableCompat.wrap(button.getDrawable());
            DrawableCompat.setTintList(wrapDrawable, null);
        }
    }
}
