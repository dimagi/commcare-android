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

import java.io.File;
import java.io.IOException;

/**
 * Created by Saumya on 6/15/2016.
 * A popup dialog fragment that handles recording_fragment and saving of audio files without external callout
 */

public class RecordingFragment extends android.support.v4.app.DialogFragment{

    private String fileName;
    private final String FILE_EXT = "/Android/data/org.commcare.dalvik/temp/Custom_Recording.mp4";

    private LinearLayout layout;
    private ImageButton toggleRecording;
    private Button saveRecording;
    private Button recordAgain;
    private TextView instruction;
    private ProgressBar recordingProgress;

    private Chronometer recordingDuration;
    private long currentTimeMillis;

    private MediaRecorder recorder;
    private RecordingCompletionListener listener;
    private MediaPlayer player;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        layout = (LinearLayout) inflater.inflate(R.layout.recording_fragment, container);
        disableScreenRotation((Activity) getContext());
        prepareButtons();
        prepareText();
        setWindowSize();
        fileName = Environment.getExternalStorageDirectory().getAbsolutePath() + FILE_EXT + listener.getFileExtension();

        File f = new File(fileName);
        if(f.exists()){
            reloadSavedRecording();
        }

        return layout;
    }

    private void reloadSavedRecording() {
        recordAgain.setVisibility(View.VISIBLE);
        saveRecording.setVisibility(View.VISIBLE);
        recordingDuration.setVisibility(View.VISIBLE);
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
        saveRecording.setEnabled(true);
        instruction.setText(Localization.get("after.recording"));
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
        header.setText(Localization.get("recording.header"));
        instruction = (TextView) layout.findViewById(R.id.recording_instruction);
        instruction.setText(Localization.get("before.recording"));
        recordingDuration = (Chronometer) layout.findViewById(R.id.recording_time);
    }

    private void prepareButtons() {
        ImageButton discardRecording = (ImageButton) layout.findViewById(R.id.discardrecording);
        toggleRecording = (ImageButton) layout.findViewById(R.id.startrecording);
        saveRecording = (Button) layout.findViewById(R.id.saverecording);
        recordAgain = (Button) layout.findViewById(R.id.recycle);

        recordAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetRecordingView();
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
        recordingProgress = (ProgressBar) layout.findViewById(R.id.demo_mpc);
    }

    private void resetRecordingView() {
        if(recorder != null){
            recorder.release();
            recorder = null;
        }

        if(player != null){
            resetAudioPlayer();
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
        recordAgain.setVisibility(View.INVISIBLE);
        recordingDuration.setVisibility(View.INVISIBLE);
        GoogleAnalyticsUtils.reportRecordingRecycled();
    }

    private void startRecording(){
        disableScreenRotation((Activity) getContext());
        setCancelable(false);

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

        recordingProgress.setVisibility(View.VISIBLE);
        recordingDuration.setVisibility(View.VISIBLE);

        recordingDuration.setBase(SystemClock.elapsedRealtime());
        recordingDuration.start();
    }

    private void setupRecorder() {
        if(recorder == null){
            recorder = new MediaRecorder();
        }

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

        recordingDuration.stop();
        recordAgain.setVisibility(View.VISIBLE);
        recordingProgress.setVisibility(View.INVISIBLE);
        recorder.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
                GoogleAnalyticsUtils.reportAudioPlayed();
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

        String getFileExtension();
    }

    public void setListener(RecordingCompletionListener listener){
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog){
        super.onDismiss(dialog);
        enableScreenRotation((Activity) getContext());
        if(recorder != null){
            recorder.release();
            this.recorder = null;
        }
    }

    public String getFileName(){
        return fileName;
    }

    public static void disableScreenRotation(Activity context) {
        int currentOrientation = context.getResources().getConfiguration().orientation;

        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        else {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    public static void enableScreenRotation(Activity context) {
        context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    protected void playAudio() {

        Uri myPath = Uri.parse(fileName);
        player = MediaPlayer.create(getContext(), myPath);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                resetAudioPlayer();
            }
        });
        recordingDuration.setBase(SystemClock.elapsedRealtime());
        recordingDuration.start();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer();
            }
        });
    }

    private void pauseAudioPlayer(){
        player.pause();
        recordingDuration.stop();
        currentTimeMillis = recordingDuration.getBase();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPlayed();
                resumeAudioPlayer();
            }
        });
    }

    private void resumeAudioPlayer(){
        recordingDuration.setBase(currentTimeMillis);
        recordingDuration.start();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportAudioPaused();
                pauseAudioPlayer();
            }
        });
    }

    private void resetAudioPlayer(){
        player.release();
        recordingDuration.stop();
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
