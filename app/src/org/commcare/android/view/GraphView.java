package org.commcare.android.view;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

import org.achartengine.ChartFactory;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.commcare.android.models.RangeXYValueSeries;
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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.view.View;
import android.widget.LinearLayout;

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
        mRenderer.setMargins(new int[]{topMargin, leftMargin, bottomMargin, rightMargin});
    }
    
    private void render(GraphData data) {
        mData = data;
        mRenderer.setInScroll(true);
        for (SeriesData s : data.getSeries()) {
            renderSeries(s);
        }
        
        renderAnnotations();

        configure();
        setMargins();        
    }
        
    public Intent getIntent(GraphData data) {
        render(data);
        
        String title = mRenderer.getChartTitle();
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            return ChartFactory.getBubbleChartIntent(mContext, mDataset, mRenderer, title);
        }
        if (mData.getType().equals(Graph.TYPE_TIME)) {
            return ChartFactory.getTimeChartIntent(mContext, mDataset, mRenderer, title, getTimeFormat());
        }
        return ChartFactory.getLineChartIntent(mContext, mDataset, mRenderer, title);
    }
    
    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    public View getView(GraphData data) {
        render(data);
        
        // Graph will not render correctly unless it has data, so
        // add a dummy series if needed.
        boolean hasPoints = false;
        Vector<SeriesData> allSeries = data.getSeries();
        for (int i = 0; i < allSeries.size() && !hasPoints; i++) {
            hasPoints = hasPoints || allSeries.get(i).getPoints().size() > 0;
        }
        if (!hasPoints) {
            SeriesData s = new SeriesData();
            if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                s.addPoint(new BubblePointData("0", "0", "0"));
            }
            else if (mData.getType().equals(Graph.TYPE_TIME)) {
                s.addPoint(new XYPointData(DateUtils.formatDate(new Date(), DateUtils.FORMAT_ISO8601), "0"));
            }
            else {
                s.addPoint(new XYPointData("0", "0"));
            }
            s.setConfiguration("line-color", "#00000000");
            s.setConfiguration("point-style", "none");
            renderSeries(s);
        }
        
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            return ChartFactory.getBubbleChartView(mContext, mDataset, mRenderer);
        }
        if (mData.getType().equals(Graph.TYPE_TIME)) {
            return ChartFactory.getTimeChartView(mContext, mDataset, mRenderer, getTimeFormat());
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
    private void renderSeries(SeriesData s) {
        XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
        mRenderer.addSeriesRenderer(currentRenderer);
        configureSeries(s, currentRenderer);

        XYSeries series = createSeries(Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? 1 : 0);
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            if (s.getConfiguration("radius-max") != null) {
                ((RangeXYValueSeries) series).setMaxValue(parseYValue(s.getConfiguration("radius-max")));
            }
        }
        mDataset.addSeries(series);

        // Bubble charts will throw an index out of bounds exception if given points out of order
        Vector<XYPointData> sortedPoints = new Vector<XYPointData>(s.size());
        for (XYPointData d : s.getPoints()) {
            sortedPoints.add(d);
        }
        Collections.sort(sortedPoints, new PointComparator());
        
        for (XYPointData p : sortedPoints) {
            if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                BubblePointData b = (BubblePointData) p;
                ((RangeXYValueSeries) series).add(parseXValue(b.getX()), parseYValue(b.getY()), parseRadiusValue(b.getRadius()));
            }
            else if (mData.getType().equals(Graph.TYPE_TIME)) {
                ((TimeSeries) series).add(parseXValue(p.getX()), parseYValue(p.getY()));
            }
            else {
                series.add(parseXValue(p.getX()), parseYValue(p.getY()));
            }
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
     * Create series appropriate to the current graph type.
     * @return An XYSeries-derived object.
     */
    private XYSeries createSeries() {
        return createSeries(0);
    }
    
    /**
     * Create series appropriate to the current graph type.
     * @param scaleIndex
     * @return An XYSeries-derived object.
     */
    private XYSeries createSeries(int scaleIndex) {
        // TODO: Bubble and time graphs ought to respect scaleIndex, but XYValueSeries
        // and TimeSeries don't expose the (String title, int scaleNumber) constructor.
        if (scaleIndex > 0 && !mData.getType().equals(Graph.TYPE_XY)) {
            throw new IllegalArgumentException("This series does not support a secondary y axis");
        }

        if (mData.getType().equals(Graph.TYPE_TIME)) {
            return new TimeSeries("");
        }
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            return new RangeXYValueSeries("");
        }
        return new XYSeries("", scaleIndex);
    }
    
    /*
     * Set up any annotations.
     */
    private void renderAnnotations() {
        Vector<AnnotationData> annotations = mData.getAnnotations();
        if (!annotations.isEmpty()) {
            // Create a fake series for the annotations
            XYSeries series = createSeries();
            for (AnnotationData a : annotations) {
                series.addAnnotation(a.getAnnotation(), parseXValue(a.getX()), parseYValue(a.getY()));
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
            if (pointStyle.equals("circle")) {
                style = PointStyle.CIRCLE;
            }
            else if (pointStyle.equals("x")) {
                style = PointStyle.X;
            }
            else if (pointStyle.equals("square")) {
                style = PointStyle.SQUARE;
            }
            else if (pointStyle.equals("triangle")) {
                style = PointStyle.TRIANGLE;
            }
            else if (pointStyle.equals("diamond")) {
                style = PointStyle.DIAMOND;
            }
            currentRenderer.setPointStyle(style);
            currentRenderer.setFillPoints(true);
            currentRenderer.setPointStrokeWidth(2);
        }
        
        String lineColor = s.getConfiguration("line-color");
        if (lineColor == null) {
            currentRenderer.setColor(Color.BLACK);
        }
        else {
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
    private void configure() {
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
        mRenderer.setYLabelsPadding(10);
        mRenderer.setYAxisAlign(Align.RIGHT, 1);
        mRenderer.setAxesColor(mContext.getResources().getColor(R.color.grey_lighter));
        mRenderer.setLabelsTextSize(mTextSize);
        mRenderer.setAxisTitleTextSize(mTextSize);
        mRenderer.setApplyBackgroundColor(true);
        mRenderer.setShowLegend(false);
        mRenderer.setShowGrid(true);
        
        // User-configurable options
        mRenderer.setXTitle(mData.getConfiguration("x-title", ""));
        mRenderer.setYTitle(mData.getConfiguration("y-title", ""));
        mRenderer.setYTitle(mData.getConfiguration("secondary-y-title", ""), 1);

        if (mData.getConfiguration("x-min") != null) {
            mRenderer.setXAxisMin(parseXValue(mData.getConfiguration("x-min")));
        }
        if (mData.getConfiguration("y-min") != null) {
            mRenderer.setYAxisMin(parseYValue(mData.getConfiguration("y-min")));
        }
        if (mData.getConfiguration("secondary-y-min") != null) {
            mRenderer.setYAxisMin(parseYValue(mData.getConfiguration("secondary-y-min")), 1);
        }
        
        if (mData.getConfiguration("x-max") != null) {
            mRenderer.setXAxisMax(parseXValue(mData.getConfiguration("x-max")));
        }
        if (mData.getConfiguration("y-max") != null) {
            mRenderer.setYAxisMax(parseYValue(mData.getConfiguration("y-max")));
        }
        if (mData.getConfiguration("secondary-y-max") != null) {
            mRenderer.setYAxisMax(parseYValue(mData.getConfiguration("secondary-y-max")), 1);
        }
        
        String showGrid = mData.getConfiguration("show-grid", "true");
        if (Boolean.valueOf(showGrid).equals(Boolean.FALSE)) {
            mRenderer.setShowGridX(false);
            mRenderer.setShowGridY(false);
        }

        String showAxes = mData.getConfiguration("show-axes", "true");
        if (Boolean.valueOf(showAxes).equals(Boolean.FALSE)) {
            mRenderer.setShowAxes(false);
        }
        
        // Labels
        configureLabels("x-labels");
        configureLabels("y-labels");
        configureLabels("secondary-y-labels");

        boolean panAndZoom = Boolean.valueOf(mData.getConfiguration("zoom", "false")).equals(Boolean.TRUE);
        mRenderer.setPanEnabled(panAndZoom, panAndZoom);
        mRenderer.setZoomEnabled(panAndZoom, panAndZoom);
        mRenderer.setZoomButtonsVisible(panAndZoom);
    }
    
    /**
     * Parse given string into Double for AChartEngine.
     * @param value
     * @return
     */
    private Double parseXValue(String value) {
        if (mData.getType().equals(Graph.TYPE_TIME)) {
            return Double.valueOf(DateUtils.parseDateTime(value).getTime());
        }

        return Double.valueOf(value);
    }
    
    /**
     * Parse given string into Double for AChartEngine.
     * @param value
     * @return
     */
    private Double parseYValue(String value) {
        return Double.valueOf(value);
    }
    
    /**
     * Parse given string into Double for AChartEngine.
     * @param value
     * @return
     */
    private Double parseRadiusValue(String value) {
        return Double.valueOf(value);
    }

    /**
     * Customize labels.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     */
    private void configureLabels(String key) {
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
                    addTextLabel(key, parseXValue(value), value);
                }
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
                    while (i.hasNext()) {
                       String location = (String) i.next();
                       addTextLabel(key, parseXValue(location), labels.getString(location));
                    }
                }
                catch (JSONException e) {
                    // Assume labelString is just a scalar, which
                    // represents the number of labels the user wants.
                    Integer count = Integer.valueOf(labelString);
                    setLabelCount(key, count);
                }
            }
        }
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
        }
        else {
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
        }
        else {
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
    private class PointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            return parseXValue(lhs.getX()).compareTo(parseXValue(rhs.getX()));
        }
    }

}
