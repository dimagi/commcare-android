package org.commcare.android.view;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.models.Entity;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
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
    private Context context;
    private AudioController controller;
    private Hashtable<Integer, Hashtable<Integer, View>> renderedGraphsCache;    // index => { orientation => GraphView }
    private long rowId;
    private static final String FORM_AUDIO = "audio";
    private static final String FORM_IMAGE = "image";
    private static final String FORM_GRAPH = "graph";
    
    private boolean mFuzzySearchEnabled = true;

    /*
     * Constructor for row/column contents
     */
    public EntityView(Context context, Detail d, Entity e, TextToSpeech tts,
            String[] searchTerms, AudioController controller, long rowId, boolean mFuzzySearchEnabled) {
        super(context);
        this.context = context;
        this.searchTerms = searchTerms;
        this.tts = tts;
        this.setWeightSum(1);
        this.controller = controller;
        this.renderedGraphsCache = new Hashtable<Integer, Hashtable<Integer, View>>();
        this.rowId = rowId;
        views = new View[e.getNumFields()];
        forms = d.getTemplateForms();
        float[] weights = calculateDetailWeights(d.getTemplateSizeHints());
        
        for (int i = 0; i < views.length; ++i) {
            if (weights[i] != 0) {
                Object uniqueId = new ViewId(rowId, i, false);
                views[i] = initView(e.getField(i), forms[i], uniqueId, e.getSortField(i));
                views[i].setId(i);
            }
        }
        refreshViewsForNewEntity(e, false, rowId);
        for (int i = 0; i < views.length; i++) {
            LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, weights[i]);
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
        this.setWeightSum(1);
        views = new View[headerText.length];
        float[] lengths = calculateDetailWeights(d.getHeaderSizeHints());
        String[] headerForms = d.getHeaderForms();
        
        for (int i = 0 ; i < views.length ; ++i) {
            if (lengths[i] != 0) {
                LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, lengths[i]);
                ViewId uniqueId = new ViewId(rowId, i, false);
                views[i] = initView(headerText[i], headerForms[i], uniqueId, null);      
                views[i].setId(i);
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
            }
            else if(FORM_IMAGE.equals(form)) {
                setupImageLayout(view, (String) field);
            } 
            else if (FORM_GRAPH.equals(form) && field instanceof GraphData) {
                int orientation = getResources().getConfiguration().orientation;
                GraphView g = new GraphView(context);
                View rendered = null;
                 if (renderedGraphsCache.get(i) != null) {
                     rendered = renderedGraphsCache.get(i).get(orientation);
                 }
                 else {
                     renderedGraphsCache.put(i, new Hashtable<Integer, View>());
                 }
                 if (rendered == null) {
                     rendered = g.renderView((GraphData) field);
                     renderedGraphsCache.get(i).put(orientation, rendered);
                 }
                ((LinearLayout) view).removeAllViews();
                ((LinearLayout) view).addView(rendered, g.getLayoutParams());
                view.setVisibility(VISIBLE);
            }
            else { //text to speech
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
       tv.setText(highlightSearches(text == null ? "" : text, searchField));
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
            iv.setImageDrawable(getResources().getDrawable(R.drawable.white));
        }
    }
    
    private Spannable highlightSearches(String displayString, String backgroundString) {
        
        Spannable raw = new SpannableString(displayString);
        String normalizedDisplayString = StringUtils.normalize(displayString);
        
        if (searchTerms == null) {
            return raw;
        }
        
        //Zero out the existing spans
        BackgroundColorSpan[] spans=raw.getSpans(0,raw.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            raw.removeSpan(span);
        }
        
        Vector<int[]> matches = new Vector<int[]>(); 
        
        
        //Highlight direct substring matches
        for (String searchText : searchTerms) {
            if ("".equals(searchText)) { continue;}
    
            int index = TextUtils.indexOf(normalizedDisplayString, searchText);
            
            while (index >= 0) {
              raw.setSpan(new BackgroundColorSpan(this.getContext().getResources().getColor(R.color.yellow_highlight)), index, index
                  + searchText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
              
              matches.add(new int[] {index, index + searchText.length() } );
              
              index=TextUtils.indexOf(raw, searchText, index + searchText.length());
              
              //we have a non-fuzzy match, so make sure we don't fuck with it
            }
        }

        //now insert the spans for any fuzzy matches (if enabled)
        if(mFuzzySearchEnabled && backgroundString != null) {
            backgroundString = StringUtils.normalize(backgroundString).trim() + " ";

            for (String searchText : searchTerms) {
                
                if ("".equals(searchText)) { continue;}
                
                
                int curStart = 0;
                int curEnd = backgroundString.indexOf(" ", curStart);
                while(curEnd != -1) {
                    
                    boolean skip = matches.size() != 0;
                    
                    //See whether the fuzzy match overlaps at all with the concrete matches
                    for(int[] textMatch : matches) {
                        if(curStart < textMatch[0] && curEnd <= textMatch[0]) {
                            skip = false;
                        } else if(curStart >= textMatch[1] &&  curEnd > textMatch[1]) {
                            skip = false;
                        }
                    }
                    
                    if(!skip) {
                        //Walk the string to find words that are fuzzy matched
                        if(StringUtils.fuzzyMatch(backgroundString.substring(curStart, curEnd), searchText)) {
                            raw.setSpan(new BackgroundColorSpan(this.getContext().getResources().getColor(R.color.green_highlight)), curStart, 
                                    curEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    curStart = curEnd + 1;
                    curEnd = backgroundString.indexOf(" ", curStart);
                }
            }
        }
        
        return raw;
    }
    
    private float[] calculateDetailWeights(int[] hints) {
        float[] weights = new float[hints.length];
        int fullSize = 100;
        int sharedBetween = 0;
        for(int hint : hints) {
            if(hint != -1) {
                fullSize -= hint;
            } else {
                sharedBetween ++;
            }
        }
        
        double average = ((double)fullSize) / (double)sharedBetween;
        
        for(int i = 0; i < hints.length; ++i) {
            int hint = hints[i];
            weights[i] = hint == -1? (float)(average/100.0) :  (float)(((double)hint)/100.0);
        }

        return weights;
    }

}
