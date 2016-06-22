package org.commcare.views.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.io.IOException;

/**
 * Created by Saumya on 6/3/2016.
 */
public class AudioPrototype extends AudioWidget implements RecordingFragment.DismissListener{

    private RecordingFragment recorder;
    private FragmentManager fm;

    public AudioPrototype(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic){
        super(context, prompt, pic);
        fm = ((FragmentActivity) getContext()).getSupportFragmentManager();
        recorder = new RecordingFragment();

        recorder.setListener(this);
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt){
        recorder.show(fm, "Recorder");
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
    public void onDismiss() {
        MediaRecorder temp = recorder.getRecorder();

        if(temp != null){
            //temp.stop();
            temp.release();
            recorder.setRecorder(null);
        }
    }

    @Override
    public void onCompletion(){
        setBinaryData(recorder.getFileName());
        mPlayButton.setEnabled(true);
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

        mPlayButton.setText("Pause Sound");
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
        mPlayButton.setText("Resume Audio");
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mp.start();
            }
        });
    }

    private void resetAudioPlayer(MediaPlayer mp){
        mp.release();
        mPlayButton.setText("Play Audio");
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
    }
}
