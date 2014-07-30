/**
 * 
 */
package org.commcare.android.view;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Pattern;

import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.ViewId;
import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.model.XYValueSeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.graph.ConfigurableData;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.PointData;
import org.commcare.suite.model.graph.SeriesData;
import org.commcare.util.CommCareSession;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.odk.collect.android.views.media.AudioController;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;

/**
 * @author ctsims
 *
 */
public class EntityDetailView extends FrameLayout {
	
	private TextView label;
	private TextView data;
	private TextView spacer;
	private Button callout;
	private View addressView;
	private Button addressButton;
	private TextView addressText;
	private ImageView imageView;
	private LinearLayout graphLayout;
	private ImageButton videoButton;
	private AudioButton audioButton;
	private View valuePane;
	private View currentView;
	private AudioController controller;
	private LinearLayout detailRow;
	private LinearLayout.LayoutParams origValue;
	private LinearLayout.LayoutParams origLabel;
	private LinearLayout.LayoutParams fill;
	
	private static final String FORM_VIDEO = "video";
	private static final String FORM_AUDIO = "audio";
	private static final String FORM_PHONE = "phone";
	private static final String FORM_ADDRESS = "address";
	private static final String FORM_IMAGE = "image";
	private static final String FORM_GRAPH = "graph";

	private static final int TEXT = 0;
	private static final int PHONE = 1;
	private static final int ADDRESS = 2;
	private static final int IMAGE = 3;
	private static final int VIDEO = 4;
	private static final int AUDIO = 5;
	private static final int GRAPH = 6;
	
	private static final int GRAPH_TEXT_SIZE = 21;
	
	int current = TEXT;
	
	DetailCalloutListener listener;

