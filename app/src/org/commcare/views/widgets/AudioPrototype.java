package org.commcare.views.widgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;

import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.IOException;

/**
 * Created by Saumya on 6/3/2016.
 */
public class AudioPrototype extends AudioWidget{

    private MediaRecorder mRecorder;
    private String mFileName;
    private Button start;
    private Button stop;
    private LinearLayout myLayout;

    public AudioPrototype(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic){
        super(context, prompt, pic);

        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/tester.3gpp";

        //TODO: Figure out what to do with files. Store them all on file system (What dir?)? Delete them every time? Overwrite every time?
        //TODO: Figure out how to preserve recording view and mRecorder when screen is turned...for now I just disabled rotation while recording.
    }

    @Override
    protected void setupLayout(){

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        myLayout = (LinearLayout) inflater.inflate(R.layout.recording, null);

        start = (Button) myLayout.findViewById(R.id.startrecording);
        stop = (Button) myLayout.findViewById(R.id.stoprecording);

        start.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
        stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });
        stop.setEnabled(false);

        addView(myLayout);
        myLayout.setVisibility(GONE);

        super.setupLayout();
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt){
        myLayout.setVisibility(VISIBLE);
        mCaptureButton.setVisibility(GONE);
        mPlayButton.setVisibility(GONE);
        mChooseButton.setVisibility(GONE);
    }

    private void startRecording(){

        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

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
            e.printStackTrace();
        }

        mRecorder.start();
        stop.setEnabled(true);
    }

    private void stopRecording(){
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        myLayout.setVisibility(GONE);

        mCaptureButton.setVisibility(VISIBLE);
        mPlayButton.setVisibility(VISIBLE);
        mChooseButton.setVisibility(VISIBLE);

        stop.setEnabled(false);

        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

    }
}
