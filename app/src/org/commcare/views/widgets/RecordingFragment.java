package org.commcare.views.widgets;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
 */
public class RecordingFragment extends android.support.v4.app.DialogFragment{

    private String mFileName;
    private Button start;
    private Button stop;
    private LinearLayout myLayout;
    private ProgressBar myProgress;
    private final String FILE_EXT = "/Android/data/org.commcare.dalvik/temp/CommCare.3gpp";
    private MediaRecorder mRecorder;
    private DismissListener myListener;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        myLayout = (LinearLayout) inflater.inflate(R.layout.recording, container);

        prepareButtons();

        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += FILE_EXT;

        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        myLayout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        return myLayout;
    }

    private void prepareButtons() {
        start = (Button) myLayout.findViewById(R.id.startrecording);
        stop = (Button) myLayout.findViewById(R.id.stoprecording);
        myProgress = (ProgressBar) myLayout.findViewById(R.id.recordingprogress);

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

        if(mRecorder == null){
            mRecorder = new MediaRecorder();
        }

        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try{
            mRecorder.prepare();
        }catch(IOException e){
            Log.d("Recorder", "Failed to prepare media recorder");
        }

        mRecorder.start();
        myProgress.setVisibility(View.VISIBLE);
        start.setEnabled(false);
        stop.setEnabled(true);
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

    private void stopRecording(){
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;

        stop.setEnabled(false);
        start.setEnabled(true);
        myProgress.setVisibility(View.GONE);

        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        if(myListener != null){
            myListener.onCompletion();
        }

        dismiss();
    }

    public interface DismissListener{
        void onDismiss();
        void onCompletion();
    }

    public void setListener(DismissListener listener){
        myListener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog){
        super.onDismiss(dialog);
        if(myListener != null){
            myListener.onDismiss();
        }
    }

    public MediaRecorder getRecorder(){
        return mRecorder;
    }

    public void setRecorder(MediaRecorder m){
        mRecorder = m;
    }

    public String getFileName(){
        return mFileName;
    }
}
