package org.commcare.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;

import org.commcare.google.services.analytics.GoogleAnalyticsUtils;
import org.commcare.graph.activities.GraphActivityStateHandler;
import org.commcare.logging.AndroidLogger;
import org.javarosa.core.services.Logger;

/**
 * Full-screen view of a graph.
 *
 * Created by jschweers on 11/20/2015.
 */
public class CommCareGraphActivity extends CommCareActivity {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        (new GraphActivityStateHandler(this)).setContent();
        GoogleAnalyticsUtils.reportGraphViewFullScreenOpened();
        Logger.log(AndroidLogger.TYPE_GRAPHING, "Start viewing full screen graph");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GoogleAnalyticsUtils.reportGraphViewFullScreenClosed();
        Logger.log(AndroidLogger.TYPE_GRAPHING, "End viewing full screen graph");
    }
}