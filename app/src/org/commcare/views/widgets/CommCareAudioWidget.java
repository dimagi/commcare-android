package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * An alternative audio widget that records and plays audio natively without
 * callout to any external application.
 *
 * @author Saumya Jain (sjain@dimagi.com)
 */
public class CommCareAudioWidget extends AudioWidget
        implements RecordingFragment.RecordingCompletionListener {

    private final RecordingFragment recorder;
    private final FragmentManager fm;
    private LinearLayout layout;
    private ImageButton mPlayButton;
    private TextView recordingNameText;
    private final String questionIndexText;
    private MediaPlayer player;

    public CommCareAudioWidget(Context context, FormEntryPrompt prompt,
                               PendingCalloutInterface pic) {
        super(context, prompt, pic);
        fm = ((FragmentActivity)getContext()).getSupportFragmentManager();
        recorder = new RecordingFragment();
        recorder.setListener(this);
        questionIndexText = prompt.getIndex().toString();
    }

    @Override
    protected void initializeButtons(final FormEntryPrompt prompt) {
        LayoutInflater vi = LayoutInflater.from(getContext());
        layout = (LinearLayout)vi.inflate(R.layout.audio_prototype, null);

        mPlayButton = (ImageButton)layout.findViewById(R.id.play_audio);
        ImageButton captureButton = (ImageButton)layout.findViewById(R.id.capture_button);
        ImageButton chooseButton = (ImageButton)layout.findViewById(R.id.choose_file);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportRecordingPopupOpened();
                captureAudio(prompt);
            }
        });

        // launch audio filechooser intent on click
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioFileChosen();
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                try {
                    ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.AUDIO_VIDEO_FETCH);
                    recordingNameText.setTextColor(getResources().getColor(R.color.black));
                    pendingCalloutInterface.setPendingCalloutFormIndex(prompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "choose audio"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });


        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                playAudio();
            }
        });
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
        recordingNameText = (TextView)layout.findViewById(R.id.recording_text);
        recordingNameText.setText(Localization.get("recording.prompt"));
        addView(layout);
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt) {
        recorder.show(fm, "Recorder");
    }

    @Override
    public void setBinaryData(Object binaryuri) {
        super.setBinaryData(binaryuri);
        if (recordedFileName != null) {
            recordingNameText.setText(recordedFileName);
        }
    }

    @Override
    public void onRecordingCompletion() {
        setBinaryData(recorder.getFileName());
        mPlayButton.setEnabled(true);
        mPlayButton.setBackgroundResource(R.drawable.play);
        recordingNameText.setTextColor(getResources().getColor(R.color.black));
        recordingNameText.setText(Localization.get("recording.custom"));
    }

    @Override
    public String getFileExtension() {
        return questionIndexText;
    }

    @Override
    protected void playAudio() {
        Uri filePath = Uri.parse(mInstanceFolder + mBinaryName);
        player = MediaPlayer.create(getContext(), filePath);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                resetAudioPlayer();
            }
        });
        player.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer();
            }
        });
    }

    private void pauseAudioPlayer() {
        player.pause();
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                resumeAudioPlayer();
            }
        });
    }

    private void resumeAudioPlayer() {
        player.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer();
            }
        });
    }

    private void resetAudioPlayer() {
        player.release();
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                playAudio();
            }
        });
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
