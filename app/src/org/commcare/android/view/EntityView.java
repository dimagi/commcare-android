package org.commcare.android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.android.models.AsyncEntity;
import org.commcare.android.models.Entity;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.util.MediaUtil;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.ViewId;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class EntityView extends LinearLayout {

    private View[] views;
    private String[] forms;
    private TextToSpeech tts;
    private String[] searchTerms;
    private String[] mHints;
    private Context context;
    private Hashtable<Integer, Hashtable<Integer, View>> renderedGraphsCache;    // index => { orientation => GraphView }
    private long rowId;
    public static final String FORM_AUDIO = "audio";
    public static final String FORM_IMAGE = "image";
    public static final String FORM_GRAPH = "graph";
    public static final String FORM_CALLLOUT = "callout";

    // Flag indicating if onMeasure has already been called for the first time on this view
    private boolean onMeasureCalled = false;
    // Maintains a queue of image layouts that need to be re-drawn once onMeasure has been called
    private HashMap<View, String> imageViewsToRedraw = new HashMap<>();

    private boolean mFuzzySearchEnabled = true;
    private boolean mIsAsynchronous = false;

    /*
     * Constructor for row/column contents
     */
    public EntityView(Context context, Detail d, Entity e, TextToSpeech tts,
                      String[] searchTerms, long rowId, boolean mFuzzySearchEnabled) {
        super(context);
        this.context = context;
        //this is bad :(
        mIsAsynchronous = e instanceof AsyncEntity;
        this.searchTerms = searchTerms;
        this.tts = tts;
        this.renderedGraphsCache = new Hashtable<Integer, Hashtable<Integer, View>>();
        this.rowId = rowId;
        this.views = new View[e.getNumFields()];
        this.forms = d.getTemplateForms();
        this.mHints = d.getTemplateSizeHints();

        for (int i = 0; i < views.length; ++i) {
            if (mHints[i] == null || !mHints[i].startsWith("0")) {
                views[i] = initView(e.getField(i), forms[i], new ViewId(rowId, i, false), e.getSortField(i));
                views[i].setId(i);
            }
        }
        refreshViewsForNewEntity(e, false, rowId);
        for (int i = 0; i < views.length; i++) {
            LayoutParams l = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
            if (views[i] != null) {
                addView(views[i], l);
            }
        }

        this.mFuzzySearchEnabled = mFuzzySearchEnabled;
    }

    /*
     * Constructor for row/column headers
     */
    public EntityView(Context context, Detail d, String[] headerText) {
        super(context);
        this.context = context;
        this.views = new View[headerText.length];
        this.mHints = d.getHeaderSizeHints();
        String[] headerForms = d.getHeaderForms();

        int[] colors = AndroidUtil.getThemeColorIDs(context, new int[]{R.attr.entity_view_header_background_color, R.attr.entity_view_header_text_color});
        
        if (colors[0] != -1) {
            this.setBackgroundColor(colors[0]);
        }

        for (int i = 0; i < views.length; ++i) {
            if (mHints[i] == null || !mHints[i].startsWith("0")) {
                LayoutParams l = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
                ViewId uniqueId = new ViewId(rowId, i, false);
                views[i] = initView(headerText[i], headerForms[i], uniqueId, null);
                views[i].setId(i);
                if (colors[1] != -1) {
                    TextView tv = (TextView) views[i].findViewById(R.id.component_audio_text_txt);
                    if (tv != null) tv.setTextColor(colors[1]);
                }
                addView(views[i], l);
            }
        }
    }

    /*
     * Creates up a new view in the view with ID uniqueid, based upon
     * the entity's text and form
     */
    private View initView(Object data, String form, ViewId uniqueId, String sortField) {
        View retVal;
        if (FORM_IMAGE.equals(form)) {
            ImageView iv = (ImageView) View.inflate(context, R.layout.entity_item_image, null);
            retVal = iv;
        } else if (FORM_AUDIO.equals(form)) {
            String text = (String) data;
            AudioButton b;
            if (text != null & text.length() > 0) {
                b = new AudioButton(context, text, uniqueId, true);
            } else {
                b = new AudioButton(context, text, uniqueId, false);
            }
            retVal = b;
        } else if (FORM_GRAPH.equals(form) && data instanceof GraphData) {
            View layout = View.inflate(context, R.layout.entity_item_graph, null);
            retVal = layout;
        } else if (FORM_CALLLOUT.equals(form)) {
            View layout = View.inflate(context, R.layout.entity_item_graph, null);
            retVal = layout;
        } else {
            View layout = View.inflate(context, R.layout.component_audio_text, null);
            setupTextAndTTSLayout(layout, (String) data, sortField);
            retVal = layout;
        }
        return retVal;
    }

    public void setSearchTerms(String[] terms) {
        this.searchTerms = terms;
    }


    public void refreshViewsForNewEntity(Entity e, boolean currentlySelected, long rowId) {
        for (int i = 0; i < e.getNumFields(); ++i) {
            Object field = e.getField(i);
            View view = views[i];
            String form = forms[i];

            if (view == null) {
                continue;
            }

            if (FORM_AUDIO.equals(form)) {
                ViewId uniqueId = new ViewId(rowId, i, false);
                setupAudioLayout(view, (String) field, uniqueId);
            } else if (FORM_IMAGE.equals(form)) {
                setupImageLayout(view, (String) field);
            } else if (FORM_GRAPH.equals(form) && field instanceof GraphData) {
                int orientation = getResources().getConfiguration().orientation;
                GraphView g = new GraphView(context, "", false);
                View rendered = null;
                if (renderedGraphsCache.get(i) != null) {
                    rendered = renderedGraphsCache.get(i).get(orientation);
                } else {
                    renderedGraphsCache.put(i, new Hashtable<Integer, View>());
                }
                if (rendered == null) {
                    try {
                        rendered = g.getView(g.getHTML((GraphData) field));
                    } catch (InvalidStateException ise) {
                        rendered = new TextView(context);
                        ((TextView) rendered).setText(ise.getMessage());
                    }
                    renderedGraphsCache.get(i).put(orientation, rendered);
                }
                ((LinearLayout) view).removeAllViews();
                ((LinearLayout) view).addView(rendered, g.getLayoutParams());
                view.setVisibility(VISIBLE);
            } else {
                //text to speech
                setupTextAndTTSLayout(view, (String) field, e.getSortField(i));
            }
        }

        if (currentlySelected) {
            this.setBackgroundResource(R.drawable.grey_bordered_box);
        } else {
            this.setBackgroundDrawable(null);
        }
    }

    /*
     * Updates the AudioButton layout that is passed in, based on the
     * new id and source
     */
    private void setupAudioLayout(View layout, String source, ViewId uniqueId) {
        AudioButton b = (AudioButton) layout;
        if (source != null && source.length() > 0) {
            b.modifyButtonForNewView(uniqueId, source, true);
        } else {
            b.modifyButtonForNewView(uniqueId, source, false);
        }
    }

    /*
     * Updates the text layout that is passed in, based on the new text
     */
    private void setupTextAndTTSLayout(View layout, final String text, String searchField) {
        TextView tv = (TextView) layout.findViewById(R.id.component_audio_text_txt);
        tv.setVisibility(View.VISIBLE);
        tv.setText(highlightSearches(this.getContext(), searchTerms, new SpannableString(text == null ? "" : text), searchField, mFuzzySearchEnabled, mIsAsynchronous));
        ImageButton btn = (ImageButton) layout.findViewById(R.id.component_audio_text_btn_audio);
        btn.setFocusable(false);

        btn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                String textToRead = text;
                tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
            }
        });
        if (tts == null || text == null || text.equals("")) {
            btn.setVisibility(View.INVISIBLE);
            RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
            params.width = 0;
            btn.setLayoutParams(params);
        } else {
            btn.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
            params.width = LayoutParams.WRAP_CONTENT;
            btn.setLayoutParams(params);
        }
    }

    private void addLayoutToRedrawQueue(View layout, String source) {
        imageViewsToRedraw.put(layout, source);
    }

    private void redrawImageLayoutsInQueue() {
        for (View v : imageViewsToRedraw.keySet()) {
            setupImageLayout(v, imageViewsToRedraw.get(v));
        }
        imageViewsToRedraw.clear();
    }


    /**
     * Updates the ImageView layout that is passed in, based on the new id and source
     */
    public void setupImageLayout(View layout, final String source) {
        ImageView iv = (ImageView) layout;
        if (source.equals("")) {
            iv.setImageDrawable(getResources().getDrawable(R.color.transparent));
            return;
        }
        if (onMeasureCalled) {
            int columnWidthInPixels = layout.getLayoutParams().width;
            Bitmap b = MediaUtil.inflateDisplayImage(getContext(), source, columnWidthInPixels, -1);
            if (b == null) {
                // Means the input stream could not be used to derive the bitmap, so showing
                // error-indicating image
                iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
            } else {
                iv.setImageBitmap(b);
            }
        } else {
            // Since case list images are scaled down based on the width of the column they
            // go into, we cannot set up an image layout until onMeasure() has been called
            addLayoutToRedrawQueue(layout, source);
        }
    }

    //TODO: This method now really does two different things and should possibly be different
    //methods.

    /**
     * Based on the search terms provided, highlight the aspects of the spannable provided which
     * match. A background string can be provided which provides the exact data that is being
     * matched.
     */
    public static Spannable highlightSearches(Context context, String[] searchTerms, Spannable raw, String backgroundString, boolean fuzzySearchEnabled, boolean strictMode) {
        if (searchTerms == null) {
            return raw;
        }

        try {
            //TOOD: Only do this if we're in strict mode
            if (strictMode) {
                if (backgroundString == null) {
                    return raw;
                }

                //make sure that we have the same consistency for our background match
                backgroundString = StringUtils.normalize(backgroundString).trim();
            } else {
                //Otherwise we basically want to treat the "Search" string and the display string
                //the same way.
                backgroundString = StringUtils.normalize(raw.toString());
            }

            String normalizedDisplayString = StringUtils.normalize(raw.toString());

            removeSpans(raw);

            Vector<int[]> matches = new Vector<int[]>();

            //Highlight direct substring matches
            for (String searchText : searchTerms) {
                if ("".equals(searchText)) {
                    continue;
                }

                //TODO: Assuming here that our background string exists and
                //isn't null due to the background string check above

                //check to see where we should start displaying this chunk
                int offset = TextUtils.indexOf(normalizedDisplayString, backgroundString);
                if (offset == -1) {
                    //We can't safely highlight any of this, due to this field not actually
                    //containing the same string we're searching by.
                    continue;
                }

                int index = backgroundString.indexOf(searchText);
                //int index = TextUtils.indexOf(normalizedDisplayString, searchText);

                while (index >= 0) {

                    //grab the display offset for actually displaying things
                    int displayIndex = index + offset;

                    raw.setSpan(new BackgroundColorSpan(Color.parseColor(Localization.get("odk_perfect_match_color"))) , displayIndex, displayIndex
                            + searchText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    matches.add(new int[]{index, index + searchText.length()});

                    //index=TextUtils.indexOf(raw, searchText, index + searchText.length());
                    index = backgroundString.indexOf(searchText, index + searchText.length());

                    //we have a non-fuzzy match, so make sure we don't fuck with it
                }
            }

            //now insert the spans for any fuzzy matches (if enabled)
            if (fuzzySearchEnabled && backgroundString != null) {
                backgroundString += " ";

                for (String searchText : searchTerms) {

                    if ("".equals(searchText)) {
                        continue;
                    }


                    int curStart = 0;
                    int curEnd = backgroundString.indexOf(" ", curStart);

                    while (curEnd != -1) {

                        boolean skip = matches.size() != 0;

                        //See whether the fuzzy match overlaps at all with the concrete matches
                        for (int[] textMatch : matches) {
                            if (curStart < textMatch[0] && curEnd <= textMatch[0]) {
                                skip = false;
                            } else if (curStart >= textMatch[1] && curEnd > textMatch[1]) {
                                skip = false;
                            } else {
                                //We're definitely inside of this span, so
                                //don't do any fuzzy matching!
                                skip = true;
                                break;
                            }
                        }

                        if (!skip) {
                            //Walk the string to find words that are fuzzy matched
                            String currentSpan = backgroundString.substring(curStart, curEnd);

                            //First, figure out where we should be matching (if we don't
                            //have anywhere to match, that means there's nothing to display
                            //anyway)
                            int indexInDisplay = normalizedDisplayString.indexOf(currentSpan);
                            int length = (curEnd - curStart);

                            if (indexInDisplay != -1 && StringUtils.fuzzyMatch(currentSpan, searchText).first) {
                                raw.setSpan(new BackgroundColorSpan(Color.parseColor(Localization.get("odk_fuzzy_match_color"))), indexInDisplay,
                                        indexInDisplay + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                        curStart = curEnd + 1;
                        curEnd = backgroundString.indexOf(" ", curStart);
                    }
                }
            }
        } catch (Exception excp) {
            removeSpans(raw);
            Logger.log("search-hl", excp.toString() + " " + ExceptionReportTask.getStackTrace(excp));
        }

        return raw;
    }

    /**
     * Removes all background color spans from the Spannable
     *
     * @param raw Spannable to remove background colors from
     */
    private static void removeSpans(Spannable raw) {
        //Zero out the existing spans
        BackgroundColorSpan[] spans = raw.getSpans(0, raw.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            raw.removeSpan(span);
        }
    }

    /**
     * Determine width of each child view, based on mHints, the suite's size hints.
     * mHints contains a width hint for each child view, each one of
     * - A string like "50%", requesting the field take up 50% of the row
     * - A string like "200", requesting the field take up 200 pixels
     * - Null, not specifying a width for the field
     * This function will parcel out requested widths and divide remaining space among unspecified columns.
     *
     * @param fullSize Width, in pixels, of the containing row.
     * @return Array of integers, each corresponding to a child view,
     * representing the desired width, in pixels, of that view.
     */
    private int[] calculateDetailWidths(int fullSize) {
        // Convert any percentages to pixels. Percentage columns are treated as percentage of the entire screen width.
        int[] widths = new int[mHints.length];
        for (int i = 0; i < mHints.length; i++) {
            if (mHints[i] == null) {
                widths[i] = -1;
            } else if (mHints[i].contains("%")) {
                widths[i] = fullSize * Integer.parseInt(mHints[i].substring(0, mHints[i].indexOf("%"))) / 100;
            } else {
                widths[i] = Integer.parseInt(mHints[i]);
            }
        }

        int claimedSpace = 0;
        int indeterminateColumns = 0;
        for (int width : widths) {
            if (width != -1) {
                claimedSpace += width;
            } else {
                indeterminateColumns++;
            }
        }
        if (fullSize < claimedSpace + indeterminateColumns
                || (fullSize > claimedSpace && indeterminateColumns == 0)) {
            // Either more space has been claimed than the screen has room for,
            // or the full width isn't spoken for and there are no indeterminate columns
            claimedSpace += indeterminateColumns;
            for (int i = 0; i < widths.length; i++) {
                if (widths[i] == -1) {
                    // Assign indeterminate columns a real width.
                    // It's arbitrary and tiny, but this is going to look terrible regardless.
                    widths[i] = 1;
                } else {
                    // Shrink or expand columns proportionally
                    widths[i] = fullSize * widths[i] / claimedSpace;
                }
            }
        } else if (indeterminateColumns > 0) {
            // Divide remaining space equally among the indeterminate columns
            int defaultWidth = (fullSize - claimedSpace) / indeterminateColumns;
            for (int i = 0; i < widths.length; i++) {
                if (widths[i] == -1) {
                    widths[i] = defaultWidth;
                }
            }
        }

        return widths;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // calculate the view and its children's default measurements
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Adjust the children view's widths based on percentage size hints
        int[] widths = calculateDetailWidths(getMeasuredWidth());
        for (int i = 0; i < views.length; i++) {
            if (views[i] != null) {
                LayoutParams params = (LinearLayout.LayoutParams) views[i].getLayoutParams();
                params.width = widths[i];
                views[i].setLayoutParams(params);
            }
        }

        onMeasureCalled = true;
        if (imageViewsToRedraw.size() > 0) {
            redrawImageLayoutsInQueue();
        }

        // Re-calculate the view's measurements based on the percentage adjustments above
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
