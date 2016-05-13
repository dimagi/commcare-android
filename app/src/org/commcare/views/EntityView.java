package org.commcare.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.dalvik.R;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphException;
import org.commcare.graph.view.GraphView;
import org.commcare.models.AsyncEntity;
import org.commcare.models.Entity;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.media.AudioButton;
import org.commcare.views.media.ViewId;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class EntityView extends LinearLayout {
    private final ArrayList<View> views;
    private ArrayList<String> forms;
    private String[] searchTerms;
    private final ArrayList<String> mHints;

    // index => { orientation => GraphView }
    private Hashtable<Long, Hashtable<Integer, View>> renderedGraphsCache;
    private long rowId;
    public static final String FORM_AUDIO = "audio";
    public static final String FORM_IMAGE = "image";
    public static final String FORM_GRAPH = "graph";
    public static final String FORM_CALLLOUT = "callout";

    // Flag indicating if onMeasure has already been called for the first time on this view
    private boolean onMeasureCalled = false;
    // Maintains a queue of image layouts that need to be re-drawn once onMeasure has been called
    private final HashMap<View, String> imageViewsToRedraw = new HashMap<>();

    private boolean mFuzzySearchEnabled = true;
    private boolean mIsAsynchronous = false;
    private String extraData = null;

    /**
     * Creates row entry for entity
     */
    private EntityView(Context context, Detail d, Entity e,
                       String[] searchTerms, long rowId,
                       boolean mFuzzySearchEnabled, String extraData) {
        super(context);

        //this is bad :(
        mIsAsynchronous = e instanceof AsyncEntity;
        this.searchTerms = searchTerms;
        this.renderedGraphsCache = new Hashtable<>();
        this.rowId = rowId;
        this.views = new ArrayList<>(e.getNumFields());
        this.forms = new ArrayList<>(Arrays.asList(d.getTemplateForms()));
        this.mHints = new ArrayList<>(Arrays.asList(d.getTemplateSizeHints()));

        for (int col = 0; col < e.getNumFields(); ++col) {
            Object field = e.getField(col);
            String sortField = e.getSortField(col);
            views.add(addCell(col, field, forms.get(col), mHints.get(col), sortField, -1, true));
        }

        addExtraData(d.getCallout().getResponseDetail(), extraData);

        this.mFuzzySearchEnabled = mFuzzySearchEnabled;
    }

    /**
     * Creates row entry for column headers
     */
    private EntityView(Context context, Detail d, String[] columnTitles,
                       boolean hasCalloutResponseData) {
        super(context);

        DetailField calloutResponseDetailField = null;
        if (hasCalloutResponseData && d.getCallout() != null) {
            calloutResponseDetailField = d.getCallout().getResponseDetail();
            columnTitles = addColumnTitleForCalloutData(columnTitles, calloutResponseDetailField);
        }

        int columnCount = columnTitles.length;
        this.views = new ArrayList<>(columnCount);
        this.mHints = new ArrayList<>(columnCount);
        String[] headerForms = new String[columnCount];

        int i = 0;
        for (DetailField field : d.getFields()) {
            mHints.add(field.getHeaderWidthHint());
            headerForms[i] = field.getHeaderForm();
            i++;
        }

        if (calloutResponseDetailField != null) {
            mHints.add(calloutResponseDetailField.getHeaderWidthHint());
            headerForms[columnCount-1] = calloutResponseDetailField.getHeaderForm();
        }

        int[] colors = AndroidUtil.getThemeColorIDs(getContext(),
                new int[]{R.attr.entity_view_header_background_color,
                        R.attr.entity_view_header_text_color});
        
        if (colors[0] != -1) {
            this.setBackgroundColor(colors[0]);
        }

        for (int col = 0; col < columnCount; ++col) {
            views.add(addCell(col, columnTitles[col], headerForms[col],
                    mHints.get(col), null, colors[1], false));
        }
    }

    private static String[] addColumnTitleForCalloutData(String[] columnTitles,
                                                         DetailField calloutResponseDetailField) {
        String[] headerTextWithCalloutResponse =
                new String[columnTitles.length + 1];
        System.arraycopy(columnTitles, 0,
                headerTextWithCalloutResponse, 0, columnTitles.length);
        columnTitles = headerTextWithCalloutResponse;
        columnTitles[columnTitles.length - 1] =
                calloutResponseDetailField.getHeader().evaluate();
    }

    public static EntityView buildEntryEntityView(Context context, Detail detail,
                                                  Entity entity,
                                                  String[] searchTerms,
                                                  long rowId, boolean isFuzzySearchEnabled,
                                                  String extraData) {
        return new EntityView(context, detail, entity,
                searchTerms, rowId, isFuzzySearchEnabled, extraData);
    }

    public static EntityView buildHeadersEntityView(Context context,
                                                    Detail detail,
                                                    String[] headerText,
                                                    boolean hasCalloutResponseData) {
        return new EntityView(context, detail, headerText, hasCalloutResponseData);
    }

    private View addCell(int columnIndex, Object data, String form,
                         String hint, String sortField,
                         int textColor, boolean shouldRefresh) {
        View view = null;
        if (isNonZeroWidth(hint)) {
            ViewId uniqueId = new ViewId(rowId, columnIndex, false);
            view = initView(data, form, uniqueId, sortField);
            view.setId(AndroidUtil.generateViewId());
            if (textColor != -1) {
                TextView tv = (TextView) view.findViewById(R.id.entity_view_text);
                if (tv != null) tv.setTextColor(textColor);
            }

            if (shouldRefresh) {
                refreshViewForNewEntity(view, data, form, sortField, columnIndex, rowId);
            }

            LayoutParams l = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
            addView(view, l);
        }
        return view;
    }

    /**
     * Creates up a new view in the view with ID uniqueid, based upon
     * the entity's text and form
     */
    private View initView(Object data, String form, ViewId uniqueId, String sortField) {
        View retVal;
        if (FORM_IMAGE.equals(form)) {
            retVal = View.inflate(getContext(), R.layout.entity_item_image, null);
        } else if (FORM_AUDIO.equals(form)) {
            String text = (String) data;
            boolean isVisible = (text != null && text.length() > 0);
            retVal = new AudioButton(getContext(), text, uniqueId, isVisible);
        } else if (FORM_GRAPH.equals(form) && data instanceof GraphData) {
            retVal = View.inflate(getContext(), R.layout.entity_item_graph, null);
        } else if (FORM_CALLLOUT.equals(form)) {
            retVal = View.inflate(getContext(), R.layout.entity_item_graph, null);
        } else {
            View layout = View.inflate(getContext(), R.layout.component_text, null);
            setupText(layout, (String) data, sortField);
            retVal = layout;
        }
        return retVal;
    }

    public void setSearchTerms(String[] terms) {
        this.searchTerms = terms;
    }

    public void setExtraData(DetailField responseDetail, String newExtraData) {
        if (extraData != null) {
            removeExtraData();
        }
        addExtraData(responseDetail, newExtraData);
    }

    private void addExtraData(DetailField responseDetail, String newExtraData) {
        String hint = responseDetail.getTemplateWidthHint();
        if (isNonZeroWidth(hint) && newExtraData != null && !"".equals(newExtraData)) {
            extraData = newExtraData;
            views.add(addCell(views.size(), newExtraData, "", "", "", -1, false));
            mHints.add(hint);
            forms.add(responseDetail.getTemplateForm());
        }
    }

    private static boolean isNonZeroWidth(String hintText) {
        return hintText == null || !hintText.startsWith("0");
    }

    private void removeExtraData() {
        if (extraData != null) {
            extraData = null;
            removeView(views.get(views.size() - 1));
            views.remove(views.size() - 1);
            mHints.remove(mHints.size() - 1);
            forms.remove(forms.size() - 1);
        }
    }

    public void refreshViewsForNewEntity(Entity e, boolean currentlySelected, long rowId) {
        for (int i = 0; i < e.getNumFields(); ++i) {
            Object field = e.getField(i);
            View view = views.get(i);

            if (view != null) {
                refreshViewForNewEntity(view, field, forms.get(i), e.getSortField(i), i, rowId);
            }
        }

        View extraDataView = views.get(views.size() - 1);
        if (extraData != null && extraDataView != null) {
            refreshViewForNewEntity(extraDataView, extraData, forms.get(forms.size() - 1), "", views.size() - 1, rowId);
        }

        if (currentlySelected) {
            this.setBackgroundResource(R.drawable.grey_bordered_box);
        } else {
            this.setBackgroundDrawable(null);
        }
    }

    private void refreshViewForNewEntity(View view, Object field,
                                         String form, String sortField,
                                         int columnIndex, long rowId) {
        if (FORM_AUDIO.equals(form)) {
            ViewId uniqueId = new ViewId(rowId, columnIndex, false);
            setupAudioLayout(view, (String) field, uniqueId);
        } else if (FORM_IMAGE.equals(form)) {
            setupImageLayout(view, (String) field);
        } else if (FORM_GRAPH.equals(form) && field instanceof GraphData) {
            int orientation = getResources().getConfiguration().orientation;
            GraphView g = new GraphView(getContext(), "", false);
            View rendered = null;
            if (renderedGraphsCache.get(rowId) != null) {
                rendered = renderedGraphsCache.get(rowId).get(orientation);
            } else {
                renderedGraphsCache.put(rowId, new Hashtable<Integer, View>());
            }
            if (rendered == null) {
                try {
                    rendered = g.getView(g.getHTML((GraphData) field));
                } catch (GraphException ex) {
                    rendered = new TextView(getContext());
                    ((TextView) rendered).setText(ex.getMessage());
                }
                renderedGraphsCache.get(rowId).put(orientation, rendered);
            }
            ((LinearLayout) view).removeAllViews();
            ((LinearLayout) view).addView(rendered, GraphView.getLayoutParams());
            view.setVisibility(VISIBLE);
        } else {
            setupText(view, (String) field, sortField);
        }
    }

    /**
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

    /**
     * Updates the text layout that is passed in, based on the new text
     */
    private void setupText(View layout, final String text, String searchField) {
        TextView tv = (TextView) layout.findViewById(R.id.entity_view_text);
        tv.setVisibility(View.VISIBLE);
        Spannable rawText = new SpannableString(text == null ? "" : text);
        tv.setText(highlightSearches(searchTerms, rawText, searchField, mFuzzySearchEnabled, mIsAsynchronous));
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
    private void setupImageLayout(View layout, final String source) {
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
    public static Spannable highlightSearches(String[] searchTerms, Spannable raw,
                                              String backgroundString, boolean fuzzySearchEnabled,
                                              boolean strictMode) {
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

            Vector<int[]> matches = new Vector<>();

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
            Logger.log("search-hl", excp.toString() + " " + ForceCloseLogger.getStackTrace(excp));
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
        int[] widths = new int[mHints.size()];
        int hintIndex = 0;
        for (String hint : mHints) {
            if (hint == null) {
                widths[hintIndex] = -1;
            } else if (hint.contains("%")) {
                widths[hintIndex] = fullSize * Integer.parseInt(hint.substring(0, hint.indexOf("%"))) / 100;
            } else {
                widths[hintIndex] = Integer.parseInt(hint);
            }
            hintIndex++;
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
        int i = 0;
        for (View view : views) {
            if (view != null) {
                LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
                params.width = widths[i];
                view.setLayoutParams(params);
            }
            i++;
        }

        onMeasureCalled = true;
        if (imageViewsToRedraw.size() > 0) {
            redrawImageLayoutsInQueue();
        }

        // Re-calculate the view's measurements based on the percentage adjustments above
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
