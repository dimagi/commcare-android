package org.commcare.android.view.c3;

import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.util.OrderedHashtable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Grid-related configuration for C3.
 *
 * Created by jschweers on 11/16/2015.
 */
public class GridConfiguration extends Configuration {
    public GridConfiguration(GraphData data) throws JSONException {
        super(data);

        if (Boolean.valueOf(mData.getConfiguration("show-grid", "true")).equals(Boolean.TRUE)) {
            JSONObject show = new JSONObject("{ show: true }");
            mConfiguration.put("x", show);
            mConfiguration.put("y", show);
        }
    }
}
