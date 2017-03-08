package org.commcare.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.IdRes;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.CommCareGraphActivity;
import org.commcare.print.TemplatePrinterActivity;
import org.commcare.cases.entity.Entity;
import org.commcare.dalvik.R;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphException;
import org.commcare.graph.view.GraphLoader;
import org.commcare.graph.view.GraphView;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.commcare.utils.DetailCalloutListener;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.MediaUtil;
import org.commcare.views.media.AudioPlaybackButton;
import org.commcare.views.media.ViewId;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Timer;

/**
 * @author ctsims
 */
public class EntityDetailView extends FrameLayout {

    private final TextView label;
    private final TextView data;
    private final TextView spacer;
    private final Button callout;
    private final View addressView;
    private final Button addressButton;
    private final TextView addressText;
    private final ImageView imageView;
    private final View calloutView;
    private final Button calloutButton;
    private final TextView calloutText;
    private final ImageButton calloutImageButton;
    private final AspectRatioLayout graphLayout;
    private final Hashtable<Integer, Hashtable<Integer, View>> graphViewsCache;    // index => { orientation => GraphView }
    private final Hashtable<Integer, Intent> graphIntentsCache;    // index => intent
    private final Set<Integer> graphsWithErrors;
    private final ImageButton videoButton;
    private final AudioPlaybackButton audioButton;
    private final View valuePane;
    private View currentView;
    private final LinearLayout detailRow;
    private final LinearLayout.LayoutParams origValue;
    private final LinearLayout.LayoutParams origLabel;
    private final LinearLayout.LayoutParams fill;
    private HashMap<View, String> graphHTMLMap = new HashMap<>();

    // Potential "forms" of a detail field
    private static final String FORM_VIDEO = MediaUtil.FORM_VIDEO;
    private static final String FORM_AUDIO = MediaUtil.FORM_AUDIO;
    private static final String FORM_PHONE = "phone";
    private static final String FORM_ADDRESS = "address";
    private static final String FORM_IMAGE = MediaUtil.FORM_IMAGE;
    private static final String FORM_GRAPH = "graph";
    private static final String FORM_CALLOUT = "callout";

    @IdRes
    private static final int IMAGE_VIEW_ID = 23422634;

    @IdRes
    private static final int CALLOUT_BUTTON_ID = 23422634;

    private static final int TEXT = 0;
    private static final int PHONE = 1;
    private static final int ADDRESS = 2;
    private static final int IMAGE = 3;
    private static final int VIDEO = 4;
    private static final int AUDIO = 5;
    private static final int GRAPH = 6;
    private static final int CALLOUT = 7;

    private int current = TEXT;

    private DetailCalloutListener listener;

    public EntityDetailView(Context context, Detail d, Entity e,
                            int index, int detailNumber) {
        super(context);

        detailRow = (LinearLayout)View.inflate(context, R.layout.component_entity_detail_item, null);
        label = (TextView)detailRow.findViewById(R.id.detail_type_text);
        spacer = (TextView)detailRow.findViewById(R.id.entity_detail_spacer);
        data = (TextView)detailRow.findViewById(R.id.detail_value_text);
        currentView = data;
        valuePane = detailRow.findViewById(R.id.detail_value_pane);
        videoButton = (ImageButton)detailRow.findViewById(R.id.detail_video_button);

        ViewId uniqueId = ViewId.buildTableViewId(detailNumber, index, true);
        String audioText = e.getFieldString(index);
        audioButton = new AudioPlaybackButton(context, audioText, uniqueId, false);
        detailRow.addView(audioButton);
        audioButton.setVisibility(View.GONE);

        callout = (Button)detailRow.findViewById(R.id.detail_value_phone);
        addressView = detailRow.findViewById(R.id.detail_address_view);
        addressText = (TextView)addressView.findViewById(R.id.detail_address_text);
        addressButton = (Button)addressView.findViewById(R.id.detail_address_button);

        imageView = (ImageView)detailRow.findViewById(R.id.detail_value_image);
        int height;
        if (CommCarePreferences.isSmartInflationEnabled()) {
            // If using smart inflation, we don't want to do any other artificial resizing of images
            height = LayoutParams.WRAP_CONTENT;
        } else {
            // otherwise, should let the image view stretch to fill the height of the row
            height = LayoutParams.MATCH_PARENT;
        }
        FrameLayout.LayoutParams imageViewParams =
                new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, height);
        imageView.setLayoutParams(imageViewParams);

