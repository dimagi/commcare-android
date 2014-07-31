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

	private Context context;
	private GraphData data;
	private XYMultipleSeriesDataset dataset;
	private XYMultipleSeriesRenderer renderer; 
	
	private int height;
	private int width;

	public GraphView(Context context, GraphData data) {
		this.context = context;
		this.data = data;
		dataset = new XYMultipleSeriesDataset();
		renderer = new XYMultipleSeriesRenderer();

		renderer.setInScroll(true);
		Iterator<SeriesData> seriesIterator = data.getSeriesIterator();
		while (seriesIterator.hasNext()) {
			SeriesData s = seriesIterator.next();
			XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
			renderer.addSeriesRenderer(currentRenderer);
			
			configureSeries(s, currentRenderer);
			renderSeries(s);
		}
		
		renderAnnotations();
		configure();
		setMargins();
	} 
	
	public void setTitle(String title) {
		renderer.setChartTitle(title);
		renderer.setChartTitleTextSize(TEXT_SIZE);
		setMargins();
	}
	
	private void setMargins() {
		int topMargin = renderer.getChartTitle().equals("") ? 0 : 30;
		int rightMargin = 20;
		int leftMargin = renderer.getYTitle().equals("") ? 20 : 70;
		int bottomMargin = renderer.getXTitle().equals("") ? 0 : 50;
		renderer.setMargins(new int[]{topMargin, leftMargin, bottomMargin, rightMargin});
	}
		
	public View getView() {
		if (isBubble()) {
        	return ChartFactory.getBubbleChartView(context, dataset, renderer);
		}
        return ChartFactory.getLineChartView(context, dataset, renderer);
	}
	
	private void renderSeries(SeriesData s) {
		XYSeries series = isBubble() ? new XYValueSeries("") : new XYSeries("");
		dataset.addSeries(series);

		// achartengine won't render a bubble chart with its points out of order
		Vector<XYPointData> sortedPoints = new Vector<XYPointData>(s.size());
		Iterator<XYPointData> pointsIterator = s.getPointsIterator();
		while (pointsIterator.hasNext()) {
			sortedPoints.add(pointsIterator.next());
		}
		Collections.sort(sortedPoints, new PointComparator());
		
		for (XYPointData p : sortedPoints) {
			if (isBubble()) {
				BubblePointData b = (BubblePointData) p;
				((XYValueSeries) series).add(b.getX(), b.getY(), b.getRadius());
			}
			else {
				series.add(p.getX(), p.getY());
			}
		}
	}
	
	private boolean isBubble() {
		return data.getType().equals(Graph.TYPE_BUBBLE);
	}
	
	public LinearLayout.LayoutParams getLayoutParams() {
		return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, height);	
	}
	
	public void setHeight(int height) {
		this.height = height;
        configureLabels(false, height);
	}
	
	public void setWidth(int width) {
		this.width = width;
        configureLabels(true, width);
	}
	
	private void configureLabels(boolean isX, int dimension) {
		configureLabels(isX);
		int count = isX ? renderer.getXLabels() : renderer.getYLabels();
		while (count * TEXT_SIZE > dimension) {
			count = count % 2 != 0 && count % 3 == 0 ? count / 3 : count / 2;
			if (isX) {
				renderer.setXLabels(count);
			}
			else {
				renderer.setYLabels(count);
			}
		}
	}
	
	private void configureLabels(boolean isX) {
		if (isX && data.getConfiguration("x-label-count") != null) {
			renderer.setXLabels(Integer.valueOf(data.getConfiguration("x-label-count")));
		}
		if (!isX && data.getConfiguration("y-label-count") != null) {
			renderer.setYLabels(Integer.valueOf(data.getConfiguration("y-label-count")));
		}
		
	}
	
	private void renderAnnotations() {
		Iterator<AnnotationData> i = data.getAnnotationIterator();
		if (i.hasNext()) {
			XYSeries series = new XYSeries("");
			while (i.hasNext()) {
				AnnotationData a = i.next();
				series.addAnnotation(a.getAnnotation(), a.getX(), a.getY());
			}
			series.add(0.0, 0.0);
			dataset.addSeries(series);
			XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
			currentRenderer.setAnnotationsTextSize(TEXT_SIZE);
			currentRenderer.setAnnotationsColor(context.getResources().getColor(R.drawable.black));
			renderer.addSeriesRenderer(currentRenderer);
		}
	}

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
			currentRenderer.setColor(context.getResources().getColor(R.drawable.black));
		}
		
		fillOutsideLine(s, currentRenderer, "fill-above", XYSeriesRenderer.FillOutsideLine.Type.ABOVE);
		fillOutsideLine(s, currentRenderer, "fill-below", XYSeriesRenderer.FillOutsideLine.Type.BELOW);
	}
	
	private void fillOutsideLine(SeriesData s, XYSeriesRenderer currentRenderer, String property, XYSeriesRenderer.FillOutsideLine.Type type) {
		property = s.getConfiguration(property);
		if (property != null) {
			XYSeriesRenderer.FillOutsideLine fill = new XYSeriesRenderer.FillOutsideLine(type);
			fill.setColor(Color.parseColor(property));
			currentRenderer.addFillOutsideLine(fill);
		}
	}

	private void configure() {
		// Default options
		renderer.setBackgroundColor(context.getResources().getColor(R.drawable.white));
		renderer.setMarginsColor(context.getResources().getColor(R.drawable.white));
		renderer.setLabelsColor(context.getResources().getColor(R.drawable.black));
		renderer.setXLabelsColor(context.getResources().getColor(R.drawable.black));
		renderer.setYLabelsColor(0, context.getResources().getColor(R.drawable.black));
		renderer.setXLabelsAlign(Paint.Align.CENTER);
		renderer.setYLabelsAlign(Paint.Align.RIGHT);
		renderer.setYLabelsPadding(10);
		renderer.setAxesColor(context.getResources().getColor(R.drawable.black));
		renderer.setLabelsTextSize(TEXT_SIZE);
		renderer.setAxisTitleTextSize(TEXT_SIZE);
		renderer.setShowLabels(true);
		renderer.setApplyBackgroundColor(true);
		renderer.setShowLegend(false);
		renderer.setShowGrid(true);
		renderer.setPanEnabled(false, false);

		// User-configurable options
		if (data.getConfiguration("x-axis-title") != null) {
			renderer.setXTitle(data.getConfiguration("x-axis-title"));
		}
		if (data.getConfiguration("y-axis-title") != null) {
			renderer.setYTitle(data.getConfiguration("y-axis-title"));
		}
		setMargins();

		if (data.getConfiguration("x-axis-min") != null) {
			renderer.setXAxisMin(Double.valueOf(data.getConfiguration("x-axis-min")));
		}
		if (data.getConfiguration("y-axis-min") != null) {
			renderer.setYAxisMin(Double.valueOf(data.getConfiguration("y-axis-min")));
		}
		
		if (data.getConfiguration("x-axis-max") != null) {
			renderer.setXAxisMax(Double.valueOf(data.getConfiguration("x-axis-max")));
		}
		if (data.getConfiguration("y-axis-max") != null) {
			renderer.setYAxisMax(Double.valueOf(data.getConfiguration("y-axis-max")));
		}
		
		configureLabels(true);
		configureLabels(false);
	}
	
	/**
	 * Comparator to sort PointData objects by x value.
	 * @author jschweers
	 *
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
