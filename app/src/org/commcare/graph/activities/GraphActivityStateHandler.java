package org.commcare.graph.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebView;

import org.commcare.graph.view.GraphView;

/**
 * Handle state change logic for graph activities.
 *
 * Created by jschweers on 11/20/2015.
 */
public class GraphActivityStateHandler {
    protected Activity activity;

    public GraphActivityStateHandler(Activity a) {
        activity = a;
    }

    public void setContent() {
        Bundle extras = activity.getIntent().getExtras();
        String title = extras.getString(GraphView.TITLE);
        if (title == null) {
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else if (title.length() > 0) {
            activity.setTitle(title);
        }

        String html = extras.getString(GraphView.HTML);
        GraphView graphView = new GraphView(activity, title, true);
        WebView webView = (WebView)graphView.getView(html);
        activity.setContentView(webView);
    }
}