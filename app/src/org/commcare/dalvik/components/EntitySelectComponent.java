package org.commcare.dalvik.components;

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
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.activities.EntityDetailActivity;
import org.commcare.dalvik.activities.EntityMapActivity;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class EntitySelectComponent implements EntityLoaderListener, TextWatcher, OnItemClickListener {

    public interface EntitySelectListener {
        
        public boolean onAdapterLoaded();
        
        public void onConfirmSelectResult();
        
        public void onError(Exception e);
        
        public boolean onMapSelectResult(TreeReference r, int position);
        
        public boolean onResumeEntity(TreeReference entity, int position);

        /**
         * True if selection event is consumed.
         */
        public boolean onSelect(TreeReference selection, int position);

    }

    public static final String EXTRA_ENTITY_KEY = "esa_entity_key";

    private static final int CONFIRM_SELECT = 0;
    private static final int BARCODE_FETCH = 1;
    private static final int MAP_SELECT = 2;

    private AndroidSessionWrapper asw;
    private CommCareSession session;

    EditText searchbox;
    TextView searchResultStatus;
    EntityListAdapter adapter;
    Entry prototype;
    LinearLayout header;
    ImageButton barcodeButton;
    ListView entitySelectList;

    TextToSpeech tts;

    SessionDatum selectDatum;

    EvaluationContext entityContext;

    boolean mResultIsMap = false;

    boolean mMappingEnabled = false;

    boolean mViewMode = false;

    boolean mNoDetailMode = false;

    private EntityLoaderTask loader;

    Intent selectedIntent = null;

    String filterString = "";

    private Detail shortSelect;

    private DataSetObserver mListStateObserver;

    private final EntitySelectListener listener;

    private CommCareActivity activity;
    
    private View entitySelectLoadingView;

    public EntitySelectComponent(EntitySelectListener listener) {
        this.listener = listener;
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (searchbox.getText() == s) {
            filterString = s.toString();
            if (adapter != null) {
                adapter.applyFilter(filterString);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.EntityLoaderListener#attach(org.commcare.android.tasks.EntityLoaderTask)
     */
    @Override
    public void attach(EntityLoaderTask task) {
        entitySelectLoadingView.setVisibility(View.VISIBLE);
        this.loader = task;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
    }

    private void createDataSetObserver() {
        mListStateObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                // update the search results box
                String query = searchbox.getText().toString();
                if (!"".equals(query)) {
                    searchResultStatus
                            .setText(Localization.get(
                                    "select.search.status",
                                    new String[] {
                                            "" + adapter.getCount(true, false),
                                            "" + adapter.getCount(true, true),
                                            query }));
                    searchResultStatus.setVisibility(View.VISIBLE);
                } else {
                    searchResultStatus.setVisibility(View.GONE);
                }
            }
        };
    }

    public void createSortMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        
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
     * @see org.commcare.android.tasks.EntityLoaderListener#deliverError(java.lang.Exception)
     */
    @Override
    public void deliverError(Exception e) {
        listener.onError(e);
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
        
        adapter = new EntityListAdapter(activity, detail, references, entities, order, tts, activity, factory);
        
        entitySelectList.setAdapter(adapter);
        adapter.registerDataSetObserver(this.mListStateObserver);
        
        entitySelectLoadingView.setVisibility(View.GONE);
        
        if(adapter != null && filterString != null && !"".equals(filterString)) {
            adapter.applyFilter(filterString);
        }
        
        if (!listener.onAdapterLoaded()) {
            updateSelectedItem(true);
        }
        
        
    }

    protected Intent getDetailIntent(TreeReference contextRef, Intent i) {
        //Parse out the return value first, and stick it in the appropriate intent so it'll get passed along when
        //we return
        if (i == null) {
            i = new Intent(activity.getApplicationContext(), EntityDetailActivity.class);
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
    
    public boolean getIsMappingEnabled() {
        return mMappingEnabled;
    }
    
    public boolean getIsAdapterInitialized() {
        return adapter != null;
    }

    public boolean getNoDetailMode() {
        return mNoDetailMode;
    }
    
    public Entry getEntryForCommand(String command) {
        
        Vector<Entry> entries = session.getEntriesForCommand(command == null ? session.getCommand() : command);
        prototype = entries.elementAt(0);
        
        return prototype;
        
    }
    
    public Intent getSelectedIntent(TreeReference selection) {
        selectedIntent = getDetailIntent(selection, activity.getIntent());
        return selectedIntent;
    }
    
    public Action getShortSelectCustomAction() {
        return shortSelect.getCustomAction();
    }

    public void initializeSession(AndroidSessionWrapper asw) {
        
        this.asw = asw;
        this.session = asw.getSession();

        selectDatum = session.getNeededDatum();

        shortSelect = session.getDetail(selectDatum.getShortDetail());

        mNoDetailMode = selectDatum.getLongDetail() == null;

        Vector<Entry> entries = session.getEntriesForCommand(session
                .getCommand());
        prototype = entries.elementAt(0);

        // (We shouldn't need the "" here, but we're avoiding making changes to
        // commcare core for release issues)
        if (entries.size() == 1
                && (prototype.getXFormNamespace() == null || prototype
                        .getXFormNamespace().equals(""))) {
            mViewMode = true;
        }

    }

    public void initializeViews(final CommCareActivity activity, ViewGroup root) {

        this.activity = activity;

        if (root == null) {
            activity.setContentView(R.layout.entity_select_layout);
        } else {
            View.inflate(activity, R.layout.entity_select_layout, root);
        }

        entitySelectList = (ListView) activity
                .findViewById(R.id.screen_entity_select_list);
        entitySelectList.setOnItemClickListener(this);

        TextView searchLabel = (TextView) activity
                .findViewById(R.id.screen_entity_select_search_label);
        searchLabel.setText(Localization.get("select.search.label"));
        searchLabel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // get the focus on the edittext by performing click
                searchbox.performClick();
                // then force the keyboard up since performClick() apparently
                // isn't enough on some devices
                InputMethodManager inputMethodManager = (InputMethodManager) activity
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                // only will trigger it if no physical keyboard is open
                inputMethodManager.showSoftInput(searchbox,
                        InputMethodManager.SHOW_IMPLICIT);
            }
        });

        searchbox = (EditText) activity.findViewById(R.id.searchbox);
        searchbox.setMaxLines(3);
        searchbox.setHorizontallyScrolling(false);
        searchResultStatus = (TextView) activity
                .findViewById(R.id.no_search_results);
        header = (LinearLayout) activity
                .findViewById(R.id.entity_select_header);

        barcodeButton = (ImageButton) activity.findViewById(R.id.barcodeButton);

        barcodeButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                try {
                    activity.startActivityForResult(i, BARCODE_FETCH);
                } catch (ActivityNotFoundException anfe) {
                    Toast noReader = Toast
                            .makeText(
                                    activity,
                                    "No barcode reader available! You can install one from the android market.",
                                    Toast.LENGTH_LONG);
                    noReader.show();
                }
            }

        });

        searchbox.addTextChangedListener(this);
        searchbox.requestFocus();
        
        entitySelectLoadingView = activity.findViewById(R.id.entity_select_loading);

    }
    
    /**
     * True is old adapter found
     */
    public boolean notifyOldComponent(EntitySelectComponent oldComponent) {
        if (oldComponent != null) {
            adapter = oldComponent.adapter;
            //not sure how this happens, but seem plausible.
            if(adapter != null) {
                adapter.setController(activity);
                entitySelectList.setAdapter(adapter);
                entitySelectLoadingView.setVisibility(View.GONE);
                
                //Disconnect the old adapter
                adapter.unregisterDataSetObserver(oldComponent.mListStateObserver);
                //connect the new one
                adapter.registerDataSetObserver(this.mListStateObserver);
                
                return true;
            }
        }
        return false;
    }
    
    public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch(requestCode){
        case BARCODE_FETCH:
            if(resultCode == Activity.RESULT_OK) {
                String result = intent.getStringExtra("SCAN_RESULT");
                this.searchbox.setText(result);
            }
            return true;
        case CONFIRM_SELECT:
            listener.onConfirmSelectResult();
            if(resultCode == Activity.RESULT_OK && !mViewMode) {
                // create intent for return and store path
                returnWithResult(intent);
                return true;
            } else {
                //Did we enter the detail from mapping mode? If so, go back to that
                if(mResultIsMap) {
                    mResultIsMap = false;
                    Intent i = new Intent(activity, EntityMapActivity.class);
                    activity.startActivityForResult(i, MAP_SELECT);
                }
                return true;
            }
        case MAP_SELECT:
            if(resultCode == Activity.RESULT_OK) {
                TreeReference r = SerializationUtil.deserializeFromIntent(intent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
                
                if(!listener.onMapSelectResult(r, adapter.getPosition(r))) {
                    Intent i = this.getDetailIntent(r, null);
                    if(mNoDetailMode) {
                        returnWithResult(i);
                    } else  {
                        //To go back to map mode if confirm is false
                        mResultIsMap = true;
                        i.putExtra("entity_detail_index", adapter.getPosition(r));
                        activity.startActivityForResult(i, CONFIRM_SELECT);
                    }
                    return true;
                }
            } else {
                refreshView();
                return true;
            }
        default:
            return false;
        }
    }

    public void onCreate(Bundle savedInstanceState) {

        createDataSetObserver();

    }
    
    public void onDestroy() {
        
        if(loader != null) {
            if(activity.isFinishing()) {
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
     * 
     * @see
     * android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget
     * .AdapterView, android.view.View, int, long)
     */
    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position,
            long id) {
        if (id == EntityListAdapter.SPECIAL_ACTION) {
            triggerDetailAction();
            return;
        }

        TreeReference selection = adapter.getItem(position);
        updateSelectedItem(selection, false);

        if (!listener.onSelect(selection, position)) {
            Intent i = getDetailIntent(selection, null);
            i.putExtra("entity_detail_index", position);
            if (mNoDetailMode) {
                returnWithResult(i);
            } else {
                activity.startActivityForResult(i, CONFIRM_SELECT);
            }
        }
    }
    
    public void onResume(boolean isStart) {
        
        if(isStart && !mNoDetailMode && activity.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
            TreeReference entity = selectDatum.getEntityFromID(asw.getEvaluationContext(), activity.getIntent().getStringExtra(EXTRA_ENTITY_KEY));
            
            if(entity != null) {
                int position = adapter != null ? adapter.getPosition(entity) : -1;
                if (listener.onResumeEntity(entity, position)) {
                    if (position >= 0) {
                        updateSelectedItem(entity, true);
                    }
                } else {
                    //Once we've done the initial dispatch, we don't want to end up triggering it later.
                    activity.getIntent().removeExtra(EXTRA_ENTITY_KEY);
                    
                    Intent i = getDetailIntent(entity, null);
                    if (adapter != null) {
                        i.putExtra("entity_detail_index", adapter.getPosition(entity));
                        i.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID, selectDatum.getShortDetail());
                    }
                    activity.startActivityForResult(i, CONFIRM_SELECT);
                    return;
                }
            }
        }
        
        refreshView();
        
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
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
            EntityView v = new EntityView(activity, shortSelect, headers);
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
    
    private void returnWithResult(Intent intent) {
        Intent i = new Intent(activity.getIntent());
        
        i.putExtras(intent.getExtras());
        activity.setResult(Activity.RESULT_OK, i);

        activity.finish();
    }

    public void selectItem(int position) {

        TreeReference selection = adapter.getItem(position);
        listener.onSelect(selection, position);

    }
    
    public void startMapSelectActivity() {
        
        Intent i = new Intent(activity, EntityMapActivity.class);
        activity.startActivityForResult(i, MAP_SELECT);
        
    }
    
    public void triggerDetailAction() {
        Action action = shortSelect.getCustomAction();
        asw.executeStackActions(action.getStackOperations());
        activity.setResult(CommCareHomeActivity.RESULT_RESTART);
        activity.finish();
    }

    private void updateSelectedItem(boolean forceMove) {
        TreeReference chosen = null;
        if(selectedIntent != null) {
            chosen = SerializationUtil.deserializeFromIntent(selectedIntent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
        }
        updateSelectedItem(chosen, forceMove);
    }

    private void updateSelectedItem(TreeReference selected, boolean forceMove) {
        if (adapter == null) {
            return;
        }
        if (selected != null) {
            adapter.notifyCurrentlyHighlighted(selected);
            if (forceMove) {
                entitySelectList.setSelection(adapter.getPosition(selected));
            }
            return;
        }
    }

}
