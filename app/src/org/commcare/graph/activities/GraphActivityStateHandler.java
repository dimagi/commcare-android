package org.commcare.graph.activities;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import org.commcare.core.graph.model.GraphData;
import org.commcare.graph.view.GraphView;

/**
 * Handle state change logic for graph activities.
 *
 * Created by jschweers on 11/20/2015.
 */
public class GraphActivityStateHandler {
    private final AppCompatActivity activity;

    public GraphActivityStateHandler(AppCompatActivity a) {
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

        GraphData html = (GraphData) extras.getSerializable(GraphView.HTML);
        GraphView graphView = new GraphView(activity, title, true);
        View webView = graphView.getView(html);
        activity.setContentView(webView);
    }
}
