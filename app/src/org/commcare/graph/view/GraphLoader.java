package org.commcare.graph.view;

import android.webkit.JavascriptInterface;

import org.commcare.activities.CommCareActivity;

/**
 * Created by jenniferschweers on 5/20/16.
 */
public class GraphLoader {
    CommCareActivity activity;
    Runnable thingToDo;

    /** Instantiate the interface and set the context */
    public GraphLoader(CommCareActivity a, Runnable r) {
        activity = a;
        thingToDo = r;
    }

    /** Show a toast from the web page */
    @JavascriptInterface
    public void showGraph() {
        activity.runOnUiThread(thingToDo);
    }
}