	public EntityDetailView(Context context, CommCareSession session, Detail d, Entity e, int index,
			AudioController controller, int detailNumber) {
		super(context);		
	    this.controller = controller;
	    
		detailRow = (LinearLayout)View.inflate(context, R.layout.component_entity_detail_item, null);
        label = (TextView)detailRow.findViewById(R.id.detail_type_text);
        spacer = (TextView)detailRow.findViewById(R.id.entity_detail_spacer); 
	    data = (TextView)detailRow.findViewById(R.id.detail_value_text);
	    currentView = data;
	    valuePane = detailRow.findViewById(R.id.detail_value_pane);
	    videoButton = (ImageButton)detailRow.findViewById(R.id.detail_video_button);
	    
	    ViewId uniqueId = new ViewId(detailNumber, index, true);
	    String audioText = e.getFieldString(index);
	    audioButton = new AudioButton(context, audioText, uniqueId, controller, false);
	    detailRow.addView(audioButton);
	    audioButton.setVisibility(View.GONE);
	    
	    callout = (Button)detailRow.findViewById(R.id.detail_value_phone);
	    //TODO: Still useful?
	    //callout.setInputType(InputType.TYPE_CLASS_PHONE);
	    addressView = (View)detailRow.findViewById(R.id.detail_address_view);
	    addressText = (TextView)addressView.findViewById(R.id.detail_address_text);
	    addressButton = (Button)addressView.findViewById(R.id.detail_address_button);
	    imageView = (ImageView)detailRow.findViewById(R.id.detail_value_image);
	    graphLayout = (LinearLayout)detailRow.findViewById(R.id.graph_layout);
	    origLabel = (LinearLayout.LayoutParams)label.getLayoutParams();
	    origValue = (LinearLayout.LayoutParams)valuePane.getLayoutParams();

	    fill = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
	    this.addView(detailRow, FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
	    setParams(session, d, e, index, detailNumber);
	}
	
	public void setCallListener(final DetailCalloutListener listener) {
		this.listener = listener;
	}

	public void setParams(CommCareSession session, Detail d, Entity e, int index, int detailNumber) {
		String labelText = d.getFields()[index].getHeader().evaluate();
		label.setText(labelText);
		spacer.setText(labelText);
		
		Object field = e.getField(index);
		String textField = e.getFieldString(index);
		boolean veryLong = false;
		String form = d.getTemplateForms()[index];
		if(FORM_PHONE.equals(form)) {
			callout.setText(textField);
			if(current != PHONE) {
				callout.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						listener.callRequested(callout.getText().toString());				
					}
					
				});
				currentView.setVisibility(View.GONE);
				callout.setVisibility(View.VISIBLE);
				this.removeView(currentView);
				currentView = callout;
				current = PHONE;
			}
		} else if(FORM_ADDRESS.equals(form)) {
			final String address = textField;
			
			addressText.setText(address);
			if(current != ADDRESS) {
				addressButton.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						listener.addressRequested(MediaUtil.getGeoIntentURI(address));
					}
					
				});
				
				currentView.setVisibility(View.GONE);
				addressView.setVisibility(View.VISIBLE);
				currentView = addressView;
				current = ADDRESS;
			}
		} else if(FORM_IMAGE.equals(form)) {
			String imageLocation = textField;
			Bitmap b = MediaUtil.getScaledImageFromReference(this.getContext(),imageLocation);
			
			if(b == null) {
				imageView.setImageDrawable(null);
			} else {
				//Ok, so. We should figure out whether our image is large or small.
				if(b.getWidth() > (getScreenWidth() / 2)) {
					veryLong = true;
				}
				
				imageView.setPadding(10, 10, 10, 10);
				imageView.setAdjustViewBounds(true);
				imageView.setImageBitmap(b);
				imageView.setId(23422634);
			}
			
			if(current != IMAGE) {
				currentView.setVisibility(View.GONE);
				imageView.setVisibility(View.VISIBLE);
				currentView = imageView;
				current = IMAGE;
			}
		} else if (FORM_GRAPH.equals(form)) {
			GraphData graphData = (GraphData) field;
			XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
			XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
			renderer.setInScroll(true);
			Iterator<SeriesData> seriesIterator = graphData.getSeriesIterator();
			boolean isBubble = graphData.getType().equals(Graph.TYPE_BUBBLE);
			while (seriesIterator.hasNext()) {
				SeriesData s = seriesIterator.next();
				XYSeries series = isBubble ? new XYValueSeries("") : new XYSeries("");
				dataset.addSeries(series);
				XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
				renderer.addSeriesRenderer(currentRenderer);
				
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
					currentRenderer.setColor(getContext().getResources().getColor(R.drawable.black));
				}
				
				String[] fillProperties = new String[]{"fill-above", "fill-below"};
				XYSeriesRenderer.FillOutsideLine.Type[] fillTypes = new XYSeriesRenderer.FillOutsideLine.Type[]{
					XYSeriesRenderer.FillOutsideLine.Type.ABOVE, 
					XYSeriesRenderer.FillOutsideLine.Type.BELOW
				};
				for (int i = 0; i < fillProperties.length; i++) {
					String fillProperty = s.getConfiguration(fillProperties[i]);
					if (fillProperty != null) {
						XYSeriesRenderer.FillOutsideLine fill = new XYSeriesRenderer.FillOutsideLine(fillTypes[i]);
						fill.setColor(Color.parseColor(fillProperty));
						currentRenderer.addFillOutsideLine(fill);
					}					
				}
				
				
				// achartengine won't render a bubble chart with its points out of order
				Vector<PointData> sortedPoints = new Vector<PointData>(s.size());
				Iterator<PointData> pointsIterator = s.getPointsIterator();
				while (pointsIterator.hasNext()) {
					sortedPoints.add(pointsIterator.next());
				}
				Collections.sort(sortedPoints, new PointComparator());
				
				for (PointData p : sortedPoints) {
					if (isBubble) {
						((XYValueSeries) series).add(p.getX(), p.getY(), p.getRadius());
					}
					else {
						series.add(p.getX(), p.getY());
					}
				}
			}
			
			// Annotations
			Iterator<PointData> i = graphData.getAnnotationIterator();
			if (i.hasNext()) {
				XYSeries series = new XYSeries("");
				while (i.hasNext()) {
					PointData p = i.next();
					series.addAnnotation(p.getAnnotation(), p.getX(), p.getY());
				}
				series.add(0.0, 0.0);
				dataset.addSeries(series);
				XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
				currentRenderer.setAnnotationsTextSize(GRAPH_TEXT_SIZE);
				currentRenderer.setAnnotationsColor(getContext().getResources().getColor(R.drawable.black));
				renderer.addSeriesRenderer(currentRenderer);
			}

			configureGraph(graphData, renderer);
			renderer.setChartTitle(labelText);
			renderer.setChartTitleTextSize(GRAPH_TEXT_SIZE);
			int topMargin = labelText.equals("") ? 0 : 30;
			int leftMargin = renderer.getYTitle().equals("") ? 20 : 70;
			renderer.setMargins(new int[]{topMargin, leftMargin, 0, 20});  // top, left, bottom, right
			
            GraphicalView graph = isBubble
            	? ChartFactory.getBubbleChartView(getContext(), dataset, renderer)
            	: ChartFactory.getLineChartView(getContext(), dataset, renderer)
            ;
			
            int width = getScreenWidth();
            int height = width / 2;
            reduceLabels(true, renderer, width);
            reduceLabels(false, renderer, height);
            graphLayout.addView(graph, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, height));
            
			if (current != GRAPH) {
				label.setVisibility(View.GONE);
				LinearLayout.LayoutParams graphValueLayout = new LinearLayout.LayoutParams(origValue);
				graphValueLayout.weight = 10;
				valuePane.setLayoutParams(graphValueLayout);

				data.setVisibility(View.GONE);
				currentView.setVisibility(View.GONE);
				graphLayout.setVisibility(View.VISIBLE);
				currentView = graphLayout;
				current = GRAPH;
			}
		} else if (FORM_AUDIO.equals(form)) {
			ViewId uniqueId = new ViewId(detailNumber, index, true);
			audioButton.modifyButtonForNewView(uniqueId, textField, true);
			if (current != AUDIO) {
				currentView.setVisibility(View.GONE);
				audioButton.setVisibility(View.VISIBLE);
				currentView = audioButton;
				current = AUDIO;
			}
		} else if(FORM_VIDEO.equals(form)) { //TODO: Why is this given a special string?
			String videoLocation = textField;
			String localLocation = null;
			try{ 
				localLocation = ReferenceManager._().DeriveReference(videoLocation).getLocalURI();
				if(localLocation.startsWith("/")) {
					//TODO: This should likely actually be happening with the getLocalURI _anyway_.
					localLocation = FileUtil.getGlobalStringUri(localLocation);
				}
			} catch(InvalidReferenceException ire) {
				Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Couldn't understand video reference format: " + localLocation + ". Error: " + ire.getMessage());
			}
			
			final String location = localLocation;
			
			videoButton.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					listener.playVideo(location);	
				}
				
			});
			
			if(location == null) {
				videoButton.setEnabled(false);
				Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "No local video reference available for ref: " + videoLocation);
			} else {
				videoButton.setEnabled(true);
			}
			
			if(current != VIDEO) {
				currentView.setVisibility(View.GONE);
				videoButton.setVisibility(View.VISIBLE);
				currentView = videoButton;
				current = VIDEO;
			}
		} else {
			String text = textField;
			data.setText(text);
			if(text != null && text.length() > this.getContext().getResources().getInteger(R.integer.detail_size_cutoff)) {
				veryLong = true;
			}
			if(current != TEXT) {
				currentView.setVisibility(View.GONE);
				data.setVisibility(View.VISIBLE);
				currentView = data;
				current = TEXT;
			}
		}
		
		if (!FORM_GRAPH.equals(form)) {
			label.setVisibility(View.VISIBLE);
			LinearLayout.LayoutParams graphValueLayout = new LinearLayout.LayoutParams(origValue);
			graphValueLayout.weight = 10;
			valuePane.setLayoutParams(origValue);
			data.setVisibility(View.VISIBLE);
		}
		
		if(veryLong) {
			detailRow.setOrientation(LinearLayout.VERTICAL);
			spacer.setVisibility(View.GONE);
			label.setLayoutParams(fill);
			valuePane.setLayoutParams(fill);
		} else {
			if(detailRow.getOrientation() != LinearLayout.HORIZONTAL) {
				detailRow.setOrientation(LinearLayout.HORIZONTAL);
				spacer.setVisibility(View.INVISIBLE);
				label.setLayoutParams(origLabel);
				valuePane.setLayoutParams(origValue);
			}
		}
	}
	
	private int getScreenWidth() {
		Display display = ((WindowManager) this.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		return display.getWidth();
	}
	
	private void reduceLabels(boolean isX, XYMultipleSeriesRenderer renderer, int dimension) {
		int count = isX ? renderer.getXLabels() : renderer.getYLabels();
		while (count * GRAPH_TEXT_SIZE > dimension) {
			count = count % 2 != 0 && count % 3 == 0 ? count / 3 : count / 2;
			if (isX) {
				renderer.setXLabels(count);
			}
			else {
				renderer.setYLabels(count);
			}
		}
	}
	
	private void configureGraph(GraphData data, XYMultipleSeriesRenderer renderer) {
		Context context = getContext();
		
		// Default options
		renderer.setBackgroundColor(context.getResources().getColor(R.drawable.white));
		renderer.setMarginsColor(context.getResources().getColor(R.drawable.white));
		renderer.setLabelsColor(getContext().getResources().getColor(R.drawable.black));
		renderer.setXLabelsColor(context.getResources().getColor(R.drawable.black));
		renderer.setYLabelsColor(0, context.getResources().getColor(R.drawable.black));
		renderer.setYLabelsAlign(Paint.Align.RIGHT);
		renderer.setYLabelsPadding(10);
		renderer.setAxesColor(context.getResources().getColor(R.drawable.black));
		renderer.setLabelsTextSize(GRAPH_TEXT_SIZE);
		renderer.setAxisTitleTextSize(GRAPH_TEXT_SIZE);
		renderer.setShowLabels(true);
		renderer.setApplyBackgroundColor(true);
		renderer.setShowLegend(false);
		renderer.setShowGrid(true);
		renderer.setPanEnabled(false, false);

		// User-configurable options
		if (data.getConfiguration("x-label-count") != null) {
			renderer.setXLabels(Integer.valueOf(data.getConfiguration("x-label-count")));
		}
		if (data.getConfiguration("y-label-count") != null) {
			renderer.setYLabels(Integer.valueOf(data.getConfiguration("y-label-count")));
		}
		
		if (data.getConfiguration("x-axis-title") != null) {
			renderer.setXTitle(data.getConfiguration("x-axis-title"));
		}
		if (data.getConfiguration("y-axis-title") != null) {
			renderer.setYTitle(data.getConfiguration("y-axis-title"));
		}

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
	}
	
	/**
	 * Comparator to sort PointData objects by x value.
	 * @author jschweers
	 *
	 */
	private class PointComparator implements Comparator<PointData> {
		@Override
		public int compare(PointData lhs, PointData rhs) {
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
