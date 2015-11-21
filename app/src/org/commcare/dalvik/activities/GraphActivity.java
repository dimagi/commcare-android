package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.LinearLayout;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.view.GraphView;
import org.commcare.suite.model.graph.GraphData;

/**
 * Full-screen view of a graph.
 *
 * Created by jschweers on 11/20/2015.
 */
public class GraphActivity extends CommCareActivity {
    private GraphView mView;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        String title = extras.getString(GraphView.TITLE);
        if (title == null) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else if (title.length() > 0) {
            setTitle(title);
        }

        String html = extras.getString(GraphView.HTML);
        mView = new GraphView(this, title, true);
        try {
            WebView view = (WebView) mView.getView(html);
            setContentView(view);
        } catch (InvalidStateException e) {
            e.printStackTrace();
        }
    }
}