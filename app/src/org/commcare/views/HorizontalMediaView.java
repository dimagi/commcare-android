package org.commcare.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.suite.model.DisplayData;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.utils.MediaUtil;
import org.commcare.views.media.AudioButton;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localizer;

import java.io.File;

/**
 * This layout for the GenericMenuFormAdapter allows you to load an image, audio, and text
 * to menus.
 *
 * @author wspride
 */
public class HorizontalMediaView extends RelativeLayout {
    private static final String t = "AVTLayout";

    private AudioButton mAudioButton;
    private ImageView mImageView;
    private final int iconDimension;

    public HorizontalMediaView(Context c) {
        super(c);
        this.iconDimension = (int) getResources().getDimension(R.dimen.menu_icon_size);
    }

    public void setDisplay(DisplayUnit display) {
        DisplayData mData = display.evaluate(null);
        setAVT(Localizer.processArguments(mData.getName(), new String[]{""}).trim(), mData.getAudioURI(), mData.getImageURI());
    }

    public void setAVT(String displayText, String audioURI, String imageURI) {
        removeAllViews();

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        TextView mTextView = (TextView) inflater.inflate(R.layout.menu_list_item, null);
        mTextView.setText(displayText);

        // Layout configurations for our elements in the relative layout
        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        RelativeLayout.LayoutParams audioParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(iconDimension, iconDimension);

        String audioFilename = "";
        if (audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager._().DeriveReference(audioURI).getLocalURI();
            } catch (InvalidReferenceException e) {
                Log.e(t, "Invalid reference exception");
                e.printStackTrace();
            }
        }

        File audioFile = new File(audioFilename);

        // First set up the audio button
        if (!"".equals(audioFilename) && audioFile.exists()) {
            // An audio file is specified
            mAudioButton = new AudioButton(getContext(), audioURI, true);
            mAudioButton.setId(3245345); // random ID to be used by the relative layout.
            // Set not focusable so that list onclick will work
            mAudioButton.setFocusable(false);
            mAudioButton.setFocusableInTouchMode(false);
            audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            audioParams.addRule(CENTER_VERTICAL);
            addView(mAudioButton, audioParams);
        }

        Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI, iconDimension, iconDimension);
        if (b != null) {
            mImageView = new ImageView(getContext());
            mImageView.setPadding(10, 10, 10, 10);
            mImageView.setAdjustViewBounds(true);
            mImageView.setImageBitmap(b);
            mImageView.setId(23422634);
            imageParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            audioParams.addRule(CENTER_VERTICAL);
            addView(mImageView, imageParams);
        }

        textParams.addRule(RelativeLayout.CENTER_VERTICAL);
        textParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        if (imageURI != null && !imageURI.equals("") && mImageView != null) {
            textParams.addRule(RelativeLayout.RIGHT_OF, mImageView.getId());
        } else {
            textParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        }

        if (mAudioButton != null) {
            textParams.addRule(RelativeLayout.LEFT_OF, mAudioButton.getId());
        }
        addView(mTextView, textParams);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            if (mAudioButton != null) {
                mAudioButton.endPlaying();
            }
        }
    }
}
