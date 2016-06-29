package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Created by Saumya on 6/3/2016.
 * An alternative audio widget that records and plays audio natively without callout to any external application
 */
public class AudioPrototype extends AudioWidget implements RecordingFragment.RecordingCompletionListener {

    private RecordingFragment recorder;
    private FragmentManager fm;
    private LinearLayout layout;
    private ImageButton mPlayButton;
    private TextView recordingText;

    public AudioPrototype(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic){
        super(context, prompt, pic);
        fm = ((FragmentActivity) getContext()).getSupportFragmentManager();
        recorder = new RecordingFragment();
        recorder.setListener(this);
    }

    @Override
    protected void initializeButtons(final FormEntryPrompt prompt){
        LayoutInflater vi = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layout = (LinearLayout) vi.inflate(R.layout.audio_prototype, null);

        mPlayButton = (ImageButton) layout.findViewById(R.id.play_audio);
        ImageButton mCaptureButton = (ImageButton) layout.findViewById(R.id.capture_button);
        ImageButton mChooseButton = (ImageButton) layout.findViewById(R.id.choose_file);

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportRecordingPopupOpened();
                captureAudio(prompt);
            }
        });

        // launch audio filechooser intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioFileChosen();
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                try {
                    ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.AUDIO_VIDEO_FETCH);
                    recordingText.setTextColor(getResources().getColor(R.color.black));
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

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                playAudio();
            }
        });
    }

    @Override
    public void setupLayout(){
        recordingText = (TextView) layout.findViewById(R.id.recording_text);
        recordingText.setText(Localization.get("recording.prompt"));
        addView(layout);
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt){
        recorder.show(fm, "Recorder");
    }

    @Override
    public void setBinaryData(Object binaryuri){
        super.setBinaryData(binaryuri);
        if(prevFileName != null){
            recordingText.setText(prevFileName);
        }
    }

    @Override
    public void onCompletion(){
        setBinaryData(recorder.getFileName());
        mPlayButton.setEnabled(true);
        mPlayButton.setBackgroundResource(R.drawable.play);
        recordingText.setTextColor(getResources().getColor(R.color.black));
        recordingText.setText(Localization.get("recording.custom"));
    }

    @Override
    protected void playAudio() {
        Uri myPath = Uri.parse(mInstanceFolder + mBinaryName);
        final MediaPlayer player = MediaPlayer.create(getContext(), myPath);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                resetAudioPlayer(mp);
            }
        });
        player.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer(player);
            }
        });
    }

    private void pauseAudioPlayer(MediaPlayer player){
        final MediaPlayer mp = player;
        mp.pause();
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                resumeAudioPlayer(mp);
            }
        });
    }

    private void resumeAudioPlayer(MediaPlayer player){
        final MediaPlayer mp = player;
        mp.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer(mp);
            }
        });
    }

    private void resetAudioPlayer(MediaPlayer mp){
        mp.release();
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
        if(enabled){
            mPlayButton.setBackgroundResource(R.drawable.play);
        }else{
            mPlayButton.setBackgroundResource(R.drawable.play_disabled);
        }
        mPlayButton.setEnabled(enabled);
    }

    @Override
    protected void reloadFile(){
        super.reloadFile();
        recordingText.setTextColor(getResources().getColor(R.color.black));
        if(mBinaryName.contains(CUSTOM_TAG)){
            recordingText.setText(Localization.get("recording.custom"));
        }else{
            recordingText.setText(mBinaryName);
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {}

    @Override
    public void cancelLongPress() {}

    @Override
    public void unsetListeners() {}

}
