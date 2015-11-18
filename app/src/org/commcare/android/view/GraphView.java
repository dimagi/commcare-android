package org.commcare.android.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;

import org.commcare.android.util.InvalidStateException;
import org.commcare.android.view.c3.AxisConfiguration;
import org.commcare.android.view.c3.DataConfiguration;
import org.commcare.android.view.c3.GridConfiguration;
import org.commcare.android.view.c3.LegendConfiguration;
import org.commcare.dalvik.BuildConfig;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.util.OrderedHashtable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Enumeration;

/*
 * View containing a graph. Note that this does not derive from View; call renderView to get a view for adding to other views, etc.
 * @author jschweers
 */
public class GraphView {
    private final Context mContext;
    private GraphData mData;

    public GraphView(Context context, String title) {
        mContext = context;
         // TODO: display title on graph
    }

    // TODO
    public Intent getIntent(GraphData data) throws InvalidStateException {
        //String title = mRenderer.getChartTitle();
        return null;
    }

    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public View getView(GraphData graphData) throws InvalidStateException {
        mData = graphData;

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        WebView webView = new WebView(mContext);
        configureSettings(webView);

        OrderedHashtable<String, String> variables = new OrderedHashtable<>();
        JSONObject config = new JSONObject();
        try {
            // Configure data first, as it may affect the other configurations
            DataConfiguration data = new DataConfiguration(mData);
            config.put("data", data.getConfiguration());
            variables.putAll(data.getVariables());

            AxisConfiguration axis = new AxisConfiguration(mData);
            config.put("axis", axis.getConfiguration());
            variables.putAll(axis.getVariables());

            GridConfiguration grid = new GridConfiguration(mData);
            config.put("grid", grid.getConfiguration());
            variables.putAll(grid.getVariables());

            LegendConfiguration legend = new LegendConfiguration(mData);
            config.put("legend", legend.getConfiguration());
            variables.putAll(legend.getVariables());
        } catch (JSONException e) {
            throw new RuntimeException("something broke");  // TODO: fix
        }
        // TODO: namespace variables?
        variables.put("type", "'" + mData.getType() + "'");
        variables.put("config", config.toString());

        String html =
                "<html>" +
                    "<head>" +
                        "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/c3.min.css'></link>" +
                        "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/graph.css'></link>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/d3.min.js'></script>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/c3.min.js' charset='utf-8'></script>" +
                        "<script type='text/javascript'>";

        Enumeration<String> e = variables.keys();
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            html += "var " + name + " = " + variables.get(name) + ";\n";
        }

        html +=
                        "</script>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/graph.js'></script>" +
                    "</head>" +
                    "<body><div id='chart'></div></body>" +
                "</html>";
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
        return webView;
    }

    private void configureSettings(WebView view) {
        WebSettings settings = view.getSettings();

        settings.setJavaScriptEnabled(true);

        // Improve performance
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Panning and zooming are allowed only in full-screen graphs (created by getIntent)
        // TODO: Support if this is full-screen view
        settings.setSupportZoom(false);
    }

    /**
     * Fetch date format for displaying time-based x labels.
     *
     * @return String, a SimpleDateFormat pattern.
     */
    private String getTimeFormat() {
        // TODO: fix for C3
        return mData.getConfiguration("x-labels-time-format", "yyyy-MM-dd");
    }

    /*
     * Get layout params for this graph, which assume that graph will fill parent
     * unless dimensions have been provided via setWidth and/or setHeight.
     */
    public static LinearLayout.LayoutParams getLayoutParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    /**
     * Get graph's desired aspect ratio.
     *
     * @return Ratio, expressed as a double: width / height.
     */
    public double getRatio() {
        // Most graphs are drawn with aspect ratio 2:1, which is mostly arbitrary
        // and happened to look nice for partographs. Vertically-oriented graphs,
        // however, get squished unless they're drawn as a square. Expect to revisit 
        // this eventually (make all graphs square? user-configured aspect ratio?).
        // TODO: did migrating to C3 fix the squishing issue?
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            return 1;
        }
        return 2;
    }
}