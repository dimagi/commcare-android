package org.commcare.android.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;

import org.achartengine.ChartFactory;
import org.achartengine.chart.BarChart;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.commcare.android.models.RangeXYValueSeries;
import org.commcare.android.util.InvalidStateException;
import org.commcare.dalvik.R;
import org.commcare.suite.model.graph.AnnotationData;
import org.commcare.suite.model.graph.BubblePointData;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.SeriesData;
import org.commcare.suite.model.graph.XYPointData;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

/*
 * View containing a graph. Note that this does not derive from View; call renderView to get a view for adding to other views, etc.
 * @author jschweers
 */
public class GraphView {
    private Context mContext;
    private int mTextSize;
    private GraphData mData;
    private XYMultipleSeriesDataset mDataset;
    private XYMultipleSeriesRenderer mRenderer;

    public GraphView(Context context, String title) {
        mContext = context;
        mTextSize = (int)context.getResources().getDimension(R.dimen.text_large);
        mDataset = new XYMultipleSeriesDataset();
        mRenderer = new XYMultipleSeriesRenderer(2);    // initialize with two scales, to support a secondary y axis

        mRenderer.setChartTitle(title);
        mRenderer.setChartTitleTextSize(mTextSize);
    }

    private void render(GraphData data) throws InvalidStateException {
        mRenderer.setInScroll(true);
        for (SeriesData s : data.getSeries()) {
            renderSeries(s);
        }

        renderAnnotations();

        configure();
    }

