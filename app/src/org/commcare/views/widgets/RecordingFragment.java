package org.commcare.views.widgets;

import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

/**
 * A popup dialog fragment that handles recording_fragment and saving of audio
 * files without external callout.
 *
 * @author Saumya Jain (sjain@dimagi.com)
 */
public class RecordingFragment extends DialogFragment {

    public static final String AUDIO_FILE_PATH_ARG_KEY = "audio_file_path";

    private String fileName;
    private static final String FILE_EXT = ".mp4";

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        layout = (LinearLayout)inflater.inflate(R.layout.recording_fragment, container);
        disableScreenRotation((AppCompatActivity)getContext());
        prepareButtons();
        prepareText();
        setWindowSize();

        Bundle args = getArguments();
        if (args != null) {
            fileName = args.getString(AUDIO_FILE_PATH_ARG_KEY);
        }

        if (fileName == null) {
            initAudioFile();
        }

        File f = new File(fileName);
        if (f.exists()) {
            reloadSavedRecording();
        }

        return layout;
    }

    private void initAudioFile() {
        fileName = CommCareApplication.instance().getAndroidFsTemp() + new Date().getTime() + FILE_EXT;
    }

    private void reloadSavedRecording() {
        recordAgain.setVisibility(View.VISIBLE);
        saveRecording.setVisibility(View.GONE);
        recordingDuration.setVisibility(View.VISIBLE);
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> playAudio());
        saveRecording.setEnabled(true);
        instruction.setText(Localization.get("after.recording"));
    }

    private void setWindowSize() {
        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        layout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }

    private void prepareText() {
        TextView header = layout.findViewById(R.id.recording_header);
        header.setText(Localization.get("recording.header"));
        instruction = layout.findViewById(R.id.recording_instruction);
        instruction.setText(Localization.get("before.recording"));
        recordingDuration = layout.findViewById(R.id.recording_time);
    }

    private void prepareButtons() {
        ImageButton discardRecording = layout.findViewById(R.id.discardrecording);
        toggleRecording = layout.findViewById(R.id.startrecording);
        saveRecording = layout.findViewById(R.id.saverecording);
        recordAgain = layout.findViewById(R.id.recycle);

        recordAgain.setOnClickListener(v -> resetRecordingView());

        discardRecording.setOnClickListener(v -> dismiss());

        toggleRecording.setOnClickListener(v -> startRecording());
        saveRecording.setOnClickListener(v -> saveRecording());
        saveRecording.setText(Localization.get("save"));
        recordingProgress = layout.findViewById(R.id.demo_mpc);
    }

    private void resetRecordingView() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }

        if (player != null) {
            resetAudioPlayer();
        }

        // reset the file path
        initAudioFile();

        toggleRecording.setBackgroundResource(R.drawable.record_start);
        toggleRecording.setOnClickListener(v -> startRecording());
        instruction.setText(Localization.get("before.recording"));
        saveRecording.setVisibility(View.INVISIBLE);
        recordAgain.setVisibility(View.INVISIBLE);
        recordingDuration.setVisibility(View.INVISIBLE);
    }

    private void startRecording() {
        disableScreenRotation((AppCompatActivity)getContext());
        setCancelable(false);

        setupRecorder();
        recorder.start();

        toggleRecording.setOnClickListener(v -> stopRecording());
        toggleRecording.setBackgroundResource(R.drawable.record_in_progress);
        instruction.setText(Localization.get("during.recording"));

        recordingProgress.setVisibility(View.VISIBLE);
        recordingDuration.setVisibility(View.VISIBLE);

        recordingDuration.setBase(SystemClock.elapsedRealtime());
        recordingDuration.start();
    }

    private void setupRecorder() {
        if (recorder == null) {
            recorder = new MediaRecorder();
        }

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {

        recordingDuration.stop();
        recordAgain.setVisibility(View.VISIBLE);
        recordingProgress.setVisibility(View.INVISIBLE);
        recorder.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> playAudio());
        saveRecording.setEnabled(true);
        saveRecording.setVisibility(View.VISIBLE);
        instruction.setText(Localization.get("after.recording"));
    }

    private void saveRecording() {
        if (listener != null) {
            listener.onRecordingCompletion(fileName);
        }
        dismiss();
    }

    public interface RecordingCompletionListener {
        void onRecordingCompletion(String audioFile);
    }

    public void setListener(RecordingCompletionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        enableScreenRotation((AppCompatActivity)getContext());
        if (recorder != null) {
            recorder.release();
            this.recorder = null;
        }

        if (player != null) {
            try {
                player.release();
            } catch (IllegalStateException e) {
                //Do nothing because player wasn't recording
            }
        }
    }

    private static void disableScreenRotation(AppCompatActivity context) {
        int currentOrientation = context.getResources().getConfiguration().orientation;

        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    private static void enableScreenRotation(AppCompatActivity context) {
        context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    private void playAudio() {

        Uri myPath = Uri.parse(fileName);
        player = MediaPlayer.create(getContext(), myPath);
        player.setOnCompletionListener(mp -> resetAudioPlayer());
        recordingDuration.setBase(SystemClock.elapsedRealtime());
        recordingDuration.start();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(v -> pauseAudioPlayer());
    }

    private void pauseAudioPlayer() {
        player.pause();
        recordingDuration.stop();
        currentTimeMillis = recordingDuration.getBase();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> resumeAudioPlayer());
    }

    private void resumeAudioPlayer() {
        recordingDuration.setBase(currentTimeMillis);
        recordingDuration.start();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(v -> pauseAudioPlayer());
    }

    private void resetAudioPlayer() {
        player.release();
        recordingDuration.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> playAudio());
    }
}
