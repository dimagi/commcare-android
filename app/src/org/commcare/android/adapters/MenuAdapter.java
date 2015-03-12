/**
 * 
 */
package org.commcare.android.adapters;

import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.view.GridMediaView;
import org.commcare.android.view.HorizontalMediaView;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.odk.collect.android.views.media.AudioButton;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

/**
 * Adapter class to handle both Menu and Entry items
 * @author wspride
 *
 */
public class MenuAdapter implements ListAdapter {
    
    private AndroidSessionWrapper asw;
    private CommCarePlatform mPlatform;
    protected Context context;
    protected Object[] objectData;
    
    private String menuTitle = null;
    
    public MenuAdapter(Context context, CommCarePlatform platform, String menuID){
        
        this.mPlatform = platform;
        this.context = context;
        
        Vector<Object> items = new Vector<Object>();
        
        Hashtable<String, Entry> map = platform.getMenuMap();
        asw = CommCareApplication._().getCurrentSessionWrapper();
        for(Suite s : platform.getInstalledSuites()) {
            for(Menu m : s.getMenus()) {
                if(m.getId().equals(menuID)) {
                    
                    if(menuTitle == null) {
                        //TODO: Do I need args, here?
                        try {
                            menuTitle = m.getName().evaluate();
                        }catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                    
                    for(String command : m.getCommandIds()) {
                        try {
                            XPathExpression mRelevantCondition = m.getRelevantCondition(m.indexOfCommand(command));
                            if(mRelevantCondition != null) {                            
                                EvaluationContext mEC = asw.getEvaluationContext();
                                Object ret = mRelevantCondition.eval(mEC);
                                try {
                                    if(!XPathFuncExpr.toBoolean(ret)) {
                                        continue;
                                    }
                                } catch(XPathTypeMismatchException e) {
                                    Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "relevancy condition for menu item returned non-boolean value : " + ret);
                                    throw new RuntimeException("relevancy condition for menu item returned non-boolean value : " + ret);
                                    
                                }
                            if(!XPathFuncExpr.toBoolean(ret)) { continue;}
                            }
                            
                            Entry e = map.get(command);
                            if(e.getXFormNamespace() == null) {
                                //If this is a "view", not an "entry"
                                //we only want to display it if all of its 
                                //datums are not already present
                                if(asw.getSession().getNeededDatum(e) == null) {
                                    continue;
                                }
                            }
                            
                            items.add(e);
                        } catch(XPathSyntaxException xpse) {
                            String xpathExpression = m.getRelevantConditionRaw(m.indexOfCommand(command));
                            CommCareApplication._().triggerHandledAppExit(context, Localization.get("app.menu.display.cond.bad.xpath", new String[] {xpathExpression, xpse.getMessage()}));
                            objectData = new Object[0];
                            return;
                        } catch(XPathException xpe) {
                            String xpathExpression = m.getRelevantConditionRaw(m.indexOfCommand(command));
                            CommCareApplication._().triggerHandledAppExit(context, Localization.get("app.menu.display.cond.xpath.err", new String[] {xpathExpression, xpe.getMessage()}));
                            objectData = new Object[0];
                            return;
                        }
                    }
                    continue;
                }
                if(menuID.equals(m.getRoot())){
                    //make sure we didn't already add this ID
                    boolean idExists = false;
                    for(Object o : items) {
                        if(o instanceof Menu) {
                            if(((Menu)o).getId().equals(m.getId())){
                                idExists = true;
                                break;
                            }
                        }
                    }
                    if(!idExists) {
                        items.add(m);
                    }
                }
            }
        }
        
        objectData = new Object[items.size()];
        items.copyInto(objectData);
    }
    
    public String getMenuTitle() {
        return menuTitle;
    }
    
