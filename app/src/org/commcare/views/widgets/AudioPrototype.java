package org.commcare.views.widgets;

<<<<<<< HEAD
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
=======
import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
>>>>>>> c4199c4... New widget records audio successfully
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
<<<<<<< HEAD
import android.widget.ProgressBar;

import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.services.locale.Localization;
=======

import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
>>>>>>> c4199c4... New widget records audio successfully
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
<<<<<<< HEAD
    private ProgressBar myProgress;
    private final String FILE_EXT = "/tester.3gpp";
=======
>>>>>>> c4199c4... New widget records audio successfully

    public AudioPrototype(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic){
        super(context, prompt, pic);

        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
<<<<<<< HEAD
        mFileName += FILE_EXT;
=======
        mFileName += "/tester.3gpp";

        //TODO: Change play button to play directly using the speaker, not some external app
        //TODO: Set up a directory for CommCare audio so that they don't just sit around in the storage directory
        //TODO: Currently recording just puts files somewhere on the file system, and the choose button allows user to select files
        //TODO:        off the file system and copies them into CommCare directory. Change it so that recording saves into that directory,
        //TODO:        and the setBinaryData() method doesn't copy them if they're already there
>>>>>>> c4199c4... New widget records audio successfully
    }

    @Override
    protected void setupLayout(){

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        myLayout = (LinearLayout) inflater.inflate(R.layout.recording, null);

        start = (Button) myLayout.findViewById(R.id.startrecording);
        stop = (Button) myLayout.findViewById(R.id.stoprecording);
<<<<<<< HEAD
        myProgress = (ProgressBar) myLayout.findViewById(R.id.recordingprogress);
=======
>>>>>>> c4199c4... New widget records audio successfully

        start.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
<<<<<<< HEAD
        start.setText(Localization.get("start.recording"));

=======
>>>>>>> c4199c4... New widget records audio successfully
        stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });
<<<<<<< HEAD
        stop.setText(Localization.get("stop.recording"));
        stop.setEnabled(false);
=======
>>>>>>> c4199c4... New widget records audio successfully

        addView(myLayout);
        myLayout.setVisibility(GONE);

        super.setupLayout();
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt){
        myLayout.setVisibility(VISIBLE);
<<<<<<< HEAD
        mCaptureButton.setVisibility(GONE);
        mPlayButton.setVisibility(GONE);
        mChooseButton.setVisibility(GONE);
=======
>>>>>>> c4199c4... New widget records audio successfully
    }

    private void startRecording(){

<<<<<<< HEAD
        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        if(mRecorder == null){
           mRecorder = new MediaRecorder();
       }
=======
        mRecorder = new MediaRecorder();
>>>>>>> c4199c4... New widget records audio successfully
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try{
            mRecorder.prepare();
        }catch(IOException e){
<<<<<<< HEAD
            Log.d("Recorder", "Failed to prepare media recorder");
        }

        mRecorder.start();
        myProgress.setVisibility(VISIBLE);
        start.setEnabled(false);
        stop.setEnabled(true);
=======
            e.printStackTrace();
        }

        mRecorder.start();
>>>>>>> c4199c4... New widget records audio successfully
    }

    private void stopRecording(){
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        myLayout.setVisibility(GONE);
<<<<<<< HEAD

        mCaptureButton.setVisibility(VISIBLE);
        mPlayButton.setVisibility(VISIBLE);
        mChooseButton.setVisibility(VISIBLE);

        stop.setEnabled(false);
        start.setEnabled(true);
        myProgress.setVisibility(GONE);

        ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
=======
>>>>>>> c4199c4... New widget records audio successfully
    }
}
