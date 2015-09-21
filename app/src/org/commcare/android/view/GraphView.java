package org.commcare.android.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.view.View;
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
        mTextSize = (int) context.getResources().getDimension(R.dimen.text_large);
        mDataset = new XYMultipleSeriesDataset();
        mRenderer = new XYMultipleSeriesRenderer(2);    // initialize with two scales, to support a secondary y axis

        mRenderer.setChartTitle(title);
        mRenderer.setChartTitleTextSize(mTextSize);
    }
    
    /*
     * Set margins.
     */
    private void setMargins() {
        int textAllowance = (int) mContext.getResources().getDimension(R.dimen.graph_text_margin);
        int topMargin = (int) mContext.getResources().getDimension(R.dimen.graph_y_margin);
        if (!mRenderer.getChartTitle().equals("")) {
            topMargin += textAllowance;
        }
        int rightMargin = (int) mContext.getResources().getDimension(R.dimen.graph_x_margin);
        if (!mRenderer.getYTitle(1).equals("")) {
            rightMargin += textAllowance;
        }
        int leftMargin = (int) mContext.getResources().getDimension(R.dimen.graph_x_margin);
        if (!mRenderer.getYTitle().equals("")) {
            leftMargin += textAllowance;
        }
        int bottomMargin = (int) mContext.getResources().getDimension(R.dimen.graph_y_margin);
        if (!mRenderer.getXTitle().equals("")) {
            bottomMargin += textAllowance;
        }
        
        // Bar charts have text labels that are likely to be long (names, etc.).
        // This is a terrible, temporary way to give them extra space. At some point
        // there'll need to be a more robust solution for setting margins that
        // respond to data and screen size.
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            bottomMargin += 100;
        }
        mRenderer.setMargins(new int[]{topMargin, leftMargin, bottomMargin, rightMargin});
    }
    
    private void render(GraphData data) throws InvalidStateException {
        mData = data;
        mRenderer.setInScroll(true);
        for (SeriesData s : data.getSeries()) {
            renderSeries(s);
        }
        
        renderAnnotations();

        configure();
        setMargins();        
    }
        
    public Intent getIntent(GraphData data) throws InvalidStateException {
        render(data);

        setPanAndZoom(Boolean.valueOf(mData.getConfiguration("zoom", "false")));
        String title = mRenderer.getChartTitle();
        if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
            return ChartFactory.getBubbleChartIntent(mContext, mDataset, mRenderer, title);
        }
        if (Graph.TYPE_TIME.equals(mData.getType())) {
            return ChartFactory.getTimeChartIntent(mContext, mDataset, mRenderer, title, getTimeFormat());
        }
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            return ChartFactory.getBarChartIntent(mContext, mDataset, mRenderer, BarChart.Type.DEFAULT, title);
        }
        return ChartFactory.getLineChartIntent(mContext, mDataset, mRenderer, title);
    }

    /**
     * Enable or disable pan and zoom settings for this view.
     * @param allow Whether or not to enabled pan and zoom.
     */
    private void setPanAndZoom(boolean allow) {
        mRenderer.setPanEnabled(allow, allow);
        mRenderer.setZoomEnabled(allow, allow);
        mRenderer.setZoomButtonsVisible(allow);
    }
    
    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    public View getView(GraphData data) throws InvalidStateException {
        render(data);

        // Panning and zooming are allowed on in full-screen graphs (created by getIntent)
        setPanAndZoom(false);

        // Graph will not render correctly unless it has data, so
        // add a dummy series if needed.
        boolean hasPoints = false;
        Vector<SeriesData> allSeries = data.getSeries();
        for (int i = 0; i < allSeries.size() && !hasPoints; i++) {
            hasPoints = hasPoints || allSeries.get(i).getPoints().size() > 0;
        }
        if (!hasPoints) {
            SeriesData s = new SeriesData();
            if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
                s.addPoint(new BubblePointData("0", "0", "0"));
            } else if (Graph.TYPE_TIME.equals(mData.getType())) {
                s.addPoint(new XYPointData(DateUtils.formatDate(new Date(), DateUtils.FORMAT_ISO8601), "0"));
            } else {
                s.addPoint(new XYPointData("0", "0"));
            }
            s.setConfiguration("line-color", "#00000000");
            s.setConfiguration("point-style", "none");
            renderSeries(s);
        }
        
        if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
            return ChartFactory.getBubbleChartView(mContext, mDataset, mRenderer);
        }
        if (Graph.TYPE_TIME.equals(mData.getType())) {
            return ChartFactory.getTimeChartView(mContext, mDataset, mRenderer, getTimeFormat());
        }
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            return ChartFactory.getBarChartView(mContext, mDataset, mRenderer, BarChart.Type.DEFAULT);
        }
        return ChartFactory.getLineChartView(mContext, mDataset, mRenderer);
    }
    
    /**
     * Fetch date format for displaying time-based x labels.
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
        series.setTitle(s.getConfiguration("name", ""));
        if (Graph.TYPE_BUBBLE.equals(mData.getType())) {
            if (s.getConfiguration("radius-max") != null) {
                ((RangeXYValueSeries) series).setMaxValue(parseYValue(s.getConfiguration("radius-max"), "radius-max"));
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
                BubblePointData b = (BubblePointData) p;
                description += " with radius " + b.getRadius();
                ((RangeXYValueSeries) series).add(parseXValue(b.getX(), description), parseYValue(b.getY(), description), parseRadiusValue(b.getRadius(), description));
            } else if (Graph.TYPE_TIME.equals(mData.getType())) {
                ((TimeSeries) series).add(parseXValue(p.getX(), description), parseYValue(p.getY(), description));
            } else if (Graph.TYPE_BAR.equals(mData.getType())) {
                // In CommCare, bar graphs are specified with x as a set of text labels
                // and y as a set of values. In AChartEngine, bar graphs are a subclass
                // of XY graphs, with numeric x and y values. Deal with this by 
                // assigning an arbitrary, evenly-spaced x value to each bar and then
                // populating x-labels with the user's x values. 
                series.add(barIndex, parseYValue(p.getY(), description));
                try {
                    barLabels.put(Double.toString(barIndex), p.getX());
                }
                catch (JSONException e) {
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
    
    /**
     * Create series appropriate to the current graph type.
     * @return An XYSeries-derived object.
     */
    private XYSeries createSeries() {
        return createSeries(0);
    }
    
    /**
     * Create series appropriate to the current graph type.
     * @return An XYSeries-derived object.
     */
    private XYSeries createSeries(int scaleIndex) {
        // TODO: Bubble and time graphs ought to respect scaleIndex, but XYValueSeries
        // and TimeSeries don't expose the (String title, int scaleNumber) constructor.
        if (scaleIndex > 0 && !Graph.TYPE_XY.equals(mData.getType())) {
            throw new IllegalArgumentException("This series does not support a secondary y axis");
        }

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
        }
        
        String lineColor = s.getConfiguration("line-color");
        if (lineColor == null) {
            currentRenderer.setColor(Color.BLACK);
        } else {
            currentRenderer.setColor(Color.parseColor(lineColor));
        }
        
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
    private void configure() throws InvalidStateException{
        // Default options
        mRenderer.setBackgroundColor(mContext.getResources().getColor(R.color.white));
        mRenderer.setMarginsColor(mContext.getResources().getColor(R.color.white));
        mRenderer.setLabelsColor(mContext.getResources().getColor(R.color.grey_darker));
        mRenderer.setXLabelsColor(mContext.getResources().getColor(R.color.grey_darker));
        mRenderer.setYLabelsColor(0, mContext.getResources().getColor(R.color.grey_darker));
        mRenderer.setYLabelsColor(1, mContext.getResources().getColor(R.color.grey_darker));
        mRenderer.setXLabelsAlign(Align.CENTER);
        mRenderer.setYLabelsAlign(Align.RIGHT);
        mRenderer.setYLabelsAlign(Align.LEFT, 1);
        mRenderer.setYAxisAlign(Align.RIGHT, 1);
        mRenderer.setAxesColor(mContext.getResources().getColor(R.color.grey_lighter));
        mRenderer.setLabelsTextSize(mTextSize);
        mRenderer.setAxisTitleTextSize(mTextSize);
        mRenderer.setApplyBackgroundColor(true);
        mRenderer.setShowGrid(true);
        
        int padding = 10;
        mRenderer.setXLabelsPadding(padding);
        mRenderer.setYLabelsPadding(padding);
        mRenderer.setYLabelsVerticalPadding(padding);
        
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            mRenderer.setBarSpacing(0.5);
        }
        
        // User-configurable options
        mRenderer.setXTitle(mData.getConfiguration("x-title", ""));
        mRenderer.setYTitle(mData.getConfiguration("y-title", ""));
        mRenderer.setYTitle(mData.getConfiguration("secondary-y-title", ""), 1);

        if (Graph.TYPE_BAR.equals(mData.getType())) {
            String orientation = mData.getConfiguration("bar-orientation", "");
            if (orientation.equalsIgnoreCase("vertical")) {
                mRenderer.setOrientation(XYMultipleSeriesRenderer.Orientation.VERTICAL);
            }
            else {
                mRenderer.setOrientation(XYMultipleSeriesRenderer.Orientation.HORIZONTAL);
            }
        }

        if (mData.getConfiguration("x-min") != null) {
            mRenderer.setXAxisMin(parseXValue(mData.getConfiguration("x-min"), "x-min"));
        }
        if (mData.getConfiguration("y-min") != null) {
            mRenderer.setYAxisMin(parseYValue(mData.getConfiguration("y-min"), "y-min"));
        }
        if (mData.getConfiguration("secondary-y-min") != null) {
            mRenderer.setYAxisMin(parseYValue(mData.getConfiguration("secondary-y-min"), "secondary-y-min"), 1);
        }
        
        if (mData.getConfiguration("x-max") != null) {
            mRenderer.setXAxisMax(parseXValue(mData.getConfiguration("x-max"), "x-max"));
        }
        if (mData.getConfiguration("y-max") != null) {
            mRenderer.setYAxisMax(parseYValue(mData.getConfiguration("y-max"), "y-max"));
        }
        if (mData.getConfiguration("secondary-y-max") != null) {
            mRenderer.setYAxisMax(parseYValue(mData.getConfiguration("secondary-y-max"), "secondary-y-max"), 1);
        }
        
        boolean showGrid = Boolean.valueOf(mData.getConfiguration("show-grid", "true")).equals(Boolean.TRUE);
        mRenderer.setShowGridX(showGrid);
        mRenderer.setShowGridY(showGrid);
        mRenderer.setShowCustomTextGridX(showGrid);
        mRenderer.setShowCustomTextGridY(showGrid);

        String showAxes = mData.getConfiguration("show-axes", "true");
        if (Boolean.valueOf(showAxes).equals(Boolean.FALSE)) {
            mRenderer.setShowAxes(false);
        }
        
        // Legend
        boolean showLegend = Boolean.valueOf(mData.getConfiguration("show-legend", "false"));
        mRenderer.setShowLegend(showLegend);
        mRenderer.setLegendTextSize(mTextSize);

        // Labels
        boolean hasX = configureLabels("x-labels");
        boolean hasY = configureLabels("y-labels");
        configureLabels("secondary-y-labels");
        boolean showLabels = hasX || hasY;
        mRenderer.setShowLabels(showLabels);
        mRenderer.setShowTickMarks(showLabels);
    }
    
    /**
     * Parse given string into Double for AChartEngine.
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private Double parseXValue(String value, String description) throws InvalidStateException {
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
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private Double parseYValue(String value, String description) throws InvalidStateException {
        return parseDouble(value, description);
    }
    
    /**
     * Parse given string into Double for AChartEngine.
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private Double parseRadiusValue(String value, String description) throws InvalidStateException {
        return parseDouble(value, description);
    }
    
    /**
     * Attempt to parse a double, but fail on NumberFormatException.
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    private Double parseDouble(String value, String description) throws InvalidStateException {
        try {
            Double numeric = Double.valueOf(value);
            if (numeric.isNaN()) {
                throw new InvalidStateException("Could not understand '" + value + "' in " + description);
            }
            return numeric;
        }
        catch (NumberFormatException nfe) {
            throw new InvalidStateException("Could not understand '" + value + "' in " + description);
        }
    }

    /**
     * Customize labels.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     * @return True iff axis has any labels at all
     */
    private boolean configureLabels(String key) throws InvalidStateException {
        boolean hasLabels = true;
        
        // The labels setting might be a JSON array of numbers, 
        // a JSON object of number => string, or a single number
        String labelString = mData.getConfiguration(key);
        if (labelString != null) {
            try {
                // Array: label each given value
                JSONArray labels = new JSONArray(labelString);
                setLabelCount(key, 0);
                for (int i = 0; i < labels.length(); i++) {
                    String value = labels.getString(i);
                    addTextLabel(key, parseXValue(value, "x label '" + key + "'"), value);
                }
                hasLabels = labels.length() > 0;
            }
            catch (JSONException je) {
                // Assume try block failed because labelString isn't an array.
                // Try parsing it as an object.
                try {
                    // Object: each keys is a location on the axis, 
                    // and the value is the text with which to label it
                    JSONObject labels = new JSONObject(labelString);
                    setLabelCount(key, 0);
                    Iterator i = labels.keys();
                    hasLabels = false;
                    while (i.hasNext()) {
                       String location = (String) i.next();
                       addTextLabel(key, parseXValue(location, "x label at " + location), labels.getString(location));
                       hasLabels = true;
                    }
                }
                catch (JSONException e) {
                    // Assume labelString is just a scalar, which
                    // represents the number of labels the user wants.
                    Integer count = Integer.valueOf(labelString);
                    setLabelCount(key, count);
                    hasLabels = count != 0;
                }
            }
        }
        
        return hasLabels;
    }
    
    /**
     * Helper for configureLabels. Adds a label to the appropriate axis.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     * @param location Point on axis to add label
     * @param text String for label
     */
    private void addTextLabel(String key, Double location, String text) {
        if (isXKey(key)) {
            mRenderer.addXTextLabel(location, text);
        } else {
            int scaleIndex = getScaleIndex(key);
            if (mRenderer.getYAxisAlign(scaleIndex) == Align.RIGHT) {
                text = "   " + text;
            }
            mRenderer.addYTextLabel(location, text, scaleIndex);
        }
    }
    
    /**
     * Helper for configureLabels. Sets desired number of labels for the appropriate axis.
     * AChartEngine will then determine how to space the labels.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     * @param value Number of labels
     */
    private void setLabelCount(String key, int value) {
        if (isXKey(key)) {
            mRenderer.setXLabels(value);
        } else {
            mRenderer.setYLabels(value);
        }
    }
    
    /**
     * Helper for turning key into scale.
     * @param key Something like "x-labels" or "y-secondary-labels"
     * @return Index for passing to AChartEngine functions that accept a scale
     */
    private int getScaleIndex(String key) {
        return key.contains("secondary") ? 1 : 0;
    }
    
    /**
     * Helper for parsing axis from configuration key.
     * @param key Something like "x-min" or "y-labels"
     * @return True iff key is relevant to x axis
     */
    private boolean isXKey(String key) {
        return key.startsWith("x-");
    }
    
    /**
     * Comparator to sort XYPointData-derived objects by x value.
     * @author jschweers
     */
    private class NumericPointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return parseXValue(lhs.getX(), "").compareTo(parseXValue(rhs.getX(), ""));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }

    /**
     * Comparator to sort XYPointData-derived objects by x value without parsing them.
     * Useful for bar graphs, where x values are text.
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
     * @author jschweers
     */
    private class AscendingValuePointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return parseXValue(lhs.getY(), "").compareTo(parseXValue(rhs.getY(), ""));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }

    /**
     * Comparator to sort XYPoint-derived data by y value, in descending order.
     * Useful for bar graphs, nonsensical for other graphs.
     * @author jschweers
     */
    private class DescendingValuePointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return parseXValue(rhs.getY(), "").compareTo(parseXValue(lhs.getY(), ""));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }
}
