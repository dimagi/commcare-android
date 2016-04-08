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
        String displayText =
                Localizer.processArguments(mData.getName(), new String[]{""}).trim();

        setAVT(displayText, mData.getAudioURI(), mData.getImageURI());
    }

    public void setAVT(String displayText, String audioURI, String imageURI) {
        removeAllViews();

        RelativeLayout.LayoutParams audioParams =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        audioButton = setupAudioButton(audioURI, audioParams);

        ImageView imageView = setupImageView(imageURI, audioParams);

        setupText(displayText, imageView);
    }

    private AudioButton setupAudioButton(String audioURI, RelativeLayout.LayoutParams audioParams) {
        AudioButton tmpAudioButton = null;
        // First set up the audio button
        if (audioFileExists(audioURI)) {
            // An audio file is specified
            tmpAudioButton = new AudioButton(getContext(), audioURI, true);
            tmpAudioButton.setId(3245345); // random ID to be used by the relative layout.
            // Set not focusable so that list onclick will work
            tmpAudioButton.setFocusable(false);
            tmpAudioButton.setFocusableInTouchMode(false);
            audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            audioParams.addRule(CENTER_VERTICAL);
            addView(tmpAudioButton, audioParams);
        }
        return tmpAudioButton;
    }

    private static boolean audioFileExists(String audioURI) {
        if (audioURI != null && !audioURI.equals("")) {
            try {
                return new File(ReferenceManager._().DeriveReference(audioURI).getLocalURI()).exists();
            } catch (InvalidReferenceException e) {
                Log.e(TAG, "Invalid reference exception");
                e.printStackTrace();
            }
        }
        return false;
    }

    private ImageView setupImageView(String imageURI, RelativeLayout.LayoutParams audioParams) {
        ImageView imageView = null;
        Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI, iconDimension, iconDimension);
        if (b != null) {
            RelativeLayout.LayoutParams imageParams =
                    new RelativeLayout.LayoutParams(iconDimension, iconDimension);
            imageView = new ImageView(getContext());
            imageView.setPadding(10, 10, 10, 10);
            imageView.setAdjustViewBounds(true);
            imageView.setImageBitmap(b);
            imageView.setId(23422634);
            imageParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            audioParams.addRule(CENTER_VERTICAL);
            addView(imageView, imageParams);
        }
        return imageView;
    }

    private void setupText(String displayText, ImageView imageView) {
        RelativeLayout.LayoutParams textParams =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.CENTER_VERTICAL);
        textParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        if (imageView != null) {
            textParams.addRule(RelativeLayout.RIGHT_OF, imageView.getId());
        } else {
            textParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        }

        if (audioButton != null) {
            textParams.addRule(RelativeLayout.LEFT_OF, audioButton.getId());
        }
        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        TextView mTextView = (TextView) inflater.inflate(R.layout.menu_list_item, null);
        mTextView.setText(displayText);
        addView(mTextView, textParams);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);

        if (visibility != View.VISIBLE && audioButton != null) {
            audioButton.endPlaying();
        }
    }
}