        graphLayout = (AspectRatioLayout)detailRow.findViewById(R.id.graph);
        calloutView = detailRow.findViewById(R.id.callout_view);
        calloutText = (TextView)detailRow.findViewById(R.id.callout_text);
        calloutButton = (Button)detailRow.findViewById(R.id.callout_button);
        calloutImageButton = (ImageButton)detailRow.findViewById(R.id.callout_image_button);
        graphViewsCache = new Hashtable<>();
        graphsWithErrors = new HashSet<>();
        graphIntentsCache = new Hashtable<>();
        origLabel = (LinearLayout.LayoutParams)label.getLayoutParams();
        origValue = (LinearLayout.LayoutParams)valuePane.getLayoutParams();

        fill = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        this.addView(detailRow, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        setParams(d, e, index, detailNumber);
    }

    public void setCallListener(final DetailCalloutListener listener) {
        this.listener = listener;
    }

    public void setParams(Detail d, Entity e, int index, int detailNumber) {
        String labelText = d.getFields()[index].getHeader().evaluate();
        label.setText(labelText);
        spacer.setText(labelText);

        Object field = e.getField(index);
        String textField = e.getFieldString(index);
        boolean veryLong = false;
        String form = d.getTemplateForms()[index];
        if (FORM_PHONE.equals(form)) {
            setupPhoneNumber(textField);
        } else if (FORM_CALLOUT.equals(form) && (field instanceof CalloutData)) {
            veryLong = setupCallout((CalloutData)field);
        } else if (FORM_ADDRESS.equals(form)) {
            setupAddress(textField);
        } else if (FORM_IMAGE.equals(form)) {
            veryLong = setupImage(textField);
        } else if (FORM_GRAPH.equals(form) && field instanceof GraphData) {
            // if graph parsing had errors, they'll be stored as a string
            setupGraph(index, labelText, field);
        } else if (FORM_AUDIO.equals(form)) {
            ViewId uniqueId = ViewId.buildTableViewId(detailNumber, index, true);
            audioButton.modifyButtonForNewView(uniqueId, textField, true);
            updateCurrentView(AUDIO, audioButton);
        } else if (FORM_VIDEO.equals(form)) { //TODO: Why is this given a special string?
            setupVideo(textField);
        } else {
            data.setText((textField));
            if (textField != null && textField.length() > this.getContext().getResources().getInteger(R.integer.detail_size_cutoff)) {
                veryLong = true;
            }

            updateCurrentView(TEXT, data);
        }

        if (veryLong) {
            detailRow.setOrientation(LinearLayout.VERTICAL);
            spacer.setVisibility(View.GONE);
            label.setLayoutParams(fill);
            valuePane.setLayoutParams(fill);
        } else {
            if (detailRow.getOrientation() != LinearLayout.HORIZONTAL) {
                detailRow.setOrientation(LinearLayout.HORIZONTAL);
                spacer.setVisibility(View.INVISIBLE);
                label.setLayoutParams(origLabel);
                valuePane.setLayoutParams(origValue);
            }
        }
    }

