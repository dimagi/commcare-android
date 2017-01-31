package org.commcare.graph.util;

import org.commcare.graph.model.GraphData;
import org.commcare.graph.configuration.c3.AxisConfiguration;
import org.commcare.graph.configuration.c3.DataConfiguration;
import org.commcare.graph.configuration.c3.GridConfiguration;
import org.commcare.graph.configuration.c3.LegendConfiguration;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Constants used by graphing
 *
 * @author jschweers
 */
public class GraphUtil {
    public static final String TYPE_XY = "xy";
    public static final String TYPE_BAR = "bar";
    public static final String TYPE_BUBBLE = "bubble";
    public static final String TYPE_TIME = "time";

    /**
     * Get the HTML that will comprise this graph.
     *
     * @param graphData The data to render.
     * @return Full HTML page, including head, body, and all script and style tags
     */
    public static String getHTML(GraphData graphData, String title) throws GraphException {
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

            // For debugging purposes, note that most minified files have un-minified equivalents in the same directory.
            // To use them, switch affix to "max" and get rid of the ignoreAssetsPattern in build.gradle that
            // filters them out of the APK.
            String affix = "min";
            html.append(
                    "<html>" +
                            "<head>" +
                            "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/c3.min.css'></link>" +
                            "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/graph." + affix + ".css'></link>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/d3.min.js'></script>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/c3." + affix + ".js' charset='utf-8'></script>" +
                            "<script type='text/javascript'>try {\n");

            html.append(getVariablesHTML(variables, null));
            html.append(getVariablesHTML(data.getVariables(), "data"));
            html.append(getVariablesHTML(axis.getVariables(), "axis"));
            html.append(getVariablesHTML(grid.getVariables(), "grid"));
            html.append(getVariablesHTML(legend.getVariables(), "legend"));

            String titleHTML = "<div id='chart-title'>" + title + "</div>";
            String errorHTML = "<div id='error'></div>";
            String chartHTML = "<div id='chart'></div>";
            html.append(
                    "\n} catch (e) { displayError(e); }</script>" +
                            "<script type='text/javascript' src='file:///android_asset/graphing/graph." + affix + ".js'></script>" +
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
    private static String getVariablesHTML(SortedMap<String, String> variables, String namespace) {
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
}
