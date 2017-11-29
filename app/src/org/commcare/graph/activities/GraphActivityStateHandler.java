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
    private final Activity activity;

    public GraphActivityStateHandler(Activity a) {
        activity = a;
    }

    public void setContent() {
        Bundle extras = activity.getIntent().getExtras();
        String title = extras.getString(GraphView.TITLE_EXTRA);
        if (title == null) {
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else if (title.length() > 0) {
            activity.setTitle(title);
        }

        String detailId = extras.getString(GraphView.DETAIL_ID_EXTRA);
        String html = extras.getString(GraphView.HTML_EXTRA);
        GraphView graphView = new GraphView(activity, title, true, detailId);
        WebView webView = graphView.getView(html);
        activity.setContentView(webView);
    }
}