package org.commcare.dalvik.activities;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.components.EntityDetailComponent;
import org.commcare.dalvik.components.EntitySelectComponent;
import org.commcare.dalvik.components.EntitySelectComponent.EntitySelectListener;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Entry;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 
 * TODO: Lots of locking and state-based cleanup
 * 
 * @author ctsims
 *
 */
public class EntitySelectActivity extends CommCareActivity implements EntitySelectListener, TextToSpeech.OnInitListener  {
    private AndroidSessionWrapper asw;
    
    public static final String EXTRA_SELECTED_POSITION = "selected_position";
    
    private static final int MENU_SORT = Menu.FIRST;
    private static final int MENU_MAP = Menu.FIRST + 1;
    private static final int MENU_ACTION = Menu.FIRST + 2;
    
    private boolean inAwesomeMode = false;
    FrameLayout secondFrame;
    private EntityDetailComponent mDetailComponent;
    private EntitySelectComponent mSelectComponent = new EntitySelectComponent(this);
    
    private int mSelectedPosition = -1;
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mSelectComponent.onCreate(savedInstanceState);
        
        EntitySelectActivity oldActivity = (EntitySelectActivity)this.getDestroyedActivityState();
        
        if(savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt(EXTRA_SELECTED_POSITION, -1);
        }
        
        try {
            asw = CommCareApplication._().getCurrentSessionWrapper();
        } catch(SessionUnavailableException sue){
            //The user isn't logged in! bounce this back to where we came from
            this.setResult(Activity.RESULT_CANCELED);
            this.finish();
            return;
        }
        
        mSelectComponent.initializeSession(asw);
        
        ViewGroup selectComponentRoot = null;
        
        if(this.getString(R.string.panes).equals("two") && !mSelectComponent.getNoDetailMode()) {
            //See if we're on a big 'ol screen. We can always display this with the awesome UI.
            setContentView(R.layout.screen_compound_select);
            
            // define root for select component to inflate in
            selectComponentRoot = (ViewGroup)findViewById(R.id.screen_compound_select_first_pane);
            
            inAwesomeMode = true;
            
            secondFrame = (FrameLayout)findViewById(R.id.screen_compound_select_second_pane);
            
            TextView message = (TextView)findViewById(R.id.screen_compound_select_prompt);
            message.setText(Localization.get("select.placeholder.message", new String[] {Localization.get("cchq.case")}));
        }
        
        mSelectComponent.initializeViews(this, selectComponentRoot);

