package org.commcare.views.widgets;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.content.DialogInterface;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

/**
 * Created by Saumya on 6/15/2016.
 * A popup dialog fragment that handles recording_fragment and saving of audio files without external callout
 */

//TODO:
    /*
Recording Popup:
    -Add ability to pause and resume recording: Can't do this
    -Add a timer for recording and playback
     */

public class RecordingFragment extends android.support.v4.app.DialogFragment{

    private String fileName;
    private final String FILE_EXT = "/Android/data/org.commcare.dalvik/temp/Custom Recording.mp4";

    private LinearLayout layout;
    private ImageButton toggleRecording;
    private ImageButton discardRecording;
    private Button saveRecording;
    private Button recycle;
    private TextView instruction;
    private ProgressBar mpc;

    private Chronometer chron;
    private long currentTime;

    private MediaRecorder recorder;
    private RecordingCompletionListener listener;
    private MediaPlayer player;


    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        layout = (LinearLayout) inflater.inflate(R.layout.recording_fragment, container);
        enableScreenRotation();
        prepareButtons();
        prepareText();
        setWindowSize();
        fileName = Environment.getExternalStorageDirectory().getAbsolutePath() + FILE_EXT;
        setCancelable(false);
        return layout;
    }

    protected void setWindowSize() {
        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        layout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }

    protected void prepareText() {
        TextView header = (TextView) layout.findViewById(R.id.recording_header);
        instruction = (TextView) layout.findViewById(R.id.recording_instruction);

        header.setText(Localization.get("recording.header"));
        instruction.setText(Localization.get("before.recording"));

        chron = (Chronometer) layout.findViewById(R.id.recording_time);
    }

    private void prepareButtons() {
        discardRecording = (ImageButton) layout.findViewById(R.id.discardrecording);
        toggleRecording = (ImageButton) layout.findViewById(R.id.startrecording);
        saveRecording = (Button) layout.findViewById(R.id.saverecording);
        recycle = (Button) layout.findViewById(R.id.recycle);

        recycle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(recorder != null){
                    recorder.release();
                    recorder = null;
                }

                if(player != null){
                    resetAudioPlayer(player);
                }

                toggleRecording.setBackgroundResource(R.drawable.record_start);
                toggleRecording.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startRecording();
                    }
                });
                instruction.setText(Localization.get("before.recording"));
                saveRecording.setVisibility(View.INVISIBLE);
                recycle.setVisibility(View.INVISIBLE);
                chron.setVisibility(View.INVISIBLE);
            }
        });

        discardRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportRecordingStarted();
                startRecording();
            }
        });
        saveRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioFileSaved();
                saveRecording();
            }
        });
        saveRecording.setText(Localization.get("save"));
        mpc = (ProgressBar) layout.findViewById(R.id.demo_mpc);
    }

    private void startRecording(){
        disableScreenRotation();
        if(recorder == null){
            recorder = new MediaRecorder();
        }

        setupRecorder();
        recorder.start();

        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportRecordingStopped();
                stopRecording();
            }
        });
        toggleRecording.setBackgroundResource(R.drawable.record_in_progress);
        instruction.setText(Localization.get("during.recording"));

        mpc.setVisibility(View.VISIBLE);
        chron.setVisibility(View.VISIBLE);

        chron.setBase(SystemClock.elapsedRealtime());
        chron.start();
    }

    private void setupRecorder() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try{
            recorder.prepare();
        }catch(IOException e){
            Log.d("Recorder", "Failed to prepare media recorder");
        }
    }

    private void stopRecording(){

        chron.stop();
        recycle.setVisibility(View.VISIBLE);
        mpc.setVisibility(View.INVISIBLE);
        recorder.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
        saveRecording.setEnabled(true);
        saveRecording.setVisibility(View.VISIBLE);
        instruction.setText(Localization.get("after.recording"));
    }

    public void saveRecording(){
        if(listener != null){
            listener.onRecordingCompletion();
        }
        dismiss();
    }

    public interface RecordingCompletionListener {
        void onRecordingCompletion();
    }

    public void setListener(RecordingCompletionListener listener){
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog){
        super.onDismiss(dialog);

        if(recorder != null){
            recorder.release();
            this.recorder = null;
        }
    }

    public String getFileName(){
        return fileName;
    }

    private void disableScreenRotation() {
        int currentOrientation = getResources().getConfiguration().orientation;

        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        else {
            ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    private void enableScreenRotation() {
        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    protected void playAudio() {

        Uri myPath = Uri.parse(fileName);
        player = MediaPlayer.create(getContext(), myPath);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                resetAudioPlayer(mp);
            }
        });
        chron.setBase(SystemClock.elapsedRealtime());
        chron.start();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
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
        chron.stop();
        currentTime = chron.getBase();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                resumeAudioPlayer(mp);
            }
        });
    }

    private void resumeAudioPlayer(MediaPlayer player){
        final MediaPlayer mp = player;
        chron.setBase(currentTime);
        chron.start();
        mp.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer(mp);
            }
        });
    }

    private void resetAudioPlayer(MediaPlayer mp){
        mp.release();
        chron.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                playAudio();
            }
        });
    }
}
