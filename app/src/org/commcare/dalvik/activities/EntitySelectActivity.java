package org.commcare.dalvik.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.tasks.EntityLoaderListener;
import org.commcare.android.tasks.EntityLoaderTask;
import org.commcare.android.util.SerializationUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.EntityView;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.components.EntityDetailComponent;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.model.xform.XPathReference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * TODO: Lots of locking and state-based cleanup
 * 
 * @author ctsims
 *
 */
public class EntitySelectActivity extends CommCareActivity implements TextWatcher, EntityLoaderListener, OnItemClickListener, TextToSpeech.OnInitListener  {
    private CommCareSession session;
    private AndroidSessionWrapper asw;
    
    public static final String EXTRA_ENTITY_KEY = "esa_entity_key";
    public static final String EXTRA_IS_MAP = "is_map";
    public static final String EXTRA_SELECTED_POSITION = "selected_position";
    
    private static final int CONFIRM_SELECT = 0;
    private static final int BARCODE_FETCH = 1;
    private static final int MAP_SELECT = 2;
    
    private static final int MENU_SORT = Menu.FIRST;
    private static final int MENU_MAP = Menu.FIRST + 1;
    private static final int MENU_ACTION = Menu.FIRST + 2;
    
    EditText searchbox;
    TextView searchResultStatus;
    EntityListAdapter adapter;
    Entry prototype;
    LinearLayout header;
    ImageButton barcodeButton;
    
    TextToSpeech tts;
    
    SessionDatum selectDatum;
    
    EvaluationContext entityContext;
    
    boolean mResultIsMap = false;
    
    boolean mMappingEnabled = false;
    
    boolean mViewMode = false;
    
    boolean mNoDetailMode = false;
    
    private EntityLoaderTask loader;
    
    private boolean inAwesomeMode = false;
    FrameLayout secondFrame;
    private EntityDetailComponent mDetailComponent;
    
    Intent selectedIntent = null;
    
    String filterString = "";
    
    private Detail shortSelect;
    
    private DataSetObserver mListStateObserver;
    
    private int mSelectedPosition = -1;
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        this.createDataSetObserver();
        
        EntitySelectActivity oldActivity = (EntitySelectActivity)this.getDestroyedActivityState();
        
        if(savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt(EXTRA_SELECTED_POSITION, -1);
            mResultIsMap = savedInstanceState.getBoolean(EXTRA_IS_MAP, false);
        }
        
        try {
            asw = CommCareApplication._().getCurrentSessionWrapper();
            session = asw.getSession();
        } catch(SessionUnavailableException sue){
            //The user isn't logged in! bounce this back to where we came from
            this.setResult(Activity.RESULT_CANCELED);
            this.finish();
            return;
        }
        selectDatum = session.getNeededDatum();
        
        shortSelect = session.getDetail(selectDatum.getShortDetail());
        
        mNoDetailMode = selectDatum.getLongDetail() == null;
        
