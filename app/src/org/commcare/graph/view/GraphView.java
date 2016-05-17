package org.commcare.graph.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;

import org.commcare.dalvik.BuildConfig;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphException;
import org.commcare.graph.util.GraphUtil;
import org.commcare.graph.view.c3.AxisConfiguration;
import org.commcare.graph.view.c3.DataConfiguration;
import org.commcare.graph.view.c3.GridConfiguration;
import org.commcare.graph.view.c3.LegendConfiguration;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * View containing a graph. Note that this does not derive from View; call renderView to get a view for adding to other views, etc.
 *
 * @author jschweers
 */
public class GraphView {
    public static final String HTML = "html";
    public static final String TITLE = "title";

    private final Context mContext;
    private final String mTitle;
    private final boolean mIsFullScreen;

    public GraphView(Context context, String title, boolean isFullScreen) {
        mContext = context;
        mTitle = title;
        mIsFullScreen = isFullScreen;
    }

    public Intent getIntent(String html, Class className) {
        Intent intent = new Intent(mContext, className);
        intent.putExtra(HTML, html);
        intent.putExtra(TITLE, mTitle);
        return intent;
    }

    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public View getView(String html) {
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
     *
     * @param graphData The data to render.
     * @return Full HTML page, including head, body, and all script and style tags
     */
    public String getHTML(GraphData graphData) throws GraphException {
        SortedMap<String, String> variables = new TreeMap<>();
        JSONObject config = new JSONObject();
        StringBuilder html = new StringBuilder();
        try {
            // Configure data first, as it may affect the other configurations
            DataConfiguration data = new DataConfiguration(graphData);
            config.put("data", data.getConfiguration());

            AxisConfiguration axis = new AxisConfiguration(graphData);
            config.put("axis", axis.getConfiguration());

            GridConfiguration grid = new GridConfiguration(graphData);
            config.put("grid", grid.getConfiguration());

            LegendConfiguration legend = new LegendConfiguration(graphData);
            config.put("legend", legend.getConfiguration());

            variables.put("type", "'" + graphData.getType() + "'");
            variables.put("config", config.toString());

            html.append(
                    "<html>" +
                            "<head>" +
                            "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/c3.min.css'></link>" +
                            "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/graph.min.css'></link>" +
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
                            "<script type='text/javascript' src='file:///android_asset/graphing/graph.min.js'></script>" +
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
     *
     * @param variables OrderedHashTable where keys are variable names and values are JSON
     *                  representations of values.
     * @param namespace Optional. If provided, instead of declaring a separate variable for each
     *                  item in variables, one object will be declared with namespace for a name
     *                  and a property corresponding to each item in variables.
     * @return HTML string
     */
    private String getVariablesHTML(SortedMap<String, String> variables, String namespace) {
        StringBuilder html = new StringBuilder();
        if (namespace != null && !namespace.equals("")) {
            html.append("var ").append(namespace).append(" = {};\n");
        }
        for (String name : variables.keySet()) {
            if (namespace == null || namespace.equals("")) {
                html.append("var ").append(name);
            } else {
                html.append(namespace).append(".").append(name);
            }
            html.append(" = ").append(variables.get(name)).append(";\n");
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
    
    /**
     * Get graph's desired aspect ratio.
     * Most graphs are drawn with aspect ratio 2:1, which is fairly arbitrary
     * and happened to look nice for partographs. Bar graphs are drawn square - 
     * again, arbitrary, happens to look nice for mobile UCR. Expect to revisit
     * this eventually (make all graphs square? user-configured aspect ratio?).
     *
     * @return Ratio, expressed as a double: width / height.
     */
    public double getRatio(GraphData data) {
        if (GraphUtil.TYPE_BAR.equals(data.getType())) {
            return 1;
        }
        return 2;
    }
}
