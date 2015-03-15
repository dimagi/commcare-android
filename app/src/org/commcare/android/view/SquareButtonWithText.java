package org.commcare.android.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;

/**
 * Created by dancluna on 3/14/15.
 */
@ManagedUi(R.layout.square_button_text)
public class SquareButtonWithText extends RelativeLayout {
    @UiElement(R.id.square_button)
    SquareButton squareButton;

    @UiElement(R.id.text_view)
    TextView textView;

    Drawable backgroundImg;
    int backgroundColor = android.R.drawable.btn_default;
    String text = "";

    private void inflateAndExtractCustomParams(Context context, AttributeSet attrs) {
        inflate(context, R.layout.square_button_text, this);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SquareButtonWithText);

        backgroundImg = typedArray.getDrawable(R.styleable.SquareButtonWithText_img);
        backgroundColor = typedArray.getResourceId(R.styleable.SquareButtonWithText_backgroundcolor, android.R.color.transparent);
        text = typedArray.getString(R.styleable.SquareButtonWithText_subtitle);

        typedArray.recycle();

        squareButton = (SquareButton) findViewById(R.id.square_button);
        textView = (TextView) findViewById(R.id.text_view);

        if(isInEditMode()){
            setUI(R.color.cc_brand_color, getResources().getDrawable(R.drawable.barcode), "Your text goes here");
        }

        setUI(backgroundColor, backgroundImg, text);
    }

    private void setUI(int backgroundColor, Drawable backgroundImg, String text) {
        squareButton.setBackgroundResource(backgroundColor);
        squareButton.setImageDrawable(backgroundImg);
        textView.setText(text);
    }

    public SquareButtonWithText(Context context) {
        super(context);
    }

    public SquareButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflateAndExtractCustomParams(context, attrs);
    }

    public SquareButtonWithText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        inflateAndExtractCustomParams(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SquareButtonWithText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        inflateAndExtractCustomParams(context, attrs);
    }
}
