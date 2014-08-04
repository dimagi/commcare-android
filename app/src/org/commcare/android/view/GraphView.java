/*
 * View containing a graph. Note that this does not derive from View; call getView to get a view for adding to other views, etc.
 * @author jschweers
 */
package org.commcare.android.view;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

import org.achartengine.ChartFactory;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.model.XYValueSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.commcare.dalvik.R;
import org.commcare.suite.model.graph.AnnotationData;
import org.commcare.suite.model.graph.BubblePointData;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.SeriesData;
import org.commcare.suite.model.graph.XYPointData;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

public class GraphView {
	private static final int TEXT_SIZE = 21;

	private Context mContext;
	private GraphData mData;
	private XYMultipleSeriesDataset mDataset;
	private XYMultipleSeriesRenderer mRenderer; 
	
	private int mHeight = 0;
	private int mWidth = 0;

	public GraphView(Context context, GraphData data) {
		mContext = context;
		mData = data;
		mDataset = new XYMultipleSeriesDataset();
		mRenderer = new XYMultipleSeriesRenderer();
		
		mRenderer.setInScroll(true);
		Iterator<SeriesData> seriesIterator = data.getSeriesIterator();
		while (seriesIterator.hasNext()) {
			SeriesData s = seriesIterator.next();
			XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
			mRenderer.addSeriesRenderer(currentRenderer);
			
			configureSeries(s, currentRenderer);
			renderSeries(s);
		}
		
		renderAnnotations();
		configure();
		setMargins();
	} 
	
	/*
	 * Set title of graph, and adjust spacing accordingly.
	 */
	public void setTitle(String title) {
		mRenderer.setChartTitle(title);
		mRenderer.setChartTitleTextSize(TEXT_SIZE);
		setMargins();
	}
	
	/*
	 * Set margins. Should also be called after altering chart title or axis titles.
	 */
	private void setMargins() {
		int topMargin = mRenderer.getChartTitle().equals("") ? 0 : 30;
		int rightMargin = 20;
		int leftMargin = mRenderer.getYTitle().equals("") ? 50 : 70;
		int bottomMargin = mRenderer.getXTitle().equals("") ? 0 : 50;
		mRenderer.setMargins(new int[]{topMargin, leftMargin, bottomMargin, rightMargin});
	}
		
