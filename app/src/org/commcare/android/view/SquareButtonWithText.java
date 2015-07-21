package org.commcare.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.util.StateSet;
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
    private int colorButtonText = R.color.cc_core_bg;

    //region Constructors

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

    //endregion

    //region Custom parameter processing

    private void inflateAndExtractCustomParams(Context context, AttributeSet attrs) {
        inflate(context, R.layout.square_button_text, this);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SquareButtonWithText);

        backgroundImg = typedArray.getDrawable(R.styleable.SquareButtonWithText_img);
        backgroundColor = getResources().getColor(typedArray.getResourceId(R.styleable.SquareButtonWithText_backgroundcolor, android.R.color.transparent));
        text = typedArray.getString(R.styleable.SquareButtonWithText_subtitle);
        colorButtonText = typedArray.getResourceId(R.styleable.SquareButtonWithText_colorText, colorButtonText);

        typedArray.recycle();

        squareButton = (SquareButton)findViewById(R.id.square_button);
        textView = (TextView)findViewById(R.id.text_view);

        if (isInEditMode()) {
            setUI(R.color.cc_brand_color, getResources().getDrawable(R.drawable.barcode), "Your text goes here", colorButtonText);
        }

        setUI(backgroundColor, backgroundImg, text, colorButtonText);
    }

    private void setUI(int backgroundColor, Drawable backgroundImg, String text, int colorButtonText) {
        setColor(backgroundColor);
        setImage(backgroundImg);
        setText(text);
        setTextColor(colorButtonText);
    }

    //endregion

    //region Compatibility methods

    public void setText(String text) {
        if (textView != null) {
            textView.setText(text);
        }
    }

    public void setImage(Drawable backgroundImg) {
        squareButton.setImageDrawable(backgroundImg);
    }

    public void setColor(int backgroundColor) {
        // shows a bluish background when pressed, otherwise shows the chosen color
        ColorDrawable pressedBackground = new ColorDrawable(getResources().getColor(R.color.blue_light));
        ColorDrawable colorDrawable = new ColorDrawable(backgroundColor);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
        sld.addState(StateSet.WILD_CARD, colorDrawable);
        squareButton.setBackgroundDrawable(sld);
    }

    public void setTextColor(int textColor) {
        textView.setTextColor(getResources().getColor(textColor));
    }

    //endregion

    @Override
    public void setOnClickListener(OnClickListener l) {
        // attach the listener to the squareButton instead of the layout
        squareButton.setOnClickListener(l);
    }
}
