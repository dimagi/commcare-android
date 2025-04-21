package org.commcare.views.widgets;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore.Video;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.form.api.FormEntryPrompt;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class VideoWidget extends MediaWidget {

    public VideoWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }

    @Override
    protected void initializeButtons() {
        // setup capture button
        mCaptureButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_video),
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(v -> {
            Intent i = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                    Video.Media.EXTERNAL_CONTENT_URI.toString());
            try {
                ((AppCompatActivity)getContext()).startActivityForResult(i,
                        FormEntryConstants.AUDIO_VIDEO_FETCH);
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(),
                        StringUtils.getStringSpannableRobust(getContext(),
                                R.string.activity_not_found, "capture video"),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // setup capture button
        mChooseButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_video),
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(v -> {
            try {
                ((AppCompatActivity)getContext())
                        .startActivityForResult(WidgetUtils.createPickMediaIntent (getContext(), "video/*"),
                                FormEntryConstants.AUDIO_VIDEO_FETCH);
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(),
                        StringUtils.getStringSpannableRobust(getContext(),
                                R.string.activity_not_found, "choose video "),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // setup play button
        mPlayButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_video),
                !mPrompt.isReadOnly());

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(v -> playMedia(getContext(),"video/*", getSourceFilePathToDisplay()));

        String acq = mPrompt.getAppearanceHint();
        if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq)) {
            mChooseButton.setVisibility(View.GONE);
        }
    }
}
