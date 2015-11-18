package org.commcare.android.view.c3;

import org.commcare.suite.model.graph.GraphData;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Legend-related configuration for C3.
 *
 * Created by jschweers on 11/16/2015.
 */
public class LegendConfiguration extends Configuration {
    public LegendConfiguration(GraphData data) throws JSONException {
        super(data);

        // Respect user's preference for showing legend
        boolean showLegend = Boolean.valueOf(mData.getConfiguration("show-legend", "false"));
        if (!showLegend) {
            mConfiguration.put("show", false);
        }

        // Keep system-generated series out of the legend
        JSONArray systemIDs = new JSONArray();
        systemIDs.put("annotationsY");
        systemIDs.put("boundsY");
        systemIDs.put("boundsY2");
        mConfiguration.put("hide", systemIDs);
    }
}