    public Intent getIntent(GraphData data) throws InvalidStateException {
        render(data);

        String title = mRenderer.getChartTitle();
        if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
            return ChartFactory.getBubbleChartIntent(mContext, mDataset, mRenderer, title);
        }
        if (Graph.TYPE_TIME.equals(mData.getType())) {
            return ChartFactory.getTimeChartIntent(mContext, mDataset, mRenderer, getTimeFormat(), title);
        }
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            return ChartFactory.getBarChartIntent(mContext, mDataset, mRenderer, getBarChartType(), title);
        }
        return ChartFactory.getLineChartIntent(mContext, mDataset, mRenderer, title);
    }

    private BarChart.Type getBarChartType() {
        if (Boolean.valueOf(mData.getConfiguration("stack", "false")).equals(Boolean.TRUE)) {
            return BarChart.Type.STACKED;
        }
        return BarChart.Type.DEFAULT;
    }

    private JSONObject getC3AxisConfig() throws InvalidStateException, JSONException {
        JSONObject x = new JSONObject();
        JSONObject y = new JSONObject();
        JSONObject y2 = new JSONObject();
        if (Boolean.valueOf(mData.getConfiguration("show-axes", "true")).equals(Boolean.FALSE)) {
            JSONObject show = new JSONObject("{ show: false }");
            x = show;
            y = show;
            y2 = show;
        } else {
            // Undo C3's automatic axis padding
            JSONObject padding = new JSONObject("{top: 0, right: 0, bottom: 0, left: 0}");
            x.put("padding", padding);
            y.put("padding", padding);
            y2.put("padding", padding);

            // Axis titles
            addC3AxisLabel(x, "x-title", "outer-center");
            addC3AxisLabel(y, "y-title", "outer-middle");
            addC3AxisLabel(y2, "secondary-y-title", "outer-middle");

            // Min and max boundaries
            // TODO: verify x-min and x-max work with time-based graphs
            if (mData.getConfiguration("x-min") != null) {
                x.put("min", parseXValue(mData.getConfiguration("x-min"), "x-min"));
            }
            if (mData.getConfiguration("x-max") != null) {
                x.put("max", parseXValue(mData.getConfiguration("x-max"), "x-max"));
            }
            if (mData.getConfiguration("y-min") != null) {
                y.put("min", parseYValue(mData.getConfiguration("y-min"), "y-min"));
            }
            if (mData.getConfiguration("y-max") != null) {
                y.put("max", parseYValue(mData.getConfiguration("y-max"), "y-max"));
            }
            if (mData.getConfiguration("secondary-y-min") != null) {
                y2.put("min", parseYValue(mData.getConfiguration("secondary-y-min"), "secondary-y-min"));
            }
            if (mData.getConfiguration("secondary-y-max") != null) {
                y2.put("max", parseYValue(mData.getConfiguration("secondary-y-max"), "secondary-y-max"));
            }

            // Determine whether secondary y axis should display
            for (SeriesData s : mData.getSeries()) {
                if (Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE)) {
                    y2.put("show", true);
                    break;
                }
            }

            // Axis tick labels
            addC3AxisTickConfig(x, "x-labels");
            addC3AxisTickConfig(y, "y-labels");
            addC3AxisTickConfig(y2, "secondary-y-labels");
        }

        JSONObject config = new JSONObject();
        config.put("x", x);
        config.put("y", y);
        config.put("y2", y2);
        return config;
    }

    private void addC3AxisTickConfig(JSONObject axis, String key) throws InvalidStateException, JSONException {
        // The labels configuration might be a JSON array of numbers,
        // a JSON object of number => string, or a single number
        String labelString = mData.getConfiguration(key);
        JSONObject tick = new JSONObject();

        if (labelString != null) {
            try {
                // Array: label each given value
                JSONArray labels = new JSONArray(labelString);
                JSONArray values = new JSONArray();
                for (int i = 0; i < labels.length(); i++) {
                    values.put(parseXValue(labels.getString(i), key));   // TODO: verify this works for time graphs
                }
                tick.put("values", values);
            } catch (JSONException je) {
                // Assume try block failed because labelString isn't an array.
                // Try parsing it as an object.
                try {
                    // Object: each key is a location on the axis,
                    // and the value is text with which to label it
                    JSONObject labels = new JSONObject(labelString);
                    JSONArray values = new JSONArray();
                    Iterator i = labels.keys();
                    while (i.hasNext()) {
                        String location = (String)i.next();
                        values.put(parseXValue(location, key));
                    }
                    tick.put("values", values);
                    tick.put("format", labels);     // TODO: verify this works for time graphs
                } catch (JSONException e) {
                    // Assume labelString is just a scalar, which
                    // represents the number of labels the user wants.
                    tick.put("count", Integer.valueOf(labelString));
                }
            }
        }

        axis.put("tick", tick);
    }

    private void addC3AxisLabel(JSONObject axis, String key, String position) throws JSONException {
        String title = mData.getConfiguration(key, "");
        title = title.replaceAll("^\\s*", "");
        title = title.replaceAll("\\s*$", "");
        if (!"".equals(title)) {
            JSONObject label = new JSONObject();
            label.put("text", title);
            label.put("position", position);
            axis.put("label", label);
        }
    }

    private JSONObject getC3DataConfig() throws InvalidStateException, JSONException {
        // Actual data: array of arrays, where first element is a string id
        // and later elements are data, either x values or y values.
        JSONArray columns = new JSONArray();

        // Hash that pairs up the arrays defined in columns,
        // y-values-array-id => x-values-array-id
        JSONObject xs = new JSONObject();

        // Hash of y-values id => name for legend
        JSONObject names = new JSONObject();

        // Hash of y-values id => 'y' or 'y2' depending on whether this data
        // should be plotted against the primary or secondary y axis
        JSONObject axes = new JSONObject();

        int seriesIndex = 0;
        for (SeriesData s : mData.getSeries()) {
            JSONArray xValues = new JSONArray();
            JSONArray yValues = new JSONArray();

            String xID = "x" + seriesIndex;
            String yID = "y" + seriesIndex;
            xs.put(yID, xID);

            xValues.put(xID);
            yValues.put(yID);
            for (XYPointData p : s.getPoints()) {
                String description = "point (" + p.getX() + ", " + p.getY() + ")";
                xValues.put(parseXValue(p.getX(), description));
                yValues.put(parseYValue(p.getY(), description));
            }
            columns.put(xValues);
            columns.put(yValues);

            String name = s.getConfiguration("name", "");
            if (name != null) {
                names.put(yID, name);
            }

            axes.put(yID, Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? "y2" : "y");

            seriesIndex++;
        }

        JSONObject config = new JSONObject();
        config.put("xs", xs);
        config.put("axes", axes);
        config.put("columns", columns);
        config.put("names", names);
        return config;
    }

    private JSONObject getC3GridConfig() throws JSONException {
        JSONObject config = new JSONObject();
        if (Boolean.valueOf(mData.getConfiguration("show-grid", "true")).equals(Boolean.TRUE)) {
            JSONObject show = new JSONObject("{ show: true }");
            config.put("x", show);
            config.put("y", show);
        }
        return config;
    }

    private JSONObject getC3LegendConfig() throws JSONException {
        JSONObject config = new JSONObject();
        if (Boolean.valueOf(mData.getConfiguration("show-legend", "false")).equals(Boolean.FALSE)) {
            config.put("show", false);
        }
        return config;
    }

    private JSONObject getC3Config() throws InvalidStateException {
        JSONObject config = new JSONObject();
        try {
            config.put("axis", getC3AxisConfig());
            config.put("data", getC3DataConfig());
            config.put("grid", getC3GridConfig());
            config.put("legend", getC3LegendConfig());
        } catch (JSONException e) {
            throw new RuntimeException("something broke");  // TODO: fix
        }
        return config;
    }

    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public View getView(GraphData data) throws InvalidStateException {
        mData = data;
        
        WebView.setWebContentsDebuggingEnabled(true);   // TODO: only if in dev
        WebView webView = new WebView(mContext);
        configureSettings(webView);

        String html =
                "<html>" +
                    "<head>" +
                        "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/c3.min.css'></link>" +
                        "<link rel='stylesheet' type='text/css' href='file:///android_asset/graphing/graph.css'></link>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/d3.min.js'></script>" +
                        "<script type='text/javascript' src='file:///android_asset/graphing/c3.min.js' charset='utf-8'></script>" +
                        "<script type='text/javascript'>var config = " + getC3Config().toString() + ";</script>" +
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
        return mData.getConfiguration("x-labels-time-format", "yyyy-MM-dd");
    }

    /*
     * Allow or disallow clicks on this graph - really, on the view generated by getView.
     */
    public void setClickable(boolean enabled) {
        mRenderer.setClickEnabled(enabled);
    }

    /*
     * Set up a single series.
     */
    private void renderSeries(SeriesData s) throws InvalidStateException {
        XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
        mRenderer.addSeriesRenderer(currentRenderer);
        configureSeries(s, currentRenderer);

        XYSeries series = createSeries(Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? 1 : 0);
        if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
            if (s.getConfiguration("radius-max") != null) {
                ((RangeXYValueSeries)series).setMaxValue(parseYValue(s.getConfiguration("radius-max"), "radius-max"));
            }
        }
        mDataset.addSeries(series);

        // Bubble charts will throw an index out of bounds exception if given points out of order
        Vector<XYPointData> sortedPoints = new Vector<XYPointData>(s.size());
        for (XYPointData d : s.getPoints()) {
            sortedPoints.add(d);
        }
        Comparator<XYPointData> comparator;
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            String barSort = s.getConfiguration("bar-sort", "");
            switch (barSort) {
                case "ascending":
                    comparator = new AscendingValuePointComparator();
                    break;
                case "descending":
                    comparator = new DescendingValuePointComparator();
                    break;
                default:
                    comparator = new StringPointComparator();
                    break;
            }
        } else {
            comparator = new NumericPointComparator();
        }
        Collections.sort(sortedPoints, comparator);

        int barIndex = 1;
        JSONObject barLabels = new JSONObject();
        for (XYPointData p : sortedPoints) {
            String description = "point (" + p.getX() + ", " + p.getY() + ")";
            if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
                BubblePointData b = (BubblePointData)p;
                description += " with radius " + b.getRadius();
                ((RangeXYValueSeries)series).add(parseXValue(b.getX(), description), parseYValue(b.getY(), description), parseRadiusValue(b.getRadius(), description));
            } else if (Graph.TYPE_TIME.equals(mData.getType())) {
                ((TimeSeries)series).add(parseXValue(p.getX(), description), parseYValue(p.getY(), description));
            } else if (Graph.TYPE_BAR.equals(mData.getType())) {
                // In CommCare, bar graphs are specified with x as a set of text labels
                // and y as a set of values. In AChartEngine, bar graphs are a subclass
                // of XY graphs, with numeric x and y values. Deal with this by 
                // assigning an arbitrary, evenly-spaced x value to each bar and then
                // populating x-labels with the user's x values. 
                series.add(barIndex, parseYValue(p.getY(), description));
                try {
                    // For horizontal graphs, force labels right so they appear on the graph itself
                    String padding = getOrientation().equals(XYMultipleSeriesRenderer.Orientation.VERTICAL) ? "      " : "";
                    barLabels.put(Double.toString(barIndex), padding + p.getX());
                } catch (JSONException e) {
                    throw new InvalidStateException("Could not handle bar label '" + p.getX() + "': " + e.getMessage());
                }
                barIndex++;
            } else {
                series.add(parseXValue(p.getX(), description), parseYValue(p.getY(), description));
            }
        }
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            mData.setConfiguration("x-min", Double.toString(0.5));
            mData.setConfiguration("x-max", Double.toString(sortedPoints.size() + 0.5));
            mData.setConfiguration("x-labels", barLabels.toString());
        }
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
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            return 1;
        }
        return 2;
    }

    private XYMultipleSeriesRenderer.Orientation getOrientation() {
        // AChartEngine's horizontal/vertical definitions are counter-intuitive
        String orientation = mData.getConfiguration("bar-orientation", "");
        if (orientation.equalsIgnoreCase("vertical")) {
            return XYMultipleSeriesRenderer.Orientation.HORIZONTAL;
        } else {
            return XYMultipleSeriesRenderer.Orientation.VERTICAL;
        }
    }

    /**
     * Create series appropriate to the current graph type.
     *
     * @return An XYSeries-derived object.
     */
    private XYSeries createSeries() {
        return createSeries(0);
    }

    /**
     * Create series appropriate to the current graph type.
     *
     * @return An XYSeries-derived object.
     */
    private XYSeries createSeries(int scaleIndex) {
        if (Graph.TYPE_TIME.equals(mData.getType())) {
            return new TimeSeries("");
        }
        if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
            return new RangeXYValueSeries("");
        }
        return new XYSeries("", scaleIndex);
    }

    /*
     * Set up any annotations.
     */
    private void renderAnnotations() throws InvalidStateException {
        Vector<AnnotationData> annotations = mData.getAnnotations();
        if (!annotations.isEmpty()) {
            // Create a fake series for the annotations
            XYSeries series = createSeries();
            for (AnnotationData a : annotations) {
                String text = a.getAnnotation();
                String description = "annotation '" + text + "' at (" + a.getX() + ", " + a.getY() + ")";
                series.addAnnotation(text, parseXValue(a.getX(), description), parseYValue(a.getY(), description));
            }

            // Annotations won't display unless the series has some data in it
            series.add(0.0, 0.0);

            mDataset.addSeries(series);
            XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
            currentRenderer.setAnnotationsTextSize(mTextSize);
            currentRenderer.setAnnotationsColor(mContext.getResources().getColor(R.color.black));
            mRenderer.addSeriesRenderer(currentRenderer);
        }
    }

    /*
     * Apply any user-requested look and feel changes to graph.
     */
    private void configureSeries(SeriesData s, XYSeriesRenderer currentRenderer) {
        // Default to circular points, but allow other shapes or no points at all
        String pointStyle = s.getConfiguration("point-style", "circle").toLowerCase();
        if (!pointStyle.equals("none")) {
            PointStyle style = null;
            switch (pointStyle) {
                case "circle":
                    style = PointStyle.CIRCLE;
                    break;
                case "x":
                    style = PointStyle.X;
                    break;
                case "square":
                    style = PointStyle.SQUARE;
                    break;
                case "triangle":
                    style = PointStyle.TRIANGLE;
                    break;
                case "diamond":
                    style = PointStyle.DIAMOND;
                    break;
            }
            currentRenderer.setPointStyle(style);
            currentRenderer.setFillPoints(true);
            currentRenderer.setPointStrokeWidth(2);
            mRenderer.setPointSize(6);
        }

        String lineColor = s.getConfiguration("line-color");
        if (lineColor == null) {
            currentRenderer.setColor(Color.BLACK);
        } else {
            currentRenderer.setColor(Color.parseColor(lineColor));
        }
        currentRenderer.setLineWidth(2);

        fillOutsideLine(s, currentRenderer, "fill-above", XYSeriesRenderer.FillOutsideLine.Type.ABOVE);
        fillOutsideLine(s, currentRenderer, "fill-below", XYSeriesRenderer.FillOutsideLine.Type.BELOW);
    }

    /*
     * Helper function for setting up color fills above or below a series.
     */
    private void fillOutsideLine(SeriesData s, XYSeriesRenderer currentRenderer, String property, XYSeriesRenderer.FillOutsideLine.Type type) {
        property = s.getConfiguration(property);
        if (property != null) {
            XYSeriesRenderer.FillOutsideLine fill = new XYSeriesRenderer.FillOutsideLine(type);
            fill.setColor(Color.parseColor(property));
            currentRenderer.addFillOutsideLine(fill);
        }
    }

    /*
     * Configure graph's look and feel based on default assumptions and user-requested configuration.
     */
    private void configure() throws InvalidStateException {
        // User-configurable options
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            XYMultipleSeriesRenderer.Orientation orientation = getOrientation();
            mRenderer.setOrientation(orientation);
        }
    }

    /**
     * Parse given string into Double for AChartEngine.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private double parseXValue(String value, String description) throws InvalidStateException {
        if (Graph.TYPE_TIME.equals(mData.getType())) {
            Date parsed = DateUtils.parseDateTime(value);
            if (parsed == null) {
                throw new InvalidStateException("Could not parse date '" + value + "' in " + description);
            }
            return parseDouble(String.valueOf(parsed.getTime()), description);
        }

        return parseDouble(value, description);
    }

    /**
     * Parse given string into Double for AChartEngine.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private double parseYValue(String value, String description) throws InvalidStateException {
        return parseDouble(value, description);
    }

    /**
     * Parse given string into Double for AChartEngine.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private Double parseRadiusValue(String value, String description) throws InvalidStateException {
        return parseDouble(value, description);
    }

    /**
     * Attempt to parse a double, but fail on NumberFormatException.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private double parseDouble(String value, String description) throws InvalidStateException {
        try {
            Double numeric = Double.valueOf(value);
            if (numeric.isNaN()) {
                throw new InvalidStateException("Could not understand '" + value + "' in " + description);
            }
            return numeric.doubleValue();
        } catch (NumberFormatException nfe) {
            throw new InvalidStateException("Could not understand '" + value + "' in " + description);
        }
    }

    /**
     * Comparator to sort XYPointData-derived objects by x value.
     *
     * @author jschweers
     */
    private class NumericPointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return Double.valueOf(parseXValue(lhs.getX(), "")).compareTo(Double.valueOf(parseXValue(rhs.getX(), "")));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }

    /**
     * Comparator to sort XYPointData-derived objects by x value without parsing them.
     * Useful for bar graphs, where x values are text.
     *
     * @author jschweers
     */
    private class StringPointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            return lhs.getX().compareTo(rhs.getX());
        }
    }

    /**
     * Comparator to sort XYPoint-derived data by y value, in ascending order.
     * Useful for bar graphs, nonsensical for other graphs.
     *
     * @author jschweers
     */
    private class AscendingValuePointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return Double.valueOf(parseXValue(lhs.getY(), "")).compareTo(Double.valueOf(parseXValue(rhs.getY(), "")));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }

    /**
     * Comparator to sort XYPoint-derived data by y value, in descending order.
     * Useful for bar graphs, nonsensical for other graphs.
     *
     * @author jschweers
     */
    private class DescendingValuePointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return Double.valueOf(parseXValue(rhs.getY(), "")).compareTo(Double.valueOf(parseXValue(lhs.getY(), "")));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }
}
