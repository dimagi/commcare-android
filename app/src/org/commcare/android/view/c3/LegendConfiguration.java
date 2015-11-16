package org.commcare.android.view.c3;

import org.commcare.suite.model.graph.GraphData;
import org.json.JSONException;

/**
 * Created by jschweers on 11/16/2015.
 */
public class LegendConfiguration extends Configuration {
    public LegendConfiguration(GraphData data) throws JSONException {
        super(data);
        if (Boolean.valueOf(mData.getConfiguration("show-legend", "false")).equals(Boolean.FALSE)) {
            mConfiguration.put("show", false);
        }
    }
}
