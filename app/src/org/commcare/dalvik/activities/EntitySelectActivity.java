package org.commcare.dalvik.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.logic.DetailCalloutListenerDefaultImpl;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.tasks.EntityLoaderListener;
import org.commcare.android.tasks.EntityLoaderTask;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.EntityView;
import org.commcare.android.view.TabbedDetailView;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.model.xform.XPathReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * 
 * TODO: Lots of locking and state-based cleanup
 * 
 * @author ctsims
 *
 */
public class EntitySelectActivity extends CommCareActivity implements TextWatcher, EntityLoaderListener, OnItemClickListener, TextToSpeech.OnInitListener, DetailCalloutListener {
    private CommCareSession session;
    private AndroidSessionWrapper asw;
    
    public static final String EXTRA_ENTITY_KEY = "esa_entity_key";
    public static final String EXTRA_IS_MAP = "is_map";
    
    private static final int CONFIRM_SELECT = 0;
    private static final int BARCODE_FETCH = 1;
    private static final int MAP_SELECT = 2;
    private static final int CALLOUT = 3;
    
    private static final int MENU_SORT = Menu.FIRST;
    private static final int MENU_MAP = Menu.FIRST + 1;
    private static final int MENU_ACTION = Menu.FIRST + 2;
    
    EditText searchbox;
    TextView searchResultStatus;
    EntityListAdapter adapter;
    LinearLayout header;
    ImageButton calloutButton;
    
    TextToSpeech tts;
    
    SessionDatum selectDatum;
    
    EvaluationContext entityContext;
    
    boolean mResultIsMap = false;
    
    boolean mMappingEnabled = false;
    
    // Is the detail screen for showing entities, without option for moving
    // forward on to form manipulation?
    boolean mViewMode = false;

    // Has a detail screen not been defined?
    boolean mNoDetailMode = false;
    
    private EntityLoaderTask loader;
    
    private boolean inAwesomeMode = false;
    FrameLayout rightFrame;
    TabbedDetailView detailView;
    
    Intent selectedIntent = null;
    
    String filterString = "";
    
    private Detail shortSelect;
    
    private DataSetObserver mListStateObserver;
    
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
            //See if we're on a big 'ol screen.
            
            if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                //If we're in landscape mode, we can display this with the awesome UI.
                
                //Inflate and set up the normal view for now.
                setContentView(R.layout.screen_compound_select);
                View.inflate(this, R.layout.entity_select_layout, (ViewGroup)findViewById(R.id.screen_compound_select_left_pane));
                inAwesomeMode = true;
                
                rightFrame = (FrameLayout)findViewById(R.id.screen_compound_select_right_pane);
                
