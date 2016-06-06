package org.commcare.views.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
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

        //TODO: Change play button to play directly using the speaker, not some external app
        //TODO: Set up a directory for CommCare audio so that they don't just sit around in the storage directory
        //TODO: Currently recording just puts files somewhere on the file system, and the choose button allows user to select files
        //TODO:        off the file system and copies them into CommCare directory. Change it so that recording saves into that directory,
        //TODO:        and the setBinaryData() method doesn't copy them if they're already there
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

        addView(myLayout);
        myLayout.setVisibility(GONE);

        super.setupLayout();
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt){
        myLayout.setVisibility(VISIBLE);
    }

    private void startRecording(){

        mRecorder = new MediaRecorder();
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
    }

    private void stopRecording(){
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        myLayout.setVisibility(GONE);
    }
}
