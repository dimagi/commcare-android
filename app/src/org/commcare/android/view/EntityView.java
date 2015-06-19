package org.commcare.android.view;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.models.AsyncEntity;
import org.commcare.android.models.Entity;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.AudioController;
import org.odk.collect.android.views.media.ViewId;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

/**
 * @author ctsims
 *
 */
public class EntityView extends LinearLayout {
    
    private View[] views;
    private String[] forms;
    private TextToSpeech tts; 
    private String[] searchTerms;
    private String[] mHints;
    private Context context;
    private AudioController controller;
    private Hashtable<Integer, Hashtable<Integer, View>> renderedGraphsCache;    // index => { orientation => GraphView }
    private long rowId;
    public static final String FORM_AUDIO = "audio";
    public static final String FORM_IMAGE = "image";
    public static final String FORM_GRAPH = "graph";
    public static final String FORM_CALLLOUT = "callout";
    
    private boolean mFuzzySearchEnabled = true;
    private boolean mIsAsynchronous = false;

    /*
     * Constructor for row/column contents
     */
    public EntityView(Context context, Detail d, Entity e, TextToSpeech tts,
            String[] searchTerms, AudioController controller, long rowId, boolean mFuzzySearchEnabled) {
        super(context);
        this.context = context;
        //this is bad :(
        mIsAsynchronous = e instanceof AsyncEntity;
        this.searchTerms = searchTerms;
        this.tts = tts;
        this.controller = controller;
        this.renderedGraphsCache = new Hashtable<Integer, Hashtable<Integer, View>>();
        this.rowId = rowId;
        this.views = new View[e.getNumFields()];
        this.forms = d.getTemplateForms();
        this.mHints = d.getTemplateSizeHints();
        
        for (int i = 0; i < views.length; ++i) {
            if (mHints[i] == null || !mHints[i].startsWith("0")) {
                Object uniqueId = new ViewId(rowId, i, false);
                views[i] = initView(e.getField(i), forms[i], uniqueId, e.getSortField(i));
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
    public EntityView(Context context, Detail d, String[] headerText, Integer textColor) {
        super(context);
        this.context = context;
        this.views = new View[headerText.length];
        this.mHints = d.getHeaderSizeHints();
        String[] headerForms = d.getHeaderForms();
        
        for (int i = 0 ; i < views.length ; ++i) {
            if (mHints[i] == null || !mHints[i].startsWith("0")) {
                LayoutParams l = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
                ViewId uniqueId = new ViewId(rowId, i, false);
                views[i] = initView(headerText[i], headerForms[i], uniqueId, null);      
                views[i].setId(i);
                if(textColor != null) {
                    TextView tv = (TextView) views[i].findViewById(R.id.component_audio_text_txt);
                    if(tv != null) tv.setTextColor(textColor);
                }
                addView(views[i], l);
            }
        }
    }
    
    /*
     * Creates up a new view in the view with ID uniqueid, based upon
     * the entity's text and form
     */
    private View initView(Object data, String form, Object uniqueId, String sortField) {
        View retVal;
        if (FORM_IMAGE.equals(form)) {
            ImageView iv = (ImageView)View.inflate(context, R.layout.entity_item_image, null);
            retVal = iv;
        } 
        else if (FORM_AUDIO.equals(form)) {
            String text = (String) data;
            AudioButton b;
            if (text != null & text.length() > 0) {
                b = new AudioButton(context, text, uniqueId, controller, true);
            }
            else {
                b = new AudioButton(context, text, uniqueId, controller, false);
            }
            retVal = b;
        } 
        else if (FORM_GRAPH.equals(form) && data instanceof GraphData) {
            View layout = View.inflate(context, R.layout.entity_item_graph, null);
            retVal = layout;
        }
        else if (FORM_CALLLOUT.equals(form)) {
            View layout = View.inflate(context, R.layout.entity_item_graph, null);
            retVal = layout;
        }
        else {
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
        for (int i = 0; i < e.getNumFields() ; ++i) {
            Object field = e.getField(i);
            View view = views[i];
            String form = forms[i];
            
            if (view == null) { continue; }
            
            if (FORM_AUDIO.equals(form)) {
                ViewId uniqueId = new ViewId(rowId, i, false);
                setupAudioLayout(view, (String) field, uniqueId);
            } else if(FORM_IMAGE.equals(form)) {
                setupImageLayout(view, (String) field);
            } else if (FORM_GRAPH.equals(form) && field instanceof GraphData) {
                int orientation = getResources().getConfiguration().orientation;
                GraphView g = new GraphView(context, "");
                View rendered = null;
                 if (renderedGraphsCache.get(i) != null) {
                     rendered = renderedGraphsCache.get(i).get(orientation);
                 }
                 else {
                     renderedGraphsCache.put(i, new Hashtable<Integer, View>());
                 }
                 if (rendered == null) {
                     try {
                         rendered = g.getView((GraphData) field);
                     } catch (InvalidStateException ise) {
                         rendered = new TextView(context);
                         ((TextView)rendered).setText(ise.getMessage());
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
        AudioButton b = (AudioButton)layout;
        if (source != null && source.length() > 0) {
            b.modifyButtonForNewView(uniqueId, source, true);
        }
        else {
            b.modifyButtonForNewView(uniqueId, source, false);
        }
    }

    /*
     * Updates the text layout that is passed in, based on the new text
     */
    private void setupTextAndTTSLayout(View layout, final String text, String searchField) {
        TextView tv = (TextView)layout.findViewById(R.id.component_audio_text_txt);
        tv.setVisibility(View.VISIBLE);
        tv.setText(highlightSearches(this.getContext(), searchTerms, new SpannableString(text == null ? "" : text), searchField, mFuzzySearchEnabled, mIsAsynchronous));
        ImageButton btn = (ImageButton)layout.findViewById(R.id.component_audio_text_btn_audio);
        btn.setFocusable(false);

        btn.setOnClickListener(new OnClickListener(){

            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
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
    
    
     /*
     * Updates the ImageView layout that is passed in, based on the  
     * new id and source
     */
    public void setupImageLayout(View layout, final String source) {
        ImageView iv = (ImageView) layout;
        Bitmap b;
        if (!source.equals("")) {
            try {
                b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(source).getStream());
                if (b == null) {
                    //Input stream could not be used to derive bitmap, so showing error-indicating image
                    iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
                }
                else {
                    iv.setImageBitmap(b);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                //Error loading image
                iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
            } catch (InvalidReferenceException ex) {
                ex.printStackTrace();
                //No image
                iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
            }
        }
        else {
            iv.setImageDrawable(getResources().getDrawable(R.color.white));
        }
    }
    
    //TODO: This method now really does two different things and should possibly be different
    //methods.
    /**
     * Based on the search terms provided, highlight the aspects of the spannable provided which
     * match. A background string can be provided which provides the exact data that is being
     * matched. 
     * 
     * @param context
     * @param searchTerms
     * @param raw
     * @param backgroundString
     * @param fuzzySearchEnabled
     * @param strictMode
     * @return
     */
    public static Spannable highlightSearches(Context context, String[] searchTerms, Spannable raw, String backgroundString, boolean fuzzySearchEnabled, boolean strictMode) {
        if (searchTerms == null) {
            return raw;
        }

        try {
            //TOOD: Only do this if we're in strict mode
            if(strictMode) {
                if(backgroundString == null) {
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

                    raw.setSpan(new BackgroundColorSpan(context.getResources().getColor(R.color.yellow)), displayIndex, displayIndex
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
                                raw.setSpan(new BackgroundColorSpan(context.getResources().getColor(R.color.green)), indexInDisplay,
                                        indexInDisplay + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                        curStart = curEnd + 1;
                        curEnd = backgroundString.indexOf(" ", curStart);
                    }
                }
            }
        } catch (Exception excp){
            removeSpans(raw);
            Logger.log("search-hl", excp.toString() + " " + ExceptionReportTask.getStackTrace(excp));
        }

        return raw;
    }

    /**
     * Removes all background color spans from the Spannable
     * @param raw Spannable to remove background colors from
     */
    private static void removeSpans(Spannable raw) {
        //Zero out the existing spans
        BackgroundColorSpan[] spans=raw.getSpans(0,raw.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            raw.removeSpan(span);
        }
    }

    private int[] calculateDetailWidths(int fullSize) {
        // Convert any percentages to pixels
        int[] hints = new int[mHints.length];
        for (int i = 0; i < mHints.length; i++) {
            if (mHints[i] == null) {
                hints[i] = -1;
            } else if (mHints[i].contains("%")) {
                hints[i] = fullSize * Integer.parseInt(mHints[i].substring(0, mHints[i].indexOf("%"))) / 100;
            }
            else {
                hints[i] = Integer.parseInt(mHints[i]);
            }
        }

        // Determine how wide to make columns without a specified width
        int[] widths = new int[hints.length];
        int sharedBetween = 0;
        for(int hint : hints) {
            if(hint != -1) {
                fullSize -= hint;
            } else {
                sharedBetween++;
            }
        }
        
        // Set column widths
        int defaultWidth = sharedBetween == 0 ? 0 : fullSize / sharedBetween;
        for(int i = 0; i < hints.length; ++i) {
            widths[i] = hints[i] == -1 ? defaultWidth : hints[i];
        }
        
        return widths;
    }

    /*
     * (non-Javadoc)
     * @see android.widget.LinearLayout#onMeasure(int, int)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        int[] widths = calculateDetailWidths(getMeasuredWidth());
        for (int i = 0; i < views.length; i++) {
            if (views[i] != null) {
                LayoutParams params = (LinearLayout.LayoutParams) views[i].getLayoutParams();
                params.width = widths[i];
                views[i].setLayoutParams(params);
            }
        }
    }
}
