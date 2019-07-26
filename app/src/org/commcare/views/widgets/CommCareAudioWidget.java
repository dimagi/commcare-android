package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Date;

import static org.commcare.views.widgets.RecordingFragment.AUDIO_FILE_PATH_ARG_KEY;

/**
 * An alternative audio widget that records and plays audio natively without
 * callout to any external application.
 *
 * @author Saumya Jain (sjain@dimagi.com)
 */
public class CommCareAudioWidget extends AudioWidget
        implements RecordingFragment.RecordingCompletionListener {

    private LinearLayout layout;
    private ImageButton mPlayButton;
    private TextView recordingNameText;
    private MediaPlayer player;

    public CommCareAudioWidget(Context context, FormEntryPrompt prompt,
                               PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }


    @Override
    protected void initializeButtons() {
        LayoutInflater vi = LayoutInflater.from(getContext());
        layout = (LinearLayout)vi.inflate(R.layout.audio_prototype, null);

        mPlayButton = layout.findViewById(R.id.play_audio);
        ImageButton captureButton = layout.findViewById(R.id.capture_button);
        ImageButton chooseButton = layout.findViewById(R.id.choose_file);

        captureButton.setOnClickListener(v -> captureAudio(mPrompt));

        // launch audio filechooser intent on click
        chooseButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            try {
                ((Activity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_FETCH);
                recordingNameText.setTextColor(getResources().getColor(R.color.black));
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(),
                        StringUtils.getStringSpannableRobust(getContext(),
                                R.string.activity_not_found,
                                "choose audio"),
                        Toast.LENGTH_SHORT).show();
            }
        });


        mPlayButton.setOnClickListener(v -> playAudio());
    }

    @Override
    public IAnswerData getAnswer() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    System.out.println("Playing");
                    player.pause();
                }
            } catch (IllegalStateException e) {
                //Do nothing because player is not playing
            }

            player.release();
        }

        return super.getAnswer();
    }

    @Override
    public void setupLayout() {
        recordingNameText = layout.findViewById(R.id.recording_text);
        recordingNameText.setText(Localization.get("recording.prompt"));
        addView(layout);
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt) {
        RecordingFragment recorder = new RecordingFragment();
        recorder.setListener(this);
        if (!TextUtils.isEmpty(mBinaryName)) {
            Bundle args = new Bundle();
            args.putString(AUDIO_FILE_PATH_ARG_KEY, mInstanceFolder + mBinaryName);
            recorder.setArguments(args);
        }
        recorder.show(((FragmentActivity)getContext()).getSupportFragmentManager(), "Recorder");
    }

    @Override
    public void setBinaryData(Object binaryuri) {
        super.setBinaryData(binaryuri);
        if (recordedFileName != null) {
            recordingNameText.setText(recordedFileName);
        }
    }

    @Override
    public void onRecordingCompletion(String audioFile) {
        setBinaryData(audioFile);
        mPlayButton.setEnabled(true);
        mPlayButton.setBackgroundResource(R.drawable.play);
        recordingNameText.setTextColor(getResources().getColor(R.color.black));
        recordingNameText.setText(Localization.get("recording.custom"));
    }

    @Override
    public String getFileUniqueIdentifier() {
        String formFileName = FormEntryInstanceState.mFormRecordPath.substring(FormEntryInstanceState.mFormRecordPath.lastIndexOf("/") + 1);
        return formFileName + "_" + mPrompt.getIndex().toString() + "_" + (new Date().getTime());
    }

    @Override
    protected void playAudio() {
        Uri filePath = Uri.parse(mInstanceFolder + mBinaryName);
        player = MediaPlayer.create(getContext(), filePath);
        player.setOnCompletionListener(mp -> resetAudioPlayer());
        player.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mPlayButton.setOnClickListener(v -> pauseAudioPlayer());
    }

    private void pauseAudioPlayer() {
        player.pause();
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> resumeAudioPlayer());
    }

    private void resumeAudioPlayer() {
        player.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mPlayButton.setOnClickListener(v -> pauseAudioPlayer());
    }

    private void resetAudioPlayer() {
        player.release();
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> playAudio());
    }

    @Override
    protected void togglePlayButton(boolean enabled) {
        if (enabled) {
            mPlayButton.setBackgroundResource(R.drawable.play);
        } else {
            mPlayButton.setBackgroundResource(R.drawable.play_disabled);
        }
        mPlayButton.setEnabled(enabled);
    }

    @Override
    protected void reloadFile() {
        super.reloadFile();
        recordingNameText.setTextColor(getResources().getColor(R.color.black));
        if (mBinaryName.contains(CUSTOM_TAG)) {
            recordingNameText.setText(Localization.get("recording.custom"));
        } else {
            recordingNameText.setText(mBinaryName);
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
    }

    @Override
    public void cancelLongPress() {
    }

    @Override
    public void unsetListeners() {
    }
}
