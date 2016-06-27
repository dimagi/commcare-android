package org.commcare.views.widgets;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.content.DialogInterface;
import android.widget.TextView;

import com.liulishuo.magicprogresswidget.MagicProgressCircle;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

/**
 * Created by Saumya on 6/15/2016.
 * A popup dialog fragment that handles recording_fragment and saving of audio files without external callout
 * Progress circle graphic from Jacksgong at https://github.com/lingochamp/MagicProgressWidget
 */
public class RecordingFragment extends android.support.v4.app.DialogFragment{

    private String fileName;
    private final String FILE_EXT = "/Android/data/org.commcare.dalvik/temp/Custom Recording.mp4";
    public static final int MAX_DURATION_MS = 120000;

    private ImageButton toggleRecording;
    private LinearLayout layout;
    private Button saveRecording;
    private TextView instruction;

    private MediaRecorder recorder;
    private RecordingCompletionListener listener;
    private MagicProgressCircle mpc;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        layout = (LinearLayout) inflater.inflate(R.layout.recording_fragment, container);
        enableScreenRotation();
        prepareButtons();
        prepareText();
        setWindowSize();
        fileName = Environment.getExternalStorageDirectory().getAbsolutePath() + FILE_EXT;

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
    }

    private void prepareButtons() {
        toggleRecording = (ImageButton) layout.findViewById(R.id.startrecording);
        saveRecording = (Button) layout.findViewById(R.id.saverecording);
        toggleRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
        saveRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRecording();
            }
        });
        saveRecording.setText(Localization.get("save"));
        mpc = (MagicProgressCircle) layout.findViewById(R.id.demo_mpc);
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
                stopRecording();
            }
        });
        toggleRecording.setBackgroundResource(R.drawable.record_in_progress);
        instruction.setText(Localization.get("during.recording"));

        mpc.setVisibility(View.VISIBLE);
        mpc.setSmoothPercent(new Float(1.0), MAX_DURATION_MS);
    }

    private void setupRecorder() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setMaxDuration(MAX_DURATION_MS);
        recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
                    stopRecording();
                }
            }
        });

        try{
            recorder.prepare();
        }catch(IOException e){
            Log.d("Recorder", "Failed to prepare media recorder");
        }
    }

    private void stopRecording(){

        mpc.setPercent(mpc.getPercent());
        mpc.setStartColor(getResources().getColor(R.color.blue_grey));
        mpc.setEndColor(getResources().getColor(R.color.blue_grey));

        recorder.stop();
        toggleRecording.setEnabled(false);
        toggleRecording.setBackgroundResource(R.drawable.record_complete);
        saveRecording.setEnabled(true);
        instruction.setText(Localization.get("after.recording"));
        saveRecording.setTextColor(getResources().getColor(R.color.white));
        saveRecording.setBackgroundColor(getResources().getColor(R.color.cc_attention_positive_color));
    }

    public void saveRecording(){
        if(listener != null){
            listener.onCompletion();
        }
        dismiss();
    }

    public interface RecordingCompletionListener {
        void onCompletion();
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
}
