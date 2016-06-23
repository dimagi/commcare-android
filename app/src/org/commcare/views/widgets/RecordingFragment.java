package org.commcare.views.widgets;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.content.DialogInterface;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

/**
 * Created by Saumya on 6/15/2016.
 * A popup dialog fragment that handles recording and saving of audio files without external callout
 */
public class RecordingFragment extends android.support.v4.app.DialogFragment{

    private String fileName;
    private Button start;
    private Button stop;
    private LinearLayout layout;
    private ProgressBar myProgress;
    private final String FILE_EXT = "/Android/data/org.commcare.dalvik/temp/CommCare.mp4";
    private MediaRecorder recorder;
    private RecordingCompletionListener listener;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        layout = (LinearLayout) inflater.inflate(R.layout.recording, container);
        prepareButtons();

        fileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        fileName += FILE_EXT;

        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        layout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        return layout;
    }

    private void prepareButtons() {
        start = (Button) layout.findViewById(R.id.startrecording);
        stop = (Button) layout.findViewById(R.id.stoprecording);
        myProgress = (ProgressBar) layout.findViewById(R.id.recordingprogress);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
        start.setText(Localization.get("start.recording"));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });
        stop.setText(Localization.get("stop.recording"));
        stop.setEnabled(false);
    }

    private void startRecording(){

        disableRotation();

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

        recorder.start();
        myProgress.setVisibility(View.VISIBLE);
        start.setEnabled(false);
        stop.setEnabled(true);
    }

    private void stopRecording(){
        recorder.stop();

        if(listener != null){
            listener.onCompletion();
        }

        dismiss();

        stop.setEnabled(false);
        start.setEnabled(true);
        myProgress.setVisibility(View.GONE);

        enableRotation();
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
            setRecorder(null);
        }
    }

    public MediaRecorder getRecorder(){
        return recorder;
    }

    public void setRecorder(MediaRecorder m){
        recorder = m;
    }

    public String getFileName(){
        return fileName;
    }


    private void disableRotation() {
        int currentOrientation = getResources().getConfiguration().orientation;

        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        else {
            ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    private void enableRotation() {
        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }
}
