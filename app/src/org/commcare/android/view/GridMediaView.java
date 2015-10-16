package org.commcare.android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.suite.model.graph.DisplayData;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.locale.Localizer;

/**
 * This layout for the GenericMenuFormAdapter allows you to load an image, audio, and text
 * to menus.
 * 
 * @author wspride
 */

public class GridMediaView extends RelativeLayout {
    private static final String t = "AVTLayout";

    private TextView mTextView;
    private ImageView mImageView;
    private TextView mMissingImage;
    private final int iconDimension;

    private EvaluationContext ec;


    public GridMediaView(Context c) {
        this(c, null);
    }

    public GridMediaView(Context c, EvaluationContext ec) {
        super(c);
        mTextView = null;
        mImageView = null;
        mMissingImage = null;
        this.ec = ec;
        this.iconDimension = (int) getResources().getDimension(R.dimen.menu_grid_icon_size);

    }


    public void setDisplay(DisplayUnit display) {
        DisplayData mData = display.evaluate(ec);
        setAVT(Localizer.processArguments(display.getText().evaluate(ec), new String[] {""}).trim(), mData.getImageURI());
    }

    //accepts a string to display and URI links to the audio and image, builds the proper TextImageAudio view
    public void setAVT(String displayText, String imageURI) {
        this.removeAllViews();

        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mTextView = (TextView)inflater.inflate(R.layout.menu_grid_item, null);

        mTextView.setText(displayText);
        mTextView.setHeight((int)(2.5*mTextView.getLineHeight())); // because 2 lines isn't enough to show 2 lines

        // Layout configurations for our elements in the relative layout
        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(iconDimension,iconDimension);

        Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI);

        if(b == null){
            b = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.info_bubble);
        }


        mImageView = new ImageView(getContext());
        mImageView.setPadding(15, 15, 15, 0);
        mImageView.setAdjustViewBounds(true);
        mImageView.setImageBitmap(b);
        mImageView.setId(23422634);
        imageParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        addView(mImageView, imageParams);


        textParams.addRule(RelativeLayout.CENTER_HORIZONTAL);

        imageParams.addRule(RelativeLayout.CENTER_HORIZONTAL);

        if(imageURI != null && !imageURI.equals("") && mImageView != null){
            imageParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            textParams.addRule(RelativeLayout.BELOW, mImageView.getId());
        }
        else{
            textParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }

        addView(mTextView, textParams);
    }


    /**
     * This adds a divider at the bottom of this layout. Used to separate fields in lists.
     */
    public void addDivider(ImageView v) {
        RelativeLayout.LayoutParams dividerParams =
                new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        if (mImageView != null) {
            dividerParams.addRule(RelativeLayout.BELOW, mImageView.getId());
        } else if (mMissingImage != null) {
            dividerParams.addRule(RelativeLayout.BELOW, mMissingImage.getId());
        } else if (mTextView != null) {
            // No picture
            dividerParams.addRule(RelativeLayout.BELOW, mTextView.getId());
        } else {
            Log.e(t, "Tried to add divider to uninitialized ATVWidget");
            return;
        }
        addView(v, dividerParams);
    }
}
