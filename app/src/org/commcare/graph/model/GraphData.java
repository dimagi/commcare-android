package org.commcare.graph.model;

import org.commcare.graph.util.GraphException;
import org.commcare.graph.view.c3.AxisConfiguration;
import org.commcare.graph.view.c3.DataConfiguration;
import org.commcare.graph.view.c3.GridConfiguration;
import org.commcare.graph.view.c3.LegendConfiguration;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Hashtable;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Contains all of the fully-evaluated data to draw a graph: a type, set of series, set of text annotations, and key-value map of configuration.
 *
 * @author jschweers
 */
public class GraphData implements ConfigurableData {
    private String mType;
    private final Vector<SeriesData> mSeries;
    private final Hashtable<String, String> mConfiguration;
    private final Vector<AnnotationData> mAnnotations;

    public GraphData() {
        mSeries = new Vector<>();
        mConfiguration = new Hashtable<>();
        mAnnotations = new Vector<>();
    }

    public String getType() {
        return mType;
    }

    public void setType(String type) {
        mType = type;
    }

    public Vector<SeriesData> getSeries() {
        return mSeries;
    }

    public void addSeries(SeriesData s) {
        mSeries.addElement(s);
    }

    public void addAnnotation(AnnotationData a) {
        mAnnotations.addElement(a);
    }

    public Vector<AnnotationData> getAnnotations() {
        return mAnnotations;
    }

    @Override
    public void setConfiguration(String key, String value) {
        mConfiguration.put(key, value);
    }

    @Override
    public String getConfiguration(String key) {
        return mConfiguration.get(key);
    }

    @Override
    public String getConfiguration(String key, String defaultValue) {
        String value = getConfiguration(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * @return The full HTML page that will comprise this graph (including head, body, and all
     * script and style tags)
     */
    public String getGraphHTML(String title) throws GraphException {
        SortedMap<String, String> variables = new TreeMap<>();
        JSONObject config = new JSONObject();
        StringBuilder html = new StringBuilder();
        try {
            // Configure data first, as it may affect the other configurations
            DataConfiguration data = new DataConfiguration(this);
            config.put("data", data.getConfiguration());

            AxisConfiguration axis = new AxisConfiguration(this);
            config.put("axis", axis.getConfiguration());

            GridConfiguration grid = new GridConfiguration(this);
            config.put("grid", grid.getConfiguration());

            LegendConfiguration legend = new LegendConfiguration(this);
            config.put("legend", legend.getConfiguration());

            variables.put("type", "'" + this.getType() + "'");
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
            throw new GraphException(e.getMessage());
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
