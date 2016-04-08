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
    private static final String TAG = HorizontalMediaView.class.getSimpleName();

    private final int iconDimension;
    private AudioButton audioButton;

    public HorizontalMediaView(Context c) {
        super(c);
        this.iconDimension = (int) getResources().getDimension(R.dimen.menu_icon_size);
    }

    public void setDisplay(DisplayUnit display) {
        DisplayData mData = display.evaluate();
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
                Log.e(TAG, "Invalid reference exception");
                e.printStackTrace();
            }
        }

        File audioFile = new File(audioFilename);

        // First set up the audio button
        if (!"".equals(audioFilename) && audioFile.exists()) {
            // An audio file is specified
            audioButton = new AudioButton(getContext(), audioURI, true);
            audioButton.setId(3245345); // random ID to be used by the relative layout.
            // Set not focusable so that list onclick will work
            audioButton.setFocusable(false);
            audioButton.setFocusableInTouchMode(false);
            audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            audioParams.addRule(CENTER_VERTICAL);
            addView(audioButton, audioParams);
        }

        ImageView imageView = null;
        Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI, iconDimension, iconDimension);
        if (b != null) {
            imageView = new ImageView(getContext());
            imageView.setPadding(10, 10, 10, 10);
            imageView.setAdjustViewBounds(true);
            imageView.setImageBitmap(b);
            imageView.setId(23422634);
            imageParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            audioParams.addRule(CENTER_VERTICAL);
            addView(imageView, imageParams);
        }

        textParams.addRule(RelativeLayout.CENTER_VERTICAL);
        textParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        if (imageURI != null && !imageURI.equals("") && imageView != null) {
            textParams.addRule(RelativeLayout.RIGHT_OF, imageView.getId());
        } else {
            textParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        }

        if (audioButton != null) {
            textParams.addRule(RelativeLayout.LEFT_OF, audioButton.getId());
        }
        addView(mTextView, textParams);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            if (audioButton != null) {
                audioButton.endPlaying();
            }
        }
    }
}
