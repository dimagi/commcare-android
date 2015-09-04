package org.commcare.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class RectangleButtonWithText extends CustomButtonWithText {
    public RectangleButtonWithText(Context context) {
        super(context);
    }

    public RectangleButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RectangleButtonWithText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void inflateAndExtractCustomParams(Context context, AttributeSet attrs) {
        inflate(context, R.layout.rectangle_button_text, this);
        this.setClickable(true);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RectangleButtonWithText);

        Drawable backgroundImg = typedArray.getDrawable(R.styleable.RectangleButtonWithText_img);
        int backgroundColor = getResources().getColor(typedArray.getResourceId(R.styleable.RectangleButtonWithText_backgroundcolor, android.R.color.transparent));
        String text = typedArray.getString(R.styleable.RectangleButtonWithText_subtitle);
        int colorButtonText = typedArray.getResourceId(R.styleable.RectangleButtonWithText_colorText, DEFAULT_TEXT_COLOR);

        typedArray.recycle();

        button = (ImageButton) findViewById(R.id.rectangle_button);
        textView = (TextView) findViewById(R.id.rectangle_text_view);

        if (isInEditMode()) {
            setUI(R.color.cc_brand_color, getResources().getDrawable(R.drawable.barcode), "Your text goes here", colorButtonText);
        }

        setUI(backgroundColor, backgroundImg, text, colorButtonText);
    }
}
