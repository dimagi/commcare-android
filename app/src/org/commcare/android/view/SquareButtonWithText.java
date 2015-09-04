package org.commcare.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * @author Daniel Luna (dluna@dimagi.com)
 */
public class SquareButtonWithText extends CustomButtonWithText {
    public SquareButtonWithText(Context context) {
        super(context);
    }

    public SquareButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareButtonWithText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void inflateAndExtractCustomParams(Context context, AttributeSet attrs) {
        inflate(context, R.layout.square_button_text, this);
        this.setClickable(true);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SquareButtonWithText);

        Drawable backgroundImg = typedArray.getDrawable(R.styleable.SquareButtonWithText_img);
        int backgroundColor = getResources().getColor(typedArray.getResourceId(R.styleable.SquareButtonWithText_backgroundcolor, android.R.color.transparent));
        String text = typedArray.getString(R.styleable.SquareButtonWithText_subtitle);
        int colorButtonText = typedArray.getResourceId(R.styleable.SquareButtonWithText_colorText, DEFAULT_TEXT_COLOR);

        typedArray.recycle();

        button = (SquareButton) findViewById(R.id.square_button);
        textView = (TextView) findViewById(R.id.square_text_view);

        if (isInEditMode()) {
            setUI(R.color.cc_brand_color, getResources().getDrawable(R.drawable.barcode), "Your text goes here", colorButtonText);
        }

        setUI(backgroundColor, backgroundImg, text, colorButtonText);
    }
}