        if(this.getString(R.string.panes).equals("two") && !mNoDetailMode) {
            //See if we're on a big 'ol screen. We can always display this with the awesome UI.
            
            //Inflate and set up the normal view for now.
            setContentView(R.layout.screen_compound_select);
            View.inflate(this, R.layout.entity_select_layout, (ViewGroup)findViewById(R.id.screen_compound_select_first_pane));
            inAwesomeMode = true;
            
            secondFrame = (FrameLayout)findViewById(R.id.screen_compound_select_second_pane);
            
            TextView message = (TextView)findViewById(R.id.screen_compound_select_prompt);
            message.setText(Localization.get("select.placeholder.message", new String[] {Localization.get("cchq.case")}));
        } else {
            setContentView(R.layout.entity_select_layout);
        }
        ((ListView)this.findViewById(R.id.screen_entity_select_list)).setOnItemClickListener(this);
        
        
        TextView searchLabel = (TextView)findViewById(R.id.screen_entity_select_search_label);
        searchLabel.setText(Localization.get("select.search.label"));
        searchLabel.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {
                // get the focus on the edittext by performing click
                searchbox.performClick();
                // then force the keyboard up since performClick() apparently isn't enough on some devices
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                // only will trigger it if no physical keyboard is open
                inputMethodManager.showSoftInput(searchbox, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        searchbox = (EditText)findViewById(R.id.searchbox);
        searchbox.setMaxLines(3);
        searchbox.setHorizontallyScrolling(false);
        searchResultStatus = (TextView) findViewById(R.id.no_search_results);
        header = (LinearLayout)findViewById(R.id.entity_select_header);
        
        barcodeButton = (ImageButton)findViewById(R.id.barcodeButton);
        
        Vector<Entry> entries = session.getEntriesForCommand(session.getCommand());
        prototype = entries.elementAt(0);
        
        
        //(We shouldn't need the "" here, but we're avoiding making changes to commcare core for release issues)
        if(entries.size() == 1 && (prototype.getXFormNamespace() == null || prototype.getXFormNamespace().equals(""))) {
            mViewMode = true;
        }
                
        barcodeButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                try {
                    startActivityForResult(i, BARCODE_FETCH);
                } catch(ActivityNotFoundException anfe) {
                    Toast noReader = Toast.makeText(EntitySelectActivity.this, "No barcode reader available! You can install one from the android market.", Toast.LENGTH_LONG);
                    noReader.show();
                }
            }
            
        });
        
        searchbox.addTextChangedListener(this);
        searchbox.requestFocus();

        if(oldActivity != null) {
            adapter = oldActivity.adapter;
            //not sure how this happens, but seem plausible.
            if(adapter != null) {
                adapter.setController(this);
                ((ListView)this.findViewById(R.id.screen_entity_select_list)).setAdapter(adapter);
                findViewById(R.id.entity_select_loading).setVisibility(View.GONE);
                
                //Disconnect the old adapter
                adapter.unregisterDataSetObserver(oldActivity.mListStateObserver);
                //connect the new one
                adapter.registerDataSetObserver(this.mListStateObserver);
                
                // See if we changed orientation and had an item selected before
                if(inAwesomeMode && mSelectedPosition >= 0) {
                    TreeReference selection = adapter.getItem(mSelectedPosition);
                    displayReferenceAwesome(selection, mSelectedPosition);
                    updateSelectedItem(selection, false);
                }
            }
        }
        //cts: disabling for non-demo purposes
        //tts = new TextToSpeech(this, this);
    }
    
    private void createDataSetObserver() {
        mListStateObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                //update the search results box
                String query = searchbox.getText().toString();
                if (!"".equals(query)) {
                    searchResultStatus.setText(Localization.get("select.search.status", new String[] {
                        ""+adapter.getCount(true, false), 
                        ""+adapter.getCount(true, true), 
                        query
                    }));
                    searchResultStatus.setVisibility(View.VISIBLE);
                }
                else {
                    searchResultStatus.setVisibility(View.GONE);
                }
            }
        };
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
        
        if(!resuming && !mNoDetailMode && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
            TreeReference entity = selectDatum.getEntityFromID(asw.getEvaluationContext(), this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));
            
            if(entity != null) {
                if(inAwesomeMode) {
                    if (adapter != null) {
                        displayReferenceAwesome(entity, adapter.getPosition(entity));
        			adapter.setAwesomeMode(true);
                        updateSelectedItem(entity, true);
                    }
                } else {
                    //Once we've done the initial dispatch, we don't want to end up triggering it later.
                    this.getIntent().removeExtra(EXTRA_ENTITY_KEY);
                    
                    Intent i = getDetailIntent(entity, null);
                    if (adapter != null) {
                        i.putExtra("entity_detail_index", adapter.getPosition(entity));
                        i.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID, selectDatum.getShortDetail());
                    }
                    startActivityForResult(i, CONFIRM_SELECT);
                    return;
                }
            }
        } 
        
        refreshView();    
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Orientation change, save selected position
        if (inAwesomeMode && mSelectedPosition >= 0) {
            outState.putInt(EXTRA_SELECTED_POSITION, mSelectedPosition);
        }
        
        super.onSaveInstanceState(outState);
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        try {            
            //TODO: Get ec into these text's
            String[] headers = new String[shortSelect.getFields().length];
            
            for(int i = 0 ; i < headers.length ; ++i) {
                headers[i] = shortSelect.getFields()[i].getHeader().evaluate();
                if("address".equals(shortSelect.getFields()[i].getTemplateForm())) {
                    this.mMappingEnabled = true;
                }
            }
            
            //Hm, sadly we possibly need to rebuild this each time. 
            EntityView v = new EntityView(this, shortSelect, headers);
            header.removeAllViews();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            v.setBackgroundResource(R.drawable.blue_tabbed_box);
	    	
	    	// only add headers if we're not using grid mode
	    	if(!shortSelect.usesGridView()){
	    	header.addView(v,params);
	    	}
            
            if(adapter == null && loader == null && !EntityLoaderTask.attachToActivity(this)) {
                EntityLoaderTask theloader = new EntityLoaderTask(shortSelect, asw.getEvaluationContext());
                theloader.attachListener(this);
                
                theloader.execute(selectDatum.getNodeset());
            }
        } catch(SessionUnavailableException sue) {
            //TODO: login and return
        }
    }

    protected Intent getDetailIntent(TreeReference contextRef, Intent i) {
        //Parse out the return value first, and stick it in the appropriate intent so it'll get passed along when
        //we return
        if (i == null) {
            i = new Intent(getApplicationContext(), EntityDetailActivity.class);
        }
        
        TreeReference valueRef = XPathReference.getPathExpr(selectDatum.getValue()).getReference(true);
        AbstractTreeElement element = asw.getEvaluationContext().resolveReference(valueRef.contextualize(contextRef));
        String value = "";
        if(element != null && element.getValue() != null) {
            value = element.getValue().uncast().getString();
        }
        
        //See if we even have a long datum
        if(selectDatum.getLongDetail() != null) {
            //If so, add this. otherwise that'll be the queue to just return
            i.putExtra(EntityDetailComponent.DETAIL_ID, selectDatum.getLongDetail()); 
            i.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID, selectDatum.getPersistentDetail());
        }
   
        i.putExtra(SessionFrame.STATE_DATUM_VAL, value);
        SerializationUtil.serializeToIntent(i, EntityDetailActivity.CONTEXT_REFERENCE, contextRef);
        
        return i;
    }    

    /*
     * (non-Javadoc)
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     */
    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
        if(id == EntityListAdapter.SPECIAL_ACTION) {
            triggerDetailAction();
            return;
        }

        mSelectedPosition = position;
        
        TreeReference selection = adapter.getItem(position);
        if(inAwesomeMode) {
            displayReferenceAwesome(selection, position);
            updateSelectedItem(selection, false);
        } else {
            Intent i = getDetailIntent(selection, null);
            i.putExtra("entity_detail_index", position);
            if (mNoDetailMode) {
                returnWithResult(i);
            } else  {
                startActivityForResult(i, CONFIRM_SELECT);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch(requestCode){
        case BARCODE_FETCH:
            if(resultCode == Activity.RESULT_OK) {
                String result = intent.getStringExtra("SCAN_RESULT");
                this.searchbox.setText(result);
            }
            break;
        case CONFIRM_SELECT:
            resuming = true;
            if(resultCode == RESULT_OK && !mViewMode) {
                // create intent for return and store path
                returnWithResult(intent);
                return;
            } else {
                //Did we enter the detail from mapping mode? If so, go back to that
                if(mResultIsMap) {
                    mResultIsMap = false;
                    Intent i = new Intent(this, EntityMapActivity.class);
                    this.startActivityForResult(i, MAP_SELECT);
                    return;
                } 
                
                //Otherwise, if we're in awesome mode, make sure we retain the original selection
                if(inAwesomeMode) {
                    TreeReference r = SerializationUtil.deserializeFromIntent(intent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
                    if(r != null) {
                        this.displayReferenceAwesome(r, adapter.getPosition(r));
                        updateSelectedItem(r, true);
                    }
                    releaseCurrentMediaEntity();        
                }
                return;
            }
        case MAP_SELECT:
            if(resultCode == RESULT_OK) {
                TreeReference r = SerializationUtil.deserializeFromIntent(intent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
                
                if(inAwesomeMode) {
                    this.displayReferenceAwesome(r, adapter.getPosition(r));
                } else {
                    Intent i = this.getDetailIntent(r, null);
                    if(mNoDetailMode) {
                        returnWithResult(i);
                    } else  {
                        //To go back to map mode if confirm is false
                        mResultIsMap = true;
                        i.putExtra("entity_detail_index", adapter.getPosition(r));
                        startActivityForResult(i, CONFIRM_SELECT);
                    }
                    return;
                }
            } else {
                refreshView();
                return;
            }
        default:
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }


    private void returnWithResult(Intent intent) {
        Intent i = new Intent(this.getIntent());
        
        i.putExtras(intent.getExtras());
        setResult(RESULT_OK, i);

        finish();
    }


    public void afterTextChanged(Editable s) {
        if(searchbox.getText() == s) {
            filterString = s.toString();
            if(adapter != null) {
                adapter.applyFilter(filterString);
            }
        }
    }


    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
        // TODO Auto-generated method stub
        
    }


    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // TODO Auto-generated method stub
        
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
        if(mMappingEnabled) {
            menu.add(0, MENU_MAP, MENU_MAP, Localization.get("select.menu.map")).setIcon(
                    android.R.drawable.ic_menu_mapmode);
        }
        Action action = shortSelect.getCustomAction();
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
        menu.findItem(MENU_SORT).setEnabled(adapter != null);
        
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
                createSortMenu();
                return true;
            case MENU_MAP:
                Intent i = new Intent(this, EntityMapActivity.class);
                this.startActivityForResult(i, MAP_SELECT);
                return true;
            case MENU_ACTION:
                triggerDetailAction();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void triggerDetailAction() {
        Action action = shortSelect.getCustomAction();
        asw.executeStackActions(action.getStackOperations());
        this.setResult(CommCareHomeActivity.RESULT_RESTART);
        this.finish();
    }

    private void createSortMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        builder.setTitle(Localization.get("select.menu.sort"));
        SessionDatum datum = session.getNeededDatum();
        DetailField[] fields = session.getDetail(datum.getShortDetail()).getFields();
        
        List<String> namesList = new ArrayList<String>();
                
        final int[] keyarray = new int[fields.length];
        
        int[] sorts = adapter.getCurrentSort();
        
        int currentSort = sorts.length == 1 ? sorts[0] : -1;
        boolean reversed = adapter.isCurrentSortReversed();

        int added = 0;
        for(int i = 0 ; i < fields.length ; ++i) {
            String result = fields[i].getHeader().evaluate();
            if(!"".equals(result)) {
                String prepend = "";
                if(currentSort == -1) {
                    for(int j = 0 ; j < sorts.length ; ++ j) {
                        if(sorts[j] == i) {
                            prepend = (j+1) + " " + (fields[i].getSortDirection() == DetailField.DIRECTION_DESCENDING ? "(v) " : "(^) ");
                        }
                    }
                } else if(currentSort == i) {
                    prepend = reversed ^ fields[i].getSortDirection() == DetailField.DIRECTION_DESCENDING ? "(v) " : "(^) ";
                }
                namesList.add(prepend + result); 
                keyarray[added] = i;
                added++;
            }
        }
        
        final String[] names = namesList.toArray(new String[0]);
                
        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                adapter.sortEntities(new int[] { keyarray[item]});
                adapter.applyFilter(searchbox.getText().toString());
            }
        });
        
        builder.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                //
            }
        });

            
        AlertDialog alert = builder.create();
        alert.show();
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onDestroy()
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(loader != null) {
            if(isFinishing()) {
                loader.cancel(false);
            } else {
                loader.detachActivity();
            }
        }
        
        if(adapter != null) {
            adapter.signalKilled();
        }
        
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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


    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.EntityLoaderListener#deliverResult(java.util.List, java.util.List)
     */
    @Override
    public void deliverResult(List<Entity<TreeReference>> entities, List<TreeReference> references, NodeEntityFactory factory) {
        loader = null;
        Detail detail = session.getDetail(selectDatum.getShortDetail());
        int[] order = detail.getSortOrder();

        for(int i = 0 ; i < detail.getFields().length ; ++i) {
            String header = detail.getFields()[i].getHeader().evaluate();
            if(order.length == 0 && !"".equals(header)) {
                order = new int[] {i}; 
            }
        }
        
        ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));
    	
        adapter = new EntityListAdapter(EntitySelectActivity.this, detail, references, entities, order, tts, this, factory);
		
        view.setAdapter(adapter);
        adapter.registerDataSetObserver(this.mListStateObserver);
        
        findViewById(R.id.entity_select_loading).setVisibility(View.GONE);
        
        if(adapter != null && filterString != null && !"".equals(filterString)) {
            adapter.applyFilter(filterString);
        }
        
        //In landscape we want to select something now. Either the top item, or the most recently selected one
        if(inAwesomeMode) {
            if (mSelectedPosition >= 0) {
                TreeReference selection = adapter.getItem(mSelectedPosition);
                displayReferenceAwesome(selection, mSelectedPosition);
                updateSelectedItem(selection, false);
            } else {
                updateSelectedItem(true);
            }
        }
        
        
    }

    private void updateSelectedItem(boolean forceMove) {
        TreeReference chosen = null;
        if(selectedIntent != null) {
            chosen = SerializationUtil.deserializeFromIntent(selectedIntent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
        }
        updateSelectedItem(chosen, forceMove);
    }
        
    private void updateSelectedItem(TreeReference selected, boolean forceMove) {
        if(adapter == null) {return;}
        if(selected != null) {
            adapter.notifyCurrentlyHighlighted(selected);
            if(forceMove) {
                ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));
                view.setSelection(adapter.getPosition(selected));
            }
            return;
        }
    }


    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.EntityLoaderListener#attach(org.commcare.android.tasks.EntityLoaderTask)
     */
    @Override
    public void attach(EntityLoaderTask task) {
        findViewById(R.id.entity_select_loading).setVisibility(View.VISIBLE);
        this.loader = task;
	}
	
	public boolean inAwesomeMode(){
		return inAwesomeMode;
    }
    
    boolean secondFrameSetup = false;
    
    public void displayReferenceAwesome(final TreeReference selection, int detailIndex) {
        selectedIntent = getDetailIntent(selection, getIntent());
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
            
            Vector<Entry> entries = session.getEntriesForCommand(passedCommand == null ? session.getCommand() : passedCommand);
            prototype = entries.elementAt(0);
            
            if (isEntryCaseList(prototype)) {
                mDetailComponent.notifyNoResult();
            }

            if (mDetailComponent.isCompound()) {
                // border around right panel doesn't look right when there are tabs
                secondFrame.setBackgroundDrawable(null);
            }

            secondFrameSetup = true;
        }

        mDetailComponent.refresh(selectedIntent, selection, detailIndex);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.EntityLoaderListener#deliverError(java.lang.Exception)
     */
    @Override
    public void deliverError(Exception e) {
        displayException(e);
    }


}
