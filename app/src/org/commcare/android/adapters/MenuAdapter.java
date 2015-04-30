/**
 * 
 */
package org.commcare.android.adapters;

import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.view.HorizontalMediaView;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.MenuDisplayable;
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
    protected MenuDisplayable[] displayableData;
    
    private String menuTitle = null;
    
    public MenuAdapter(Context context, CommCarePlatform platform, String menuID){
        
        this.mPlatform = platform;
        this.context = context;
        
        Vector<MenuDisplayable> items = new Vector<MenuDisplayable>();
        
        Hashtable<String, Entry> map = platform.getMenuMap();
        asw = CommCareApplication._().getCurrentSessionWrapper();
        EvaluationContext ec = null;
        for(Suite s : platform.getInstalledSuites()) {
            for(Menu m : s.getMenus()) {
                String xpathExpression = "";
                try {
                    XPathExpression relevance = m.getMenuRelevance();
                    if (m.getMenuRelevance() != null) {
                        xpathExpression = m.getMenuRelevanceRaw();
                        ec = asw.getEvaluationContext(m.getId());
                        if (XPathFuncExpr.toBoolean(relevance.eval(ec)).booleanValue() == false) {
                            continue;
                        }
                    }
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
                            xpathExpression = "";
                            XPathExpression mRelevantCondition = m.getCommandRelevance(m.indexOfCommand(command));
                            if(mRelevantCondition != null) {                            
                                xpathExpression = m.getCommandRelevanceRaw(m.indexOfCommand(command));
                                ec = asw.getEvaluationContext();
                                Object ret = mRelevantCondition.eval(ec);
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
                } catch(XPathSyntaxException xpse) {
                    CommCareApplication._().triggerHandledAppExit(context, Localization.get("app.menu.display.cond.bad.xpath", new String[] {xpathExpression, xpse.getMessage()}));
                    displayableData = new MenuDisplayable[0];
                    return;
                } catch(XPathException xpe) {
                    CommCareApplication._().triggerHandledAppExit(context, Localization.get("app.menu.display.cond.xpath.err", new String[] {xpathExpression, xpe.getMessage()}));
                    displayableData = new MenuDisplayable[0];
                    return;
                }
            }
        }
        
        displayableData = new MenuDisplayable[items.size()];
        items.copyInto(displayableData);
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
        return (displayableData.length);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    public Object getItem(int i) {
        return displayableData[i];
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    public long getItemId(int i) {

        Object tempItem = displayableData[i];
        
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

    private enum NavIconState {
        NONE
       ,NEXT
       ,JUMP
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    public View getView(int i, View v, ViewGroup vg) {
        
        MenuDisplayable mObject = displayableData[i];

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

        // set up audio
        final String audioURI = mObject.getAudioURI();
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
        ImageView mIconView = (ImageView) menuListItem.findViewById(R.id.row_img);

        NavIconState iconChoice = NavIconState.NEXT;

        //figure out some icons
        if(mObject instanceof Entry) {
            SessionDatum datum = asw.getSession().getNeededDatum((Entry)mObject);
            if(datum == null || datum.getNodeset() == null) {
                iconChoice = NavIconState.JUMP;
            }
        }
        if(!DeveloperPreferences.isNewNavEnabled()) {
            iconChoice = NavIconState.NONE;
        }

        if(mIconView != null) {
            switch (iconChoice) {
                case NEXT:
                    mIconView.setImageResource(R.drawable.avatar_module);
                    break;
                case JUMP:
                    mIconView.setImageResource(R.drawable.avatar_form);
                    break;
                case NONE:
                    // remove it because we won't need it anymore
                    if(mIconView != null) {
                        ((LinearLayout) mIconView.getParent()).removeView(mIconView);
                    }
                    break;
            }
        } else {
            if(mIconView != null) {
                ((LinearLayout) mIconView.getParent()).removeView(mIconView);
            }
        }

        String imageURI = mObject.getImageURI();

        Bitmap image = ViewUtil.inflateDisplayImage(context, imageURI);
        if(image != null && mIconView != null) {
            mIconView.setImageBitmap(image);
            mIconView.setAdjustViewBounds(true);
        }

        return menuListItem;
    }

    /*
     * Helper to build the TextView for the HorizontalMediaView constructor
     */
    public String textViewHelper(MenuDisplayable e){
        return e.getDisplayText();
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
