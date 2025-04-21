package org.commcare.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;

import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.graph.activities.GraphActivityStateHandler;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

/**
 * Full-screen view of a graph.
 *
 * Created by jschweers on 11/20/2015.
 */
public class CommCareGraphActivity extends CommCareActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        (new GraphActivityStateHandler(this)).setContent();
        FirebaseAnalyticsUtil.reportGraphViewFullScreenOpened();

        String title = getTitle() == null || getTitle().length() == 0 ? "(no title)" : getTitle().toString();
        Logger.log(LogTypes.TYPE_GRAPHING,
                String.format("Start viewing full screen graph for %s", title));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseAnalyticsUtil.reportGraphViewFullScreenClosed();
    }
}