    private void setupPhoneNumber(String textField) {
        callout.setText(textField);
        if (current != PHONE) {
            callout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.callRequested(callout.getText().toString());
                }
            });
            this.removeView(currentView);
            updateCurrentView(PHONE, callout);
        }
    }

    private boolean setupCallout(final CalloutData callout) {
        boolean veryLong = false;

        String imagePath = callout.getImage();

        if (imagePath != null) {
            // use image as button, if available
            calloutButton.setVisibility(View.GONE);
            calloutText.setVisibility(View.GONE);

            Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imagePath);

            if (b == null) {
                calloutImageButton.setImageDrawable(null);
            } else {
                // Figure out whether our image small or large.
                if (b.getWidth() > (getScreenWidth() / 2)) {
                    veryLong = true;
                }

                calloutImageButton.setPadding(10, 10, 10, 10);
                calloutImageButton.setAdjustViewBounds(true);
                calloutImageButton.setImageBitmap(b);
                calloutImageButton.setId(CALLOUT_BUTTON_ID);
            }

            calloutImageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.performCallout(callout, CALLOUT);
                }
            });
        } else {
            calloutImageButton.setVisibility(View.GONE);
            calloutText.setVisibility(View.GONE);

            String displayName = callout.getDisplayName();
            // use display name if available, otherwise use URI
            if (displayName != null) {
                calloutButton.setText(displayName);
            } else {
                String actionName = callout.getActionName();
                calloutButton.setText(actionName);
            }


            calloutButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.performCallout(callout, CALLOUT);
                }
            });
        }

        updateCurrentView(CALLOUT, calloutView);
        return veryLong;
    }

    private void setupAddress(final String address) {
        addressText.setText(address);
        if (current != ADDRESS) {
            addressButton.setText(Localization.get("select.address.show"));
            addressButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.addressRequested(GeoUtils.getGeoIntentURI(address));
                }
            });
            updateCurrentView(ADDRESS, addressView);
        }
    }

    private boolean setupImage(String textField) {
        boolean veryLong = false;
        Bitmap b = MediaUtil.inflateDisplayImage(getContext(), textField);

        if (b == null) {
            imageView.setImageDrawable(null);
        } else {
            //Ok, so. We should figure out whether our image is large or small.
            if (b.getWidth() > (getScreenWidth() / 2)) {
                veryLong = true;
            }

            imageView.setPadding(10, 10, 10, 10);
            imageView.setAdjustViewBounds(true);
            imageView.setImageBitmap(b);
            imageView.setId(IMAGE_VIEW_ID);
        }

        updateCurrentView(IMAGE, imageView);
        return veryLong;
    }

    private void setupGraph(int index, String labelText, Object field) {
        // Get graph view and intent
        int orientation = getResources().getConfiguration().orientation;
        boolean cached = true;
        View graphView = getGraphViewFromCache(index, orientation);
        if (graphView == null) {
            cached = false;
            graphView = getGraphView(index, labelText, (GraphData)field, orientation);
        }
        final Intent finalIntent = getGraphIntent(index, labelText, (GraphData) field);

        // Open full-screen graph intent on double tap
        if (!graphsWithErrors.contains(index)) {
            enableGraphIntent((WebView) graphView, finalIntent);
        }

        // Add graph child views to graph layout
        graphLayout.removeAllViews();
        graphLayout.addView(graphView, GraphView.getLayoutParams());
        if (!cached && !graphsWithErrors.contains(index)) {
            addSpinnerToGraph((WebView) graphView, graphLayout);
        }

        if (current != GRAPH) {
            // Hide field label and expand value to take up full screen width
            LinearLayout.LayoutParams graphValueLayout = new LinearLayout.LayoutParams((ViewGroup.LayoutParams)origValue);
            graphValueLayout.weight = 10;
            valuePane.setLayoutParams(graphValueLayout);

            label.setVisibility(View.GONE);
            data.setVisibility(View.GONE);
            updateCurrentView(GRAPH, graphLayout);
        }
    }

    // TODO: Will probably want the print button to live at the case detail-level rather than
    // the level of individual graphs, so that users can create print templates that expect
    // multiple graphs, plus other information
    private void addPrintGraphButton(final View graphView) {
        Button printButton = new Button(getContext());
        printButton.setText("PRINT");

        printButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getContext(), TemplatePrinterActivity.class);
                i.putExtra(TemplatePrinterActivity.KEY_GRAPH_TO_PRINT, graphHTMLMap.get(graphView));
                getContext().startActivity(i);
            }
        });

        graphLayout.addView(printButton);
    }

    private void setupVideo(String textField) {
        String localLocation = null;
        try {
            localLocation = ReferenceManager.instance().DeriveReference(textField).getLocalURI();
            if (localLocation.startsWith("/")) {
                //TODO: This should likely actually be happening with the getLocalURI _anyway_.
                localLocation = FileUtil.getGlobalStringUri(localLocation);
            }
        } catch (InvalidReferenceException ire) {
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Couldn't understand video reference format: " + localLocation + ". Error: " + ire.getMessage());
        }

        final String location = localLocation;

        videoButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                listener.playVideo(location);
            }

        });

        if (location == null) {
            videoButton.setEnabled(false);
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "No local video reference available for ref: " + textField);
        } else {
            videoButton.setEnabled(true);
        }

        updateCurrentView(VIDEO, videoButton);
    }

    private void updateCurrentView(int newCurrent, View newView) {
        if (newCurrent != current) {
            currentView.setVisibility(View.GONE);
            newView.setVisibility(View.VISIBLE);
            currentView = newView;
            current = newCurrent;
        }

        if (current != GRAPH) {
            label.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams graphValueLayout = new LinearLayout.LayoutParams((ViewGroup.LayoutParams)origValue);
            graphValueLayout.weight = 10;
            valuePane.setLayoutParams(origValue);
        }
    }

    private int getScreenWidth() {
        Display display = ((WindowManager)this.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        return display.getWidth();
    }

    @SuppressWarnings("AddJavascriptInterface")
    private void addSpinnerToGraph(WebView graphView, ViewGroup graphLayout) {
        // WebView.addJavascriptInterface should not be called with minSdkVersion < 17
        // for security reasons: JavaScript can use reflection to manipulate application
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        final ProgressBar spinner = new ProgressBar(this.getContext(), null, android.R.attr.progressBarStyleLarge);
        spinner.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        GraphLoader graphLoader = new GraphLoader((Activity) this.getContext(), spinner);

        // Set up interface that JavaScript will call to hide the spinner
        // once the graph has finished rendering.
        graphView.addJavascriptInterface(graphLoader, "Android");

        // The above JavaScript interface doesn't load properly 100% of the time.
        // Worst case, hide the spinner after ten seconds.
        Timer spinnerTimer = new Timer();
        spinnerTimer.schedule(graphLoader, 10000);
        graphLayout.addView(spinner);
    }

    private View getGraphViewFromCache(int index, int orientation) {
        if (graphViewsCache.get(index) != null) {
            return graphViewsCache.get(index).get(orientation);
        }
        graphViewsCache.put(index, new Hashtable<Integer, View>());
        return null;
    }

    /**
     * Generate graph view. May return WebView displaying graph, or TextView displaying error.
     */
    private View getGraphView(int index, String title, GraphData field, int orientation) {
        Context context = getContext();
        View graphView;
        GraphView g = new GraphView(context, title, false);
        try {
            String graphHTML = field.getGraphHTML(title);
            graphView = g.getView(graphHTML);
            graphLayout.setRatio((float)g.getRatio(field), (float)1);
            graphHTMLMap.put(graphView, g.myHTML);
        } catch (GraphException ex) {
            graphView = new TextView(context);
            int padding = (int)context.getResources().getDimension(R.dimen.spacer_small);
            graphView.setPadding(padding, padding, padding, padding);
            ((TextView)graphView).setText(ex.getMessage());
            graphsWithErrors.add(index);
        }
        graphViewsCache.get(index).put(orientation, graphView);
        return graphView;
    }

    /**
     * Fetch full-screen graph intent from cache, or create it.
     */
    private Intent getGraphIntent(int index, String title, GraphData field) {
        Intent graphIntent = graphIntentsCache.get(index);
        if (graphIntent == null && !graphsWithErrors.contains(index)) {
            GraphView g = new GraphView(this.getContext(), title, true);
            try {
                String html = field.getGraphHTML(title);
                graphIntent = g.getIntent(html, CommCareGraphActivity.class);
                graphIntentsCache.put(index, graphIntent);
            } catch (GraphException ex) {
                graphsWithErrors.add(index);
            }
        }

        return graphIntent;
    }

    /**
     * Set up event handling so that full-screen graph intent opens on double tap of given view.
     */
    private void enableGraphIntent(WebView graphView, final Intent graphIntent) {
        final Context context = this.getContext();
        final GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                context.startActivity(graphIntent);
                return true;
            }
        });
        graphView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return detector.onTouchEvent(event);
            }
        });
    }
}