	/*
	 * Get a View object that will display this graph.
	 */
	public View getView() {
		if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
        	return ChartFactory.getBubbleChartView(mContext, mDataset, mRenderer);
		}
        return ChartFactory.getLineChartView(mContext, mDataset, mRenderer);
	}
	
	/*
	 * Set up a single series.
	 */
	private void renderSeries(SeriesData s) {
		XYSeries series = mData.getType().equals(Graph.TYPE_BUBBLE) ? new XYValueSeries("") : new XYSeries("");
		mDataset.addSeries(series);

		// Bubble charts will throw an index out of bounds exception if given points out of order
		Vector<XYPointData> sortedPoints = new Vector<XYPointData>(s.size());
		Iterator<XYPointData> pointsIterator = s.getPointsIterator();
		while (pointsIterator.hasNext()) {
			sortedPoints.add(pointsIterator.next());
		}
		Collections.sort(sortedPoints, new PointComparator());
		
		for (XYPointData p : sortedPoints) {
			if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
				BubblePointData b = (BubblePointData) p;
				((XYValueSeries) series).add(b.getX(), b.getY(), b.getRadius());
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
	public LinearLayout.LayoutParams getLayoutParams() {
		int height = mHeight == 0 ? LinearLayout.LayoutParams.FILL_PARENT : mHeight;
		int width = mWidth == 0 ? LinearLayout.LayoutParams.FILL_PARENT : mWidth;
		return new LinearLayout.LayoutParams(width, height);	
	}
	
	public void setHeight(int height) {
		mHeight = height;
        reduceLabels(false);
	}
	
	public void setWidth(int width) {
		mWidth = width;
        reduceLabels(true);
	}
	
	/*
	 * Attempt to remove some axis labels if graph is too short or narrow for them all.
	 */
	private void reduceLabels(boolean isX) {
		// Get number of labels user originally asked for
		configureLabels(isX);

		int count = isX ? mRenderer.getXLabels() : mRenderer.getYLabels();
		int dimension = isX ? mWidth : mHeight;
		
		// Guess if labels will be too crowded
		while (count * TEXT_SIZE > dimension) {
			count = count % 2 != 0 && count % 3 == 0 ? count / 3 : count / 2;
			if (isX) {
				mRenderer.setXLabels(count);
			}
			else {
				mRenderer.setYLabels(count);
			}
		}
	}
	
	/*
	 * Set number of axis labels based on user configuration.
	 */
	private void configureLabels(boolean isX) {
		if (isX && mData.getConfiguration("x-label-count") != null) {
			mRenderer.setXLabels(Integer.valueOf(mData.getConfiguration("x-label-count")));
		}
		if (!isX && mData.getConfiguration("y-label-count") != null) {
			mRenderer.setYLabels(Integer.valueOf(mData.getConfiguration("y-label-count")));
		}
		
	}
	
	/*
	 * Set up any annotations.
	 */
	private void renderAnnotations() {
		Iterator<AnnotationData> i = mData.getAnnotationIterator();
		if (i.hasNext()) {
			// Create a fake series for the annotations
			XYSeries series = new XYSeries("");
			while (i.hasNext()) {
				AnnotationData a = i.next();
				series.addAnnotation(a.getAnnotation(), a.getX(), a.getY());
			}
			
			// Annotations won't display unless the series has some data in it
			series.add(0.0, 0.0);

			mDataset.addSeries(series);
			XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
			currentRenderer.setAnnotationsTextSize(TEXT_SIZE);
			currentRenderer.setAnnotationsColor(mContext.getResources().getColor(R.drawable.black));
			mRenderer.addSeriesRenderer(currentRenderer);
		}
	}

	/*
	 * Apply any user-requested look and feel changes to graph.
	 */
	private void configureSeries(SeriesData s, XYSeriesRenderer currentRenderer) {
		String showPoints = s.getConfiguration("show-points");
		if (showPoints == null || !Boolean.valueOf(showPoints).equals(Boolean.FALSE)) {
			currentRenderer.setPointStyle(PointStyle.CIRCLE);
			currentRenderer.setFillPoints(true);
		}
		
		String lineColor = s.getConfiguration("line-color");
		if (lineColor != null) {
			currentRenderer.setColor(Color.parseColor(lineColor));
		}
		else {
			currentRenderer.setColor(mContext.getResources().getColor(R.drawable.black));
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
		mRenderer.setXLabelsAlign(Paint.Align.CENTER);
		mRenderer.setYLabelsAlign(Paint.Align.RIGHT);
		mRenderer.setYLabelsPadding(10);
		mRenderer.setAxesColor(mContext.getResources().getColor(R.drawable.black));
		mRenderer.setLabelsTextSize(TEXT_SIZE);
		mRenderer.setAxisTitleTextSize(TEXT_SIZE);
		mRenderer.setShowLabels(true);
		mRenderer.setApplyBackgroundColor(true);
		mRenderer.setShowLegend(false);
		mRenderer.setShowGrid(true);
		mRenderer.setPanEnabled(false, false);

		// User-configurable options
		if (mData.getConfiguration("x-axis-title") != null) {
			mRenderer.setXTitle(mData.getConfiguration("x-axis-title"));
		}
		if (mData.getConfiguration("y-axis-title") != null) {
			mRenderer.setYTitle(mData.getConfiguration("y-axis-title"));
		}
		setMargins();

		if (mData.getConfiguration("x-axis-min") != null) {
			mRenderer.setXAxisMin(Double.valueOf(mData.getConfiguration("x-axis-min")));
		}
		if (mData.getConfiguration("y-axis-min") != null) {
			mRenderer.setYAxisMin(Double.valueOf(mData.getConfiguration("y-axis-min")));
		}
		
		if (mData.getConfiguration("x-axis-max") != null) {
			mRenderer.setXAxisMax(Double.valueOf(mData.getConfiguration("x-axis-max")));
		}
		if (mData.getConfiguration("y-axis-max") != null) {
			mRenderer.setYAxisMax(Double.valueOf(mData.getConfiguration("y-axis-max")));
		}
		
		configureLabels(true);
		configureLabels(false);
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
