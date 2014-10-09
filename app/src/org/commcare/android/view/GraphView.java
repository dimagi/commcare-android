package org.commcare.android.view;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

import org.achartengine.ChartFactory;
import org.achartengine.chart.PointStyle;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
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
        mRenderer = new XYMultipleSeriesRenderer(2);

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
        return ChartFactory.getLineChartIntent(mContext, mDataset, mRenderer, title);
    }
    
    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    public View getView(GraphData data) {
        render(data);
        
        // Graph will not render correctly unless it has data. 
        // Add a dummy series to guarantee this.
        // Do this after adding any real data and after configuring
        // so that get_AxisMin functions return correct values.
        SeriesData s = new SeriesData();
        double minX = mRenderer.getXAxisMin();
        double minY = mRenderer.getYAxisMin();
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            s.addPoint(new BubblePointData(minX, minY, 0.0));
        }
        else {
            s.addPoint(new XYPointData(minX, minY));
        }
        s.setConfiguration("line-color", "#00000000");
        s.setConfiguration("point-style", "none");
        renderSeries(s);
        
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            return ChartFactory.getBubbleChartView(mContext, mDataset, mRenderer);
        }
        return ChartFactory.getLineChartView(mContext, mDataset, mRenderer);
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

        XYSeries series;
        int seriesIndex = Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? 1 : 0;
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            // TODO: This also ought to respect seriesIndex. However, XYValueSeries doesn't expose the
            // (String title, int scaleNumber) constructor, so RangeXYValueSeries doesn't have access to it.
            if (seriesIndex > 0) {
                throw new IllegalArgumentException("Bubbles series do not support a secondary y axis");
            }
            series = new RangeXYValueSeries("");
            if (s.getConfiguration("radius-max") != null) {
                ((RangeXYValueSeries) series).setMaxValue(Double.valueOf(s.getConfiguration("radius-max")));
            }
        }
        else {
            series = new XYSeries("", seriesIndex);
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
                ((RangeXYValueSeries) series).add(b.getX(), b.getY(), b.getRadius());
            }
            else {
                series.add(p.getX(), p.getY());
            }
        }
    }
    
    /*
     * Get layout params for this graph, which assume that graph will fill parent
     * unless dimensions have been provided via setWidth and/or setHeight.
     */
    public static LinearLayout.LayoutParams getLayoutParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);    
    }
    
    /*
     * Set up any annotations.
     */
    private void renderAnnotations() {
        Vector<AnnotationData> annotations = mData.getAnnotations();
        if (!annotations.isEmpty()) {
            // Create a fake series for the annotations
            XYSeries series = new XYSeries("");
            for (AnnotationData a : annotations) {
                series.addAnnotation(a.getAnnotation(), a.getX(), a.getY());
            }
            
            // Annotations won't display unless the series has some data in it
            series.add(0.0, 0.0);

            mDataset.addSeries(series);
            XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
            currentRenderer.setAnnotationsTextSize(mTextSize);
            currentRenderer.setAnnotationsColor(mContext.getResources().getColor(R.drawable.black));
            mRenderer.addSeriesRenderer(currentRenderer);
        }
    }

    /*
     * Apply any user-requested look and feel changes to graph.
     */
    private void configureSeries(SeriesData s, XYSeriesRenderer currentRenderer) {
        // Default to circular points, but allow Xs or no points at all
        String pointStyle = s.getConfiguration("point-style", "circle").toLowerCase();
        if (!pointStyle.equals("none")) {
            PointStyle style = null;
            if (pointStyle.equals("circle")) {
                style = PointStyle.CIRCLE;
            }
            else if (pointStyle.equals("x")) {
                style = PointStyle.X;
            }
            currentRenderer.setPointStyle(style);
            currentRenderer.setFillPoints(true);
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
        mRenderer.setBackgroundColor(mContext.getResources().getColor(R.drawable.white));
        mRenderer.setMarginsColor(mContext.getResources().getColor(R.drawable.white));
        mRenderer.setLabelsColor(mContext.getResources().getColor(R.drawable.black));
        mRenderer.setXLabelsColor(mContext.getResources().getColor(R.drawable.black));
        mRenderer.setYLabelsColor(0, mContext.getResources().getColor(R.drawable.black));
        mRenderer.setYLabelsColor(1, mContext.getResources().getColor(R.drawable.black));
        mRenderer.setXLabelsAlign(Align.CENTER);
        mRenderer.setYLabelsAlign(Align.RIGHT);
        mRenderer.setYLabelsAlign(Align.LEFT, 1);
        mRenderer.setYLabelsPadding(10);
        mRenderer.setYAxisAlign(Align.RIGHT, 1);
        mRenderer.setAxesColor(mContext.getResources().getColor(R.drawable.black));
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
            mRenderer.setXAxisMin(Double.valueOf(mData.getConfiguration("x-min")));
        }
        if (mData.getConfiguration("y-min") != null) {
            mRenderer.setYAxisMin(Double.valueOf(mData.getConfiguration("y-min")));
        }
        if (mData.getConfiguration("secondary-y-min") != null) {
            mRenderer.setYAxisMin(Double.valueOf(mData.getConfiguration("secondary-y-min")), 1);
        }
        
        if (mData.getConfiguration("x-max") != null) {
            mRenderer.setXAxisMax(Double.valueOf(mData.getConfiguration("x-max")));
        }
        if (mData.getConfiguration("y-max") != null) {
            mRenderer.setYAxisMax(Double.valueOf(mData.getConfiguration("y-max")));
        }
        if (mData.getConfiguration("secondary-y-max") != null) {
            mRenderer.setYAxisMax(Double.valueOf(mData.getConfiguration("secondary-y-max")), 1);
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
        boolean hasXLabels = configureLabels("x-labels");
        boolean hasYLabels = configureLabels("y-labels");
        boolean hasSecondaryYLabels = configureLabels("secondary-y-labels");
        boolean showLabels = hasXLabels || hasYLabels || hasSecondaryYLabels;
        mRenderer.setShowLabels(showLabels);
        mRenderer.setShowTickMarks(showLabels);

        boolean panAndZoom = Boolean.valueOf(mData.getConfiguration("zoom", "false")).equals(Boolean.TRUE);
        mRenderer.setPanEnabled(panAndZoom, panAndZoom);
        mRenderer.setZoomEnabled(panAndZoom, panAndZoom);
        mRenderer.setZoomButtonsVisible(panAndZoom);
    }
    
    /**
     * Customize labels.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     * @return True if any labels at all will be displayed.
     */
    private boolean configureLabels(String key) {
        boolean hasLabels = false;
        
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
                    addTextLabel(key, Double.valueOf(value), value);
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
                    while (i.hasNext()) {
                       String location = (String) i.next();
                       addTextLabel(key, Double.valueOf(location), labels.getString(location));
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
        }
        else {
            int seriesIndex = getSeriesIndex(key);
            if (mRenderer.getYAxisAlign(seriesIndex) == Align.RIGHT) {
                text = "   " + text;
            }
            mRenderer.addYTextLabel(location, text, seriesIndex);
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
    private int getSeriesIndex(String key) {
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
     * Comparator to sort PointData objects by x value.
     * @author jschweers
     */
    private class PointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            if (lhs.getX() > rhs.getX()) {
                return 1;
            }
            if (lhs.getX() < rhs.getX()) {
                return -1;
            }
            return 0;
        }
    }
}
