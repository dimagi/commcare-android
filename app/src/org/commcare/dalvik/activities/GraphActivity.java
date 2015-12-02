package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebView;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.view.GraphView;

/**
 * Full-screen view of a graph.
 *
 * Created by jschweers on 11/20/2015.
 */
public class GraphActivity extends CommCareActivity {

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
        GraphView graphView = new GraphView(this, title, true);
        try {
            WebView webView = (WebView) graphView.getView(html);
            setContentView(webView);
        } catch (InvalidStateException e) {
            e.printStackTrace();
            finish();
        }
    }
}