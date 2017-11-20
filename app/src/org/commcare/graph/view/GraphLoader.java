package org.commcare.graph.view;

import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.JavascriptInterface;

import java.util.TimerTask;

/**
 * Created by jenniferschweers on 5/20/16.
 *
 * Interface between Android's GraphView and the JavaScript graphing code.
 * Its responsibility is to hide a spinner that displays while the graph loads.
 */
public class GraphLoader extends TimerTask {
    private final AppCompatActivity activity;
    private final View spinner;

    public GraphLoader(AppCompatActivity a, View v) {
        activity = a;
        spinner = v;
    }

    @JavascriptInterface
    @Override
    public void run() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(View.GONE);
            }
        });
    }
}