        if(oldActivity != null) {
            if (mSelectComponent.notifyOldComponent(oldActivity.mSelectComponent)) {
                // See if we changed orientation and had an item selected before
                if(inAwesomeMode && mSelectedPosition >= 0) {
                    mSelectComponent.selectItem(mSelectedPosition);
                }
            }
        }
        //cts: disabling for non-demo purposes
        //tts = new TextToSpeech(this, this);
    }
    
    @Override
    public boolean onAdapterLoaded() {
        
        //In landscape we want to select something now. Either the top item, or the most recently selected one
        if(inAwesomeMode) {
            if (mSelectedPosition >= 0) {
                mSelectComponent.selectItem(mSelectedPosition);
                return true;
            }
        }
        
        return false;
        
    }
    
    @Override
    public void onError(Exception e) {
        displayException(e);
    }
    
    @Override
    public boolean onSelect(TreeReference selection, int position) {

        mSelectedPosition = position;
        
        if(inAwesomeMode) {
            displayReferenceAwesome(selection, position);
            return true;
        }

        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#isTopNavEnabled()
     */
    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#getActivityTitle()
     */
    @Override
    public String getActivityTitle() {
        //Skipping this until it's a more general pattern
        
//        String title = Localization.get("select.list.title");
//        
//        try {
//            Detail detail = session.getDetail(selectDatum.getShortDetail());
//            title = detail.getTitle().evaluate();
//        } catch(Exception e) {
//            
//        }
//        
//        return title;
        return null;
    }

    boolean resuming = false;
    boolean startOther = false;
    
    public void onResume() {
        super.onResume();
        //Don't go through making the whole thing if we're finishing anyway.
        if(this.isFinishing() || startOther) {return;}
        
        mSelectComponent.onResume(!resuming);
    }
    
    @Override
    public boolean onResumeEntity(TreeReference entity, int position) {
        if (inAwesomeMode) {
            if (position >= 0) {
                displayReferenceAwesome(entity, position);
            }
            return true;
        }
        return false;
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Orientation change, save selected position
        if (inAwesomeMode && mSelectedPosition >= 0) {
            outState.putInt(EXTRA_SELECTED_POSITION, mSelectedPosition);
        }
        
        super.onSaveInstanceState(outState);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (!mSelectComponent.onActivityResult(requestCode, resultCode, intent)) {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }
    
    @Override
    public void onConfirmSelectResult() {
        resuming = true;
    }
    
    @Override
    public boolean onMapSelectResult(TreeReference r, int position) {
        if (inAwesomeMode) {
            displayReferenceAwesome(r, position);
            return true;
        }
        return false;
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_SORT, MENU_SORT, Localization.get("select.menu.sort")).setIcon(
                android.R.drawable.ic_menu_sort_alphabetically);
        if(mSelectComponent.getIsMappingEnabled()) {
            menu.add(0, MENU_MAP, MENU_MAP, Localization.get("select.menu.map")).setIcon(
                    android.R.drawable.ic_menu_mapmode);
        }
        Action action = mSelectComponent.getShortSelectCustomAction();
        if(action != null) {
            ViewUtil.addDisplayToMenu(this, menu, MENU_ACTION, action.getDisplay());
        }

        return true;
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        
        //only display the sort menu if we're going to be able to sort
        //(IE: not until the items have loaded)
        menu.findItem(MENU_SORT).setEnabled(mSelectComponent.getIsAdapterInitialized());
        
        return super.onPrepareOptionsMenu(menu);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SORT:
                mSelectComponent.createSortMenu();
                return true;
            case MENU_MAP:
                mSelectComponent.startMapSelectActivity();
                return true;
            case MENU_ACTION:
                mSelectComponent.triggerDetailAction();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onDestroy()
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mSelectComponent.onDestroy();
    }
    
    /*
     * (non-Javadoc)
     * @see android.speech.tts.TextToSpeech.OnInitListener#onInit(int)
     */
    @Override
    public void onInit(int status) {
 
        if (status == TextToSpeech.SUCCESS) {
            //using the default speech engine for now.
        } else {
        }
 
    }
	
	public boolean inAwesomeMode(){
		return inAwesomeMode;
    }
    
    boolean secondFrameSetup = false;
    
    public void displayReferenceAwesome(final TreeReference selection, int detailIndex) {
        Intent selectedIntent = mSelectComponent.getSelectedIntent(selection);
        //this should be 100% "fragment" able
        if(!secondFrameSetup) {
            findViewById(R.id.screen_compound_select_prompt).setVisibility(View.GONE);
            
            mDetailComponent = new EntityDetailComponent(
                    asw,
                    this,
                    secondFrame,
                    selectedIntent,
                    selection,
                    detailIndex,
                    false
            );

            String passedCommand = selectedIntent.getStringExtra(SessionFrame.STATE_COMMAND_ID);
            
            Entry prototype = mSelectComponent.getEntryForCommand(passedCommand);
            
            if (isEntryCaseList(prototype)) {
                mDetailComponent.notifyIsCaseList();
            }

            if (mDetailComponent.isCompound()) {
                // border around right panel doesn't look right when there are tabs
                secondFrame.setBackgroundDrawable(null);
            }

            secondFrameSetup = true;
        }

        mDetailComponent.refresh(selectedIntent, selection, detailIndex);
    }


}