    /* (non-Javadoc)
     * @see android.widget.ListAdapter#areAllItemsEnabled()
     */
    public boolean areAllItemsEnabled() {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.ListAdapter#isEnabled(int)
     */
    public boolean isEnabled(int arg0) {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getCount()
     */
    public int getCount() {
        return (objectData.length);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    public Object getItem(int i) {
        return objectData[i];
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    public long getItemId(int i) {

        Object tempItem = objectData[i];
        
        if(tempItem instanceof Menu){
            return ((Menu)tempItem).getId().hashCode();
        }
        else{
            return ((Entry)tempItem).getCommandId().hashCode();
        }
    }


    /*
     * (non-Javadoc)
     * @see android.widget.Adapter#getItemViewType(int)
     */
    public int getItemViewType(int i) {
        return 0;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    public View getView(int i, View v, ViewGroup vg) {
        
        Object mObject = objectData[i];

        // inflate view
        View menuListItem = v;

        if(menuListItem == null) {
            // inflate it and do not attach to parent, or we will get the 'addView not supported' exception
            menuListItem = LayoutInflater.from(context).inflate(R.layout.menu_list_item_modern, vg, false);
        }

        // set up text
        String mQuestionText = textViewHelper(mObject);

        //Final change, remove any numeric context requests. J2ME uses these to
        //help with numeric navigation.
        if(mQuestionText != null) {
            mQuestionText = Localizer.processArguments(mQuestionText, new String[] {""}).trim();
        }

        TextView rowText = (TextView) menuListItem.findViewById(R.id.row_txt);
        rowText.setText(mQuestionText);

        final String audioURI = getAudioURI(mObject);
        String audioFilename = "";
        if(audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager._().DeriveReference(audioURI).getLocalURI();
            } catch (InvalidReferenceException e) {
                Log.e("AVTLayout", "Invalid reference exception");
                e.printStackTrace();
            }
        }

        File audioFile = new File(audioFilename);

        // First set up the audio button
        AudioButton mAudioButton = (AudioButton) menuListItem.findViewById(R.id.row_soundicon);
        if (audioFilename != "" && audioFile.exists()) {
            // An audio file is specified
//            mAudioButton = new AudioButton(getContext(), audioURI, true);
            // Set not focusable so that list onclick will work
            mAudioButton.setFocusable(false);
            mAudioButton.setFocusableInTouchMode(false);

            mAudioButton.resetButton(audioURI, true);
        } else {
            if(mAudioButton != null) {
                mAudioButton.resetButton(audioURI, false);
                ((LinearLayout) mAudioButton.getParent()).removeView(mAudioButton);
            }
        }

        // set up the image, if available
        ImageView mImageView = (ImageView) menuListItem.findViewById(R.id.row_img);

        final String imageURI = getImageURI(mObject);
        Bitmap b = ViewUtil.inflateDisplayImage(context, imageURI);

        if(b != null) {
            mImageView.setImageBitmap(b);
//            mImageView.setAdjustViewBounds(true);
//            mImageView.setId(23422634);
//            imageParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
//            audioParams.addRule(CENTER_VERTICAL);
//            addView(mImageView, imageParams);
        } else {
            if(mImageView != null) {
                ((LinearLayout) mImageView.getParent()).removeView(mImageView);
            }
        }


//            if(navStyle != NAVIGATION_NONE) {
//                audioParams.addRule(RelativeLayout.LEFT_OF, 2345345);
//            } else {
//                audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//            }
//            audioParams.addRule(CENTER_VERTICAL);
//            addView(mAudioButton, audioParams);

        //        HorizontalMediaView emv = (HorizontalMediaView)v;
        //        String mQuestionText = textViewHelper(mObject);
        //        if(emv == null) {
        //            emv = new HorizontalMediaView(context);
        //        }
        //
        //        int iconChoice = HorizontalMediaView.NAVIGATION_NEXT;
        //
        //        //figure out some icons
        //        if(mObject instanceof Entry) {
        //            SessionDatum datum = asw.getSession().getNeededDatum((Entry)mObject);
        //            if(datum == null || datum.getNodeset() == null) {
        //                iconChoice = HorizontalMediaView.NAVIGATION_JUMP;
        //            }
        //        }
        //        if(!DeveloperPreferences.isNewNavEnabled()) {
        //            iconChoice = HorizontalMediaView.NAVIGATION_NONE;
        //        }
        //
        //        //Final change, remove any numeric context requests. J2ME uses these to
        //        //help with numeric navigation.
        //        if(mQuestionText != null) {
        //            mQuestionText = Localizer.processArguments(mQuestionText, new String[] {""}).trim();
        //        }
        //        emv.setAVT(mQuestionText, getAudioURI(mObject), getImageURI(mObject), iconChoice);
        //        return emv;
        return menuListItem;
    }
    
    /*
     * Helpers to make the getView call Entry/Menu agnostic
     */
    
    public String getAudioURI(Object e){
        if(e instanceof Menu){
            return ((Menu)e).getAudioURI();
        }
        return ((Entry)e).getAudioURI();
    }
    
    public String getImageURI(Object e){
        if(e instanceof Menu){
            return ((Menu)e).getImageURI();
        }
        return ((Entry)e).getImageURI();
    }
    
    /*
     * Helper to build the TextView for the HorizontalMediaView constructor
     */
    public String textViewHelper(Object e){
        String displayText;
        if(e instanceof Menu){
            displayText = ((Menu)e).getName().evaluate();
        }
        else{
            displayText = ((Entry)e).getText().evaluate();
        }
        //mQuestionText.setTypeface(null, Typeface.NORMAL);
        //mQuestionText.setPadding(0, 0, 0, 7);
        //mQuestionText.setId((int)Math.random()*100000000); // assign random id
        //mQuestionText.setHorizontallyScrolling(false);
        return displayText;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getViewTypeCount()
     */
    public int getViewTypeCount() {
        return 1;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#hasStableIds()
     */
    public boolean hasStableIds() {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#isEmpty()
     */
    public boolean isEmpty() {
        return false;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
     */
    public void registerDataSetObserver(DataSetObserver arg0) {

    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
     */
    public void unregisterDataSetObserver(DataSetObserver arg0) {

    }
}
