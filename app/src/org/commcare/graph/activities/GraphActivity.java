package org.commcare.graph.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.graph.activities.GraphActivityStateHandler;

/**
 * Full-screen view of a graph.
 *
 * Created by jschweers on 11/20/2015.
 */
public class GraphActivity extends FragmentActivity {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        (new GraphActivityStateHandler(this)).setContent();
    }
}