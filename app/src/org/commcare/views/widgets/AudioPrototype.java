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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
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

    private ImageButton mCaptureButton;
    private ImageButton mPlayButton;
    private ImageButton mChooseButton;
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
        mCaptureButton = (ImageButton) layout.findViewById(R.id.capture_button);
        mChooseButton = (ImageButton) layout.findViewById(R.id.choose_file);

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAudio(prompt);
            }
        });

        // launch audio filechooser intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                playAudio();
            }
        });
    }

    @Override
    protected void disablePlayButton(){
        mPlayButton.setEnabled(false);
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

        //TODO: After the file choose intent is done the widget gets redrawn by Android, so the new text in recordingText gets cleared. Ask Will about how to get around this.
    }

    @Override
    public IAnswerData getAnswer(){

        MediaRecorder mRecorder = recorder.getRecorder();

        if(mRecorder != null){
            mRecorder.stop();
            mRecorder.release();
        }

        return super.getAnswer();
    }

    @Override
    public void onCompletion(){
        setBinaryData(recorder.getFileName());
        mPlayButton.setEnabled(true);
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
        mPlayButton.setBackgroundResource(R.drawable.avatar_vellum_phonenumber);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseAudioPlayer(player);
            }
        });
    }

    private void pauseAudioPlayer(MediaPlayer player){
        final MediaPlayer mp = player;
        mp.pause();
        mPlayButton.setBackgroundResource(R.drawable.next_arrow);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mp.start();
            }
        });
    }

    private void resetAudioPlayer(MediaPlayer mp){
        mp.release();
        mPlayButton.setBackgroundResource(R.drawable.next_arrow);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
    }

    @Override
    protected void togglePlayButton(boolean enabled) {
        mPlayButton.setEnabled(enabled);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {}

    @Override
    public void cancelLongPress() {}

    @Override
    public void unsetListeners() {}

    }
