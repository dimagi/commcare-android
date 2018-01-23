package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore.Audio;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to take pictures, sounds or video and add them to
 * the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class AudioWidget extends MediaWidget {

    public AudioWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }

    @Override
    protected void initializeButtons() {
        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_audio),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // setup audio filechooser button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_sound),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // setup play button
        mPlayButton = new Button(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_audio),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAudio(mPrompt);
            }
        });

        // launch audio filechooser intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                try {
                    ((Activity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_FETCH);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "choose audio"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(mPrompt.getAppearanceHint())) {
            mChooseButton.setVisibility(View.GONE);
        }

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
    }

    protected void playAudio() {
        playMedia("audio/*");
    }

    protected void captureAudio(FormEntryPrompt prompt) {
        Intent i = new Intent(Audio.Media.RECORD_SOUND_ACTION);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                Audio.Media.EXTERNAL_CONTENT_URI.toString());
        try {
            ((Activity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_FETCH);
            pendingCalloutInterface.setPendingCalloutFormIndex(prompt.getIndex());
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    StringUtils.getStringSpannableRobust(getContext(),
                            R.string.activity_not_found,
                            "audio capture"),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
