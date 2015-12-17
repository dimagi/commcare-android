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
import org.commcare.dalvik.activities.GraphActivity;
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
    public static final String HTML = "html";
    public static final String TITLE = "title";

    private final Context mContext;
    private final String mTitle;
    private final boolean mIsFullScreen;
    private GraphData mData;

    public GraphView(Context context, String title, boolean isFullScreen) {
        mContext = context;
        mTitle = title;
        mIsFullScreen = isFullScreen;
    }

    public Intent getIntent(String html) throws InvalidStateException {
        Intent intent = new Intent(mContext, GraphActivity.class);
        intent.putExtra(HTML, html);
        intent.putExtra(TITLE, mTitle);
        return intent;
    }

    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public View getView(String html) throws InvalidStateException {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        WebView webView = new WebView(mContext);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);

        webView.setClickable(true);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);

        settings.setBuiltInZoomControls(mIsFullScreen);
        settings.setSupportZoom(mIsFullScreen);
        settings.setDisplayZoomControls(mIsFullScreen);

        // Improve performance
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
        return webView;
    }

    /**
     * Get the HTML that will comprise this graph.
     * @param graphData The data to render.
     * @return Full HTML page, including head, body, and all script and style tags
     */
    public String getHTML(GraphData graphData) throws InvalidStateException {
        mData = graphData;
        OrderedHashtable<String, String> variables = new OrderedHashtable<>();
        JSONObject config = new JSONObject();
        StringBuilder html = new StringBuilder();
        try {
            // Configure data first, as it may affect the other configurations
            DataConfiguration data = new DataConfiguration(mData);
            config.put("data", data.getConfiguration());

            AxisConfiguration axis = new AxisConfiguration(mData);
            config.put("axis", axis.getConfiguration());

            GridConfiguration grid = new GridConfiguration(mData);
            config.put("grid", grid.getConfiguration());

            LegendConfiguration legend = new LegendConfiguration(mData);
            config.put("legend", legend.getConfiguration());

            variables.put("type", "'" + mData.getType() + "'");
            variables.put("config", config.toString());

            html.append(
                    "<html>" +
                            "<head>" +
                            "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/c3.min.css'></link>" +
                            "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/graph.css'></link>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/errors.js'></script>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/d3.min.js'></script>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/c3.min.js' charset='utf-8'></script>" +
                            "<script type='text/javascript'>try {\n");

            html.append(getVariablesHTML(variables, null));
            html.append(getVariablesHTML(data.getVariables(), "data"));
            html.append(getVariablesHTML(axis.getVariables(), "axis"));
            html.append(getVariablesHTML(grid.getVariables(), "grid"));
            html.append(getVariablesHTML(legend.getVariables(), "legend"));

            String titleHTML = "<div id='chart-title'>" + mTitle + "</div>";
            String errorHTML = "<div id='error'></div>";
            String chartHTML = "<div id='chart'></div>";
            html.append(
                    "\n} catch (e) { displayError(e); }</script>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/graph.js'></script>" +
                            "</head>" +
                            "<body>" + titleHTML + errorHTML + chartHTML + "</body>" +
                            "</html>");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return html.toString();
    }

    /**
     * Generate HTML to declare given variables in WebView.
     * @param variables OrderedHashTable where keys are variable names and values are JSON
     *                  representations of values.
     * @param namespace Optional. If provided, instead of declaring a separate variable for each
     *                  item in variables, one object will be declared with namespace for a name
     *                  and a property corresponding to each item in variables.
     * @return HTML string
     */
    private String getVariablesHTML(OrderedHashtable<String, String> variables, String namespace) {
        StringBuilder html = new StringBuilder();
        Enumeration<String> e = variables.keys();
        if (namespace != null && !namespace.equals("")) {
            html.append("var " + namespace + " = {};\n");
        }
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            if (namespace == null || namespace.equals("")) {
                html.append("var " + name);
            } else {
                html.append(namespace + "." + name);
            }
            html.append(" = " + variables.get(name) + ";\n");
        }
        return html.toString();
    }

    /*
     * Get layout params for this graph, which assume that graph will fill parent
     * unless dimensions have been provided via setWidth and/or setHeight.
     */
    public static LinearLayout.LayoutParams getLayoutParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
    }
}