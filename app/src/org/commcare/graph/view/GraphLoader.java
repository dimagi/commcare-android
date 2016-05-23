package org.commcare.graph.view;

import android.app.Activity;
import android.webkit.JavascriptInterface;

import org.commcare.activities.CommCareActivity;

/**
 * Created by jenniferschweers on 5/20/16.
 *
 * Interface between Android's GraphView and the JavaScript graphing code.
 */
public class GraphLoader {
    Activity activity;
    Runnable onRendered;

    public GraphLoader(Activity a, Runnable r) {
        activity = a;
        onRendered = r;
    }

    /**
     * Run any android code that wants to wait for the graph to finish rendering.
     */
    @JavascriptInterface
    public void onRendered() {
        activity.runOnUiThread(onRendered);
    }
}