                TextView message = (TextView)findViewById(R.id.screen_compound_select_prompt);
                //use the old method here because some Android versions don't like Spannables for titles
                message.setText(Localization.get("select.placeholder.message", new String[]{Localization.get("cchq.case")}));
            } else {
                setContentView(R.layout.entity_select_layout);
                //So we're not in landscape mode anymore, but were before. If we had something selected, we 
                //need to go to the detail screen instead.
                if (oldActivity != null) {
                    Intent intent = this.getIntent();

                    TreeReference selectedRef = SerializationUtil.deserializeFromIntent(intent,
                            EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
                    if (selectedRef != null) {
                        // remove the reference from this intent, ensuring we
                        // don't re-launch the detail for an entity even after
                        // it being de-selected.
                        intent.removeExtra(EntityDetailActivity.CONTEXT_REFERENCE);

                        // attach the selected entity to the new detail intent
                        // we're launching
                        Intent detailIntent = getDetailIntent(selectedRef, null);

                        startOther = true;
                        startActivityForResult(detailIntent, CONFIRM_SELECT);
                    }
                }
            }
        } else {
            setContentView(R.layout.entity_select_layout);
        }
        ((ListView)this.findViewById(R.id.screen_entity_select_list)).setOnItemClickListener(this);
        
        
        TextView searchLabel = (TextView)findViewById(R.id.screen_entity_select_search_label);
        //use the old method here because some Android versions don't like Spannables for titles
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

        mViewMode = session.isViewCommand(session.getCommand());

        Callout callout = shortSelect.getCallout();

        if (callout == null) {
            // Default to barcode scanning if no callout defined in the detail
            calloutButton = (ImageButton)findViewById(R.id.barcodeButton);
            calloutButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent("com.google.zxing.client.android.SCAN");
                    try {
                        startActivityForResult(i, BARCODE_FETCH);
                    } catch (ActivityNotFoundException anfe) {
                        Toast noReader = Toast.makeText(EntitySelectActivity.this,
                                "No barcode reader available! You can install one " +
                                "from the android market.",
                                Toast.LENGTH_LONG);
                        noReader.show();
                    }
                }
            });
        } else {
            CalloutData calloutData = callout.evaluate();

            if (calloutData.getImage() != null) {
                setupImageLayout(calloutButton, calloutData.getImage());
            }

            final String actionName = calloutData.getActionName();
            final Hashtable<String, String> extras = calloutData.getExtras();

            calloutButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(actionName);

                    for(String key: extras.keySet()){
                        i.putExtra(key, extras.get(key));
                    }
                    try {
                        startActivityForResult(i, CALLOUT);
                    } catch (ActivityNotFoundException anfe) {
                        Toast noReader = Toast.makeText(EntitySelectActivity.this, "No application found for action: " + actionName, Toast.LENGTH_LONG);
                        noReader.show();
                    }
                }
            });
        }

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
            }
        }
        //cts: disabling for non-demo purposes
        //tts = new TextToSpeech(this, this);
    }

    /**
     * Updates the ImageView layout that is passed in, based on the
     * new id and source
     */
    public void setupImageLayout(View layout, final String imagePath) {
        ImageView iv = (ImageView)layout;
        Bitmap b;
        if (!imagePath.equals("")) {
            try {
                b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(imagePath).getStream());
                if (b == null) {
                    // Input stream could not be used to derive bitmap, so
                    // showing error-indicating image
                    iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
                } else {
                    iv.setImageBitmap(b);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                // Error loading image, default to folder button
                iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
            } catch (InvalidReferenceException ex) {
                ex.printStackTrace();
                // No image, default to folder button
                iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
            }
        } else {
            // no image passed in, draw a white background
            iv.setImageDrawable(getResources().getDrawable(R.color.white));
        }
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
        if (this.isFinishing() || startOther) { return; }
        
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
            } else {
                startTimer();
            }
            
        } catch(SessionUnavailableException sue) {
            //TODO: login and return
        }
    }
    
    
    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }
    
    @Override
    public void onStop() {
        super.onStop();
        stopTimer();
    }
    

    

    /**
     * Attach element selection information to the intent argument, or create a
     * new EntityDetailActivity if null. Used for displaying a detailed view of
     * an element (form instance).
     *
     * @param contextRef reference to the selected element for which to display
     * detailed view
     * @param detailIntent intent to attach extra data to. If null, create a fresh
     * EntityDetailActivity intent
     * @return The intent argument, or a newly created one, with element
     * selection information attached.
     */
    protected Intent getDetailIntent(TreeReference contextRef, Intent detailIntent) {
        if (detailIntent == null) {
            detailIntent = new Intent(getApplicationContext(), EntityDetailActivity.class);
        }

        // grab the session's (form) element reference, and load it.
        TreeReference elementRef =
            XPathReference.getPathExpr(selectDatum.getValue()).getReference(true);
        AbstractTreeElement element =
            asw.getEvaluationContext().resolveReference(elementRef.contextualize(contextRef));

        String value = "";
        // get the case id and add it to the intent
        if(element != null && element.getValue() != null) {
            value = element.getValue().uncast().getString();
        }
        detailIntent.putExtra(SessionFrame.STATE_DATUM_VAL, value);

        // Include long datum info if present. Otherwise that'll be the queue
        // to just return
        if (selectDatum.getLongDetail() != null) {
            detailIntent.putExtra(EntityDetailActivity.DETAIL_ID,
                    selectDatum.getLongDetail());
            detailIntent.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID,
                    selectDatum.getPersistentDetail());
        }

        SerializationUtil.serializeToIntent(detailIntent,
                EntityDetailActivity.CONTEXT_REFERENCE, contextRef);

        return detailIntent;
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
        case CALLOUT:
            if (resultCode == Activity.RESULT_OK) {
                String result = intent.getStringExtra("odk_intent_data");
                if (result != null) {
                    this.searchbox.setText(result);
                    break;
                }
                Callout callout = shortSelect.getCallout();
                for (String key : callout.getResponses()) {
                    result = intent.getStringExtra(key);
                    if (result != null) {
                        this.searchbox.setText(result);
                        break;
                    }
                }
            }
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

                if (inAwesomeMode) {
                    // Retain original element selection
                    TreeReference r = SerializationUtil.deserializeFromIntent(intent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
                    if (r != null && adapter != null) {
                        // TODO: added 'adapter != null' due to a
                        // NullPointerException, we need to figure out how to
                        // make sure adapter is never null -- PLM
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
        //use the old method here because some Android versions don't like Spannables for titles
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
        //use the old method here because some Android versions don't like Spannables for titles
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
            updateSelectedItem(true);
        }
        
        this.startTimer();        
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
    
    boolean rightFrameSetup = false;
    NodeEntityFactory factory;
    
    private void select() {
        // create intent for return and store path
        Intent i = new Intent(EntitySelectActivity.this.getIntent());
        i.putExtra(SessionFrame.STATE_DATUM_VAL, selectedIntent.getStringExtra(SessionFrame.STATE_DATUM_VAL));
        setResult(RESULT_OK, i);
        finish();
    }

    // CommCare-159503: implementing DetailCalloutListener so it will not crash the app when requesting call/sms
    public void callRequested(String phoneNumber) {
        DetailCalloutListenerDefaultImpl.callRequested(this, phoneNumber);
    }

    public void addressRequested(String address) {
        DetailCalloutListenerDefaultImpl.addressRequested(this, address);
    }

    public void playVideo(String videoRef) {
        DetailCalloutListenerDefaultImpl.playVideo(this, videoRef);
    }

    public void performCallout(CalloutData callout, int id) {
        DetailCalloutListenerDefaultImpl.performCallout(this, callout, id);
    }
    
    public void displayReferenceAwesome(final TreeReference selection, int detailIndex) {
        selectedIntent = getDetailIntent(selection, getIntent());
        //this should be 100% "fragment" able
        if(!rightFrameSetup) {
            findViewById(R.id.screen_compound_select_prompt).setVisibility(View.GONE);
            View.inflate(this, R.layout.entity_detail, rightFrame);
            Button next = (Button)findViewById(R.id.entity_select_button);
            //use the old method here because some Android versions don't like Spannables for titles
            next.setText(Localization.get("select.detail.confirm"));
            next.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    select();
                    return;
                }
            });

            if (mViewMode) {
                next.setVisibility(View.GONE);
            }

            String passedCommand = selectedIntent.getStringExtra(SessionFrame.STATE_COMMAND_ID);

            if (passedCommand != null) {
                mViewMode = session.isViewCommand(passedCommand);
            } else {
                mViewMode = session.isViewCommand(session.getCommand());
            }

            detailView = new TabbedDetailView(this);
            detailView.setRoot((ViewGroup) rightFrame.findViewById(R.id.entity_detail_tabs));

            factory = new NodeEntityFactory(session.getDetail(selectedIntent.getStringExtra(EntityDetailActivity.DETAIL_ID)), session.getEvaluationContext(new CommCareInstanceInitializer(session)));            
            Detail detail = factory.getDetail();
            detailView.setDetail(detail);

            if (detail.isCompound()) {
                // border around right panel doesn't look right when there are tabs
                rightFrame.setBackgroundDrawable(null);
            }

            rightFrameSetup = true;
        }

           detailView.refresh(factory.getDetail(), selection, detailIndex, false);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.EntityLoaderListener#deliverError(java.lang.Exception)
     */
    @Override
    public void deliverError(Exception e) {
        displayException(e);
    }

    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onForwardSwipe()
     */
    @Override
    protected boolean onForwardSwipe() {
        // If user has picked an entity, move along to form entry
        if (selectedIntent != null) {
            if (inAwesomeMode && detailView != null && detailView.getCurrentTab() < detailView.getTabCount() - 1) {
                return false;
            }

            if (!mViewMode) {
                select();
            }
        }
        return true;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onBackwardSwipe()
     */
    @Override
    protected boolean onBackwardSwipe() {
        if (inAwesomeMode && detailView != null && detailView.getCurrentTab() > 0) {
            return false;
        }
        finish();
        return true;
    }

    //Below is helper code for the Refresh Feature. 
    //this is a dev feature and should get restructured before release in prod.
    //If the devloper setting is turned off this code should do nothing.
    
    private void triggerRebuild() {
        if(loader == null && !EntityLoaderTask.attachToActivity(this)) {
            EntityLoaderTask theloader = new EntityLoaderTask(shortSelect, asw.getEvaluationContext());
            theloader.attachListener(this);
            
            theloader.execute(selectDatum.getNodeset());
        }
    }
    
    private Timer myTimer;
    private Object timerLock = new Object();
    boolean cancelled;
    
    private void startTimer() {
        if(!DeveloperPreferences.isListRefreshEnabled()) { return; }
        synchronized(timerLock) {
            if(myTimer == null) {
                myTimer = new Timer();
                myTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                            runOnUiThread( new Runnable() {
                                @Override
                                public void run() {
                                    if(!cancelled) {
                                        triggerRebuild();
                                    }
                                }
                            });
                        }
                }, 15*1000, 15 * 1000);
                cancelled = false;
            }
        }
    }
    
    private void stopTimer() {
        synchronized(timerLock) {
            if(myTimer != null) {
                myTimer.cancel();
                myTimer = null;
                cancelled = true;
            }
        }
    }

}
