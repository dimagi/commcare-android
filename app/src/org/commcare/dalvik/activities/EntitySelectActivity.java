package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
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
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.logic.DetailCalloutListenerDefaultImpl;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.tasks.EntityLoaderListener;
import org.commcare.android.tasks.EntityLoaderTask;
import org.commcare.android.util.AndroidInstanceInitializer;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.android.view.EntityView;
import org.commcare.android.view.TabbedDetailView;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.SessionDatum;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.odk.collect.android.views.media.AudioController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * TODO: Lots of locking and state-based cleanup
 *
 * @author ctsims
 */
public class EntitySelectActivity extends SessionAwareCommCareActivity
        implements TextWatcher,
        EntityLoaderListener,
        OnItemClickListener,
        TextToSpeech.OnInitListener,
        DetailCalloutListener {
    private static final String TAG = EntitySelectActivity.class.getSimpleName();

    private CommCareSession session;
    private AndroidSessionWrapper asw;

    public static final String EXTRA_ENTITY_KEY = "esa_entity_key";
    private static final String EXTRA_IS_MAP = "is_map";

    private static final int CONFIRM_SELECT = 0;
    private static final int MAP_SELECT = 2;
    private static final int BARCODE_FETCH = 1;
    private static final int CALLOUT = 3;

    private static final int MENU_SORT = Menu.FIRST;
    private static final int MENU_MAP = Menu.FIRST + 1;
    private static final int MENU_ACTION = Menu.FIRST + 2;

    private EditText searchbox;
    private TextView searchResultStatus;
    private EntityListAdapter adapter;
    private LinearLayout header;
    private ImageButton barcodeButton;
    private SearchView searchView;
    private MenuItem searchItem;

    private TextToSpeech tts;

    private SessionDatum selectDatum;

    private boolean mResultIsMap = false;

    private boolean mMappingEnabled = false;

    // Is the detail screen for showing entities, without option for moving
    // forward on to form manipulation?
    private boolean mViewMode = false;

    // No detail confirm screen is defined for this entity select
    private boolean mNoDetailMode = false;

    private EntityLoaderTask loader;

    private boolean inAwesomeMode = false;
    private FrameLayout rightFrame;
    private TabbedDetailView detailView;

    private Intent selectedIntent = null;

    private String filterString = "";

    private Detail shortSelect;

    private DataSetObserver mListStateObserver;
    private OnClickListener barcodeScanOnClickListener;

    private boolean resuming = false;
    private boolean startOther = false;

    private boolean rightFrameSetup = false;
    private NodeEntityFactory factory;

    private Timer myTimer;
    private final Object timerLock = new Object();
    private boolean cancelled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.createDataSetObserver();

        EntitySelectActivity oldActivity = (EntitySelectActivity)this.getDestroyedActivityState();

        if (savedInstanceState != null) {
            mResultIsMap = savedInstanceState.getBoolean(EXTRA_IS_MAP, false);
        }

        asw = CommCareApplication._().getCurrentSessionWrapper();
        session = asw.getSession();

        selectDatum = session.getNeededDatum();

        shortSelect = session.getDetail(selectDatum.getShortDetail());

        mNoDetailMode = selectDatum.getLongDetail() == null;

        if (this.getString(R.string.panes).equals("two") && !mNoDetailMode) {
            //See if we're on a big 'ol screen.

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
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
        ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));
        view.setOnItemClickListener(this);
        setupDivider(view);

        TextView searchLabel = (TextView)findViewById(R.id.screen_entity_select_search_label);
        //use the old method here because some Android versions don't like Spannables for titles
        searchLabel.setText(Localization.get("select.search.label"));
        searchLabel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // get the focus on the edittext by performing click
                searchbox.performClick();
                // then force the keyboard up since performClick() apparently isn't enough on some devices
                InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                // only will trigger it if no physical keyboard is open
                inputMethodManager.showSoftInput(searchbox, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        searchbox = (EditText)findViewById(R.id.searchbox);
        searchbox.setMaxLines(3);
        searchbox.setHorizontallyScrolling(false);
        searchResultStatus = (TextView)findViewById(R.id.no_search_results);
        header = (LinearLayout)findViewById(R.id.entity_select_header);

        mViewMode = session.isViewCommand(session.getCommand());

        barcodeButton = (ImageButton)findViewById(R.id.barcodeButton);

        Callout callout = shortSelect.getCallout();
        if (callout ==  null) {
            barcodeScanOnClickListener = makeBarcodeClickListener();
        } else {
            barcodeScanOnClickListener = makeCalloutClickListener(callout);
        }

        barcodeButton.setOnClickListener(barcodeScanOnClickListener);

        searchbox.addTextChangedListener(this);
        searchbox.requestFocus();

        if (oldActivity != null) {
            adapter = oldActivity.adapter;
            // on orientation change
            if (adapter != null) {
                view.setAdapter(adapter);
                setupDivider(view);
                findViewById(R.id.entity_select_loading).setVisibility(View.GONE);

                //Disconnect the old adapter
                adapter.unregisterDataSetObserver(oldActivity.mListStateObserver);
                //connect the new one
                adapter.registerDataSetObserver(this.mListStateObserver);
            }
        }
        //cts: disabling for non-demo purposes
        //tts = new TextToSpeech(this, this);

        restoreLastQueryString();

        if (!isUsingActionBar()) {
            searchbox.setText(lastQueryString);
        }
    }

    /**
     * @return A click listener that launches QR code scanner
     */
    private View.OnClickListener makeBarcodeClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                try {
                    EntitySelectActivity.this.startActivityForResult(i, BARCODE_FETCH);
                } catch (ActivityNotFoundException anfe) {
                    Toast.makeText(EntitySelectActivity.this,
                            "No barcode reader available! You can install one " +
                                    "from the android market.",
                            Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    /**
     * Build click listener from callout: set button's image, get intent action,
     * and copy extras into intent.
     *
     * @param callout contains intent action and extras, and sometimes button image
     * @return click listener that launches the callout's activity with the
     * associated callout extras
     */
    private View.OnClickListener makeCalloutClickListener(Callout callout) {
        final CalloutData calloutData = callout.getRawCalloutData();

        if (calloutData.getImage() != null) {
            setupImageLayout(barcodeButton, calloutData.getImage());
        }

        final Intent i = new Intent(calloutData.getActionName());
        for (Map.Entry<String, String> keyValue : calloutData.getExtras().entrySet()) {
            i.putExtra(keyValue.getKey(), keyValue.getValue());
        }
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    EntitySelectActivity.this.startActivityForResult(i, CALLOUT);
                } catch (ActivityNotFoundException anfe) {
                    Toast.makeText(EntitySelectActivity.this,
                            "No application found for action: " + i.getAction(),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    /**
     * Updates the ImageView layout that is passed in, based on the
     * new id and source
     */
    private void setupImageLayout(View layout, final String imagePath) {
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
            } catch (IOException | InvalidReferenceException ex) {
                ex.printStackTrace();
                // Error loading image, default to folder button
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
                String query = getSearchText().toString();
                if (!"".equals(query)) {
                    searchResultStatus.setText(Localization.get("select.search.status", new String[]{
                            "" + adapter.getCount(true, false),
                            "" + adapter.getCount(true, true),
                            query
                    }));
                    searchResultStatus.setVisibility(View.VISIBLE);
                } else {
                    searchResultStatus.setVisibility(View.GONE);
                }
            }
        };
    }

    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }

    @Override
    public String getActivityTitle() {
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Don't go through making the whole thing if we're finishing anyway.
        if (this.isFinishing() || startOther) {
            return;
        }

        if (!resuming && !mNoDetailMode && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
            TreeReference entity =
                    selectDatum.getEntityFromID(asw.getEvaluationContext(),
                            this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));

            if (entity != null) {
                if (inAwesomeMode) {
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
                        i.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID,
                                selectDatum.getShortDetail());
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

            for (int i = 0; i < headers.length; ++i) {
                headers[i] = shortSelect.getFields()[i].getHeader().evaluate();
                if ("address".equals(shortSelect.getFields()[i].getTemplateForm())) {
                    this.mMappingEnabled = true;
                }
            }

            header.removeAllViews();

            // only add headers if we're not using grid mode
            if (!shortSelect.usesGridView()) {
                //Hm, sadly we possibly need to rebuild this each time.
                EntityView v = new EntityView(this, shortSelect, headers);
                header.addView(v);
            }

            if (adapter == null &&
                    loader == null &&
                    !EntityLoaderTask.attachToActivity(this)) {
                EntityLoaderTask theloader =
                        new EntityLoaderTask(shortSelect, asw.getEvaluationContext());
                theloader.attachListener(this);
                theloader.execute(selectDatum.getNodeset());
            } else {
                startTimer();
            }
        } catch (RuntimeException re) {
            createErrorDialog(re.getMessage(), true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopTimer();
        saveLastQueryString();
    }

    /**
     * Get an intent for displaying the confirm detail screen for an element (either just populates
     * the given intent with the necessary information, or creates a new one if it is null)
     *
     * @param contextRef reference to the selected element for which to display
     *                   detailed view
     * @return The intent argument, or a newly created one, with element
     * selection information attached.
     */
    private Intent getDetailIntent(TreeReference contextRef, Intent detailIntent) {
        if (detailIntent == null) {
            detailIntent = new Intent(getApplicationContext(), EntityDetailActivity.class);
        }
        return populateDetailIntent(detailIntent, contextRef, this.selectDatum, this.asw);
    }

    /**
     * Attach all element selection information to the intent argument and return the resulting
     * intent
     */
    protected static Intent populateDetailIntent(Intent detailIntent,
                                                 TreeReference contextRef,
                                                 SessionDatum selectDatum,
                                                 AndroidSessionWrapper asw) {

        String caseId = SessionDatum.getCaseIdFromReference(
                contextRef, selectDatum, asw.getEvaluationContext());
        detailIntent.putExtra(SessionFrame.STATE_DATUM_VAL, caseId);

        // Include long datum info if present
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

    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
        if (id == EntityListAdapter.SPECIAL_ACTION) {
            triggerDetailAction();
            return;
        }

        TreeReference selection = adapter.getItem(position);
        if (CommCarePreferences.isEntityDetailLoggingEnabled()) {
            Logger.log(EntityDetailActivity.class.getSimpleName(), selectDatum.getLongDetail());
        }
        if (inAwesomeMode) {
            displayReferenceAwesome(selection, position);
            updateSelectedItem(selection, false);
        } else {
            Intent i = getDetailIntent(selection, null);
            i.putExtra("entity_detail_index", position);
            if (mNoDetailMode) {
                // Not actually launching detail intent because there's no confirm detail available
                returnWithResult(i);
            } else {
                startActivityForResult(i, CONFIRM_SELECT);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case BARCODE_FETCH:
                processBarcodeFetch(resultCode, intent);
                break;
            case CALLOUT:
                processCalloutResult(resultCode, intent);
                break;
            case CONFIRM_SELECT:
                resuming = true;
                if (resultCode == RESULT_OK && !mViewMode) {
                    // create intent for return and store path
                    returnWithResult(intent);
                    return;
                } else {
                    //Did we enter the detail from mapping mode? If so, go back to that
                    if (mResultIsMap) {
                        mResultIsMap = false;
                        Intent i = new Intent(this, EntityMapActivity.class);
                        this.startActivityForResult(i, MAP_SELECT);
                        return;
                    }

                    if (inAwesomeMode) {
                        // Retain original element selection
                        TreeReference r =
                                SerializationUtil.deserializeFromIntent(intent,
                                        EntityDetailActivity.CONTEXT_REFERENCE,
                                        TreeReference.class);
                        if (r != null && adapter != null) {
                            // TODO: added 'adapter != null' due to a
                            // NullPointerException, we need to figure out how to
                            // make sure adapter is never null -- PLM
                            this.displayReferenceAwesome(r, adapter.getPosition(r));
                            updateSelectedItem(r, true);
                        }
                        AudioController.INSTANCE.releaseCurrentMediaEntity();
                    }
                    return;
                }
            case MAP_SELECT:
                if (resultCode == RESULT_OK) {
                    TreeReference r =
                            SerializationUtil.deserializeFromIntent(intent,
                                    EntityDetailActivity.CONTEXT_REFERENCE,
                                    TreeReference.class);

                    if (inAwesomeMode) {
                        this.displayReferenceAwesome(r, adapter.getPosition(r));
                    } else {
                        Intent i = this.getDetailIntent(r, null);
                        if (mNoDetailMode) {
                            returnWithResult(i);
                        } else {
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

    private void processBarcodeFetch(int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) {
            String result = intent.getStringExtra("SCAN_RESULT");
            if (result != null) {
                result = result.trim();
            }
            setSearchText(result);
        }
    }

    private void processCalloutResult(int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) {
            String result = intent.getStringExtra("odk_intent_data");
            if (result != null){
                setSearchText(result.trim());
            } else {
                for (String key : shortSelect.getCallout().getResponses()) {
                    result = intent.getExtras().getString(key);
                    if (result != null) {
                        setSearchText(result);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Finish this activity, including all extras from the given intent in the finishing intent
     */
    private void returnWithResult(Intent intent) {
        Intent i = new Intent(this.getIntent());
        i.putExtras(intent.getExtras());
        setResult(RESULT_OK, i);
        finish();
    }


    @Override
    public void afterTextChanged(Editable s) {
        if (getSearchText() == s) {
            filterString = s.toString();
            if (adapter != null) {
                adapter.applyFilter(filterString);
            }
        }
        if (!isUsingActionBar()) {
            lastQueryString = filterString;
        }
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        //use the old method here because some Android versions don't like Spannables for titles
        menu.add(0, MENU_SORT, MENU_SORT, Localization.get("select.menu.sort")).setIcon(
                android.R.drawable.ic_menu_sort_alphabetically);
        if (mMappingEnabled) {
            menu.add(0, MENU_MAP, MENU_MAP, Localization.get("select.menu.map")).setIcon(
                    android.R.drawable.ic_menu_mapmode);
        }
        Action action = shortSelect.getCustomAction();
        if (action != null) {
            ViewUtil.addDisplayToMenu(this, menu, MENU_ACTION,
                    action.getDisplay().evaluate());
        }

        tryToAddActionSearchBar(this, menu, new ActionBarInstantiator() {
            // again, this should be unnecessary...
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onActionBarFound(MenuItem searchItem, SearchView searchView) {
                EntitySelectActivity.this.searchItem = searchItem;
                EntitySelectActivity.this.searchView = searchView;
                // restore last query string in the searchView if there is one
                if (lastQueryString != null && lastQueryString.length() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        searchItem.expandActionView();
                    }
                    searchView.setQuery(lastQueryString, false);
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Setting lastQueryString in searchView: (" + lastQueryString + ")");
                    }
                    if (adapter != null) {
                        adapter.applyFilter(lastQueryString == null ? "" : lastQueryString);
                    }
                }
                EntitySelectActivity.this.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        lastQueryString = newText;
                        if (adapter != null) {
                            adapter.applyFilter(newText);
                        }
                        return false;
                    }
                });
            }
        });

        return true;
    }

    /**
     * Checks if this activity uses the ActionBar
     */
    private boolean isUsingActionBar() {
        return searchView != null;
    }

    @SuppressWarnings("NewApi")
    private CharSequence getSearchText() {
        if (isUsingActionBar()) {
            return searchView.getQuery();
        }
        return searchbox.getText();
    }

    @SuppressWarnings("NewApi")
    private void setSearchText(CharSequence text) {
        if (isUsingActionBar()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                searchItem.expandActionView();
            }
            searchView.setQuery(text, false);
        }
        searchbox.setText(text);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        //only display the sort menu if we're going to be able to sort
        //(IE: not until the items have loaded)
        menu.findItem(MENU_SORT).setEnabled(adapter != null);

        return super.onPrepareOptionsMenu(menu);
    }

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
            // handling click on the barcode scanner's actionbar
            // trying to set the onclicklistener in its view in the onCreateOptionsMenu method does not work because it returns null
            case R.id.barcode_scan_action_bar:
                barcodeScanOnClickListener.onClick(null);
                return true;
            // this is needed because superclasses do not implement the menu_settings click
            case R.id.menu_settings:
                CommCareHomeActivity.createPreferencesMenu(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void triggerDetailAction() {
        Action action = shortSelect.getCustomAction();

        try {
            asw.executeStackActions(action.getStackOperations());
        } catch (XPathTypeMismatchException e) {
            Logger.exception(e);
            CommCareActivity.createErrorDialog(this, e.getMessage(), true);
            return;
        }

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
        for (int i = 0; i < fields.length; ++i) {
            String result = fields[i].getHeader().evaluate();
            if (!"".equals(result)) {
                String prepend = "";
                if (currentSort == -1) {
                    for (int j = 0; j < sorts.length; ++j) {
                        if (sorts[j] == i) {
                            prepend = (j + 1) + " " + (fields[i].getSortDirection() == DetailField.DIRECTION_DESCENDING ? "(v) " : "(^) ");
                        }
                    }
                } else if (currentSort == i) {
                    prepend = reversed ^ fields[i].getSortDirection() == DetailField.DIRECTION_DESCENDING ? "(v) " : "(^) ";
                }
                namesList.add(prepend + result);
                keyarray[added] = i;
                added++;
            }
        }

        final String[] names = namesList.toArray(new String[namesList.size()]);

        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                adapter.sortEntities(new int[]{keyarray[item]});
                adapter.applyFilter(getSearchText().toString());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loader != null) {
            if (isFinishing()) {
                loader.cancel(false);
            } else {
                loader.detachActivity();
            }
        }

        if (adapter != null) {
            adapter.signalKilled();
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {
            //using the default speech engine for now.
        } else {
        }

    }


    @Override
    public void deliverResult(List<Entity<TreeReference>> entities,
                              List<TreeReference> references,
                              NodeEntityFactory factory) {
        loader = null;
        Detail detail = session.getDetail(selectDatum.getShortDetail());
        int[] order = detail.getSortOrder();

        for (int i = 0; i < detail.getFields().length; ++i) {
            String header = detail.getFields()[i].getHeader().evaluate();
            if (order.length == 0 && !"".equals(header)) {
                order = new int[]{i};
            }
        }

        ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));

        setupDivider(view);

        adapter = new EntityListAdapter(EntitySelectActivity.this, detail, references, entities, order, tts, factory);

        view.setAdapter(adapter);
        adapter.registerDataSetObserver(this.mListStateObserver);

        findViewById(R.id.entity_select_loading).setVisibility(View.GONE);

        if (adapter != null && filterString != null && !"".equals(filterString)) {
            adapter.applyFilter(filterString);
        }

        //In landscape we want to select something now. Either the top item, or the most recently selected one
        if (inAwesomeMode) {
            updateSelectedItem(true);
        }

        rebuildMenus();

        this.startTimer();
    }

    private void setupDivider(ListView view) {
        boolean useNewDivider = shortSelect.usesGridView();

        if (useNewDivider) {
            int viewWidth = view.getWidth();
            float density = getResources().getDisplayMetrics().density;
            int viewWidthDP = (int)(viewWidth / density);
            // sometimes viewWidth is 0, and in this case we default to a reasonable value taken from dimens.xml
            int dividerWidth = viewWidth == 0 ? (int)getResources().getDimension(R.dimen.entity_select_divider_left_inset) : (int)(viewWidth / 6.0);

            Drawable divider = getResources().getDrawable(R.drawable.divider_case_list_modern);

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "ListView divider is: " + divider + ", estimated divider width is: " + dividerWidth + ", viewWidth (dp) is: " + viewWidthDP);
            }

            if (BuildConfig.DEBUG && (divider == null || !(divider instanceof LayerDrawable))) {
                throw new AssertionError("Divider should be a LayerDrawable!");
            }

            LayerDrawable layerDrawable = (LayerDrawable)divider;

            dividerWidth += (int)getResources().getDimension(R.dimen.row_padding_horizontal);

            layerDrawable.setLayerInset(0, dividerWidth, 0, 0, 0);

            view.setDivider(layerDrawable);
        } else {
            view.setDivider(null);

        }
        view.setDividerHeight((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));

    }

    private void updateSelectedItem(boolean forceMove) {
        TreeReference chosen = null;
        if (selectedIntent != null) {
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
                ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));
                view.setSelection(adapter.getPosition(selected));
            }
        }
    }


    @Override
    public void attach(EntityLoaderTask task) {
        findViewById(R.id.entity_select_loading).setVisibility(View.VISIBLE);
        this.loader = task;
    }

    private void select() {
        // create intent for return and store path
        Intent i = new Intent(EntitySelectActivity.this.getIntent());
        i.putExtra(SessionFrame.STATE_DATUM_VAL, selectedIntent.getStringExtra(SessionFrame.STATE_DATUM_VAL));
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void callRequested(String phoneNumber) {
        DetailCalloutListenerDefaultImpl.callRequested(this, phoneNumber);
    }

    @Override
    public void addressRequested(String address) {
        DetailCalloutListenerDefaultImpl.addressRequested(this, address);
    }

    @Override
    public void playVideo(String videoRef) {
        DetailCalloutListenerDefaultImpl.playVideo(this, videoRef);
    }

    @Override
    public void performCallout(CalloutData callout, int id) {
        DetailCalloutListenerDefaultImpl.performCallout(this, callout, id);
    }

    private void displayReferenceAwesome(final TreeReference selection, int detailIndex) {
        selectedIntent = getDetailIntent(selection, getIntent());
        //this should be 100% "fragment" able
        if (!rightFrameSetup) {
            findViewById(R.id.screen_compound_select_prompt).setVisibility(View.GONE);
            View.inflate(this, R.layout.entity_detail, rightFrame);
            Button next = (Button)findViewById(R.id.entity_select_button);
            //use the old method here because some Android versions don't like Spannables for titles
            next.setText(Localization.get("select.detail.confirm"));
            next.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    select();
                }
            });

            if (mViewMode) {
                next.setVisibility(View.GONE);
                next.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }

            String passedCommand = selectedIntent.getStringExtra(SessionFrame.STATE_COMMAND_ID);

            if (passedCommand != null) {
                mViewMode = session.isViewCommand(passedCommand);
            } else {
                mViewMode = session.isViewCommand(session.getCommand());
            }

            detailView = (TabbedDetailView)rightFrame.findViewById(R.id.entity_detail_tabs);
            detailView.setRoot(detailView);

            factory = new NodeEntityFactory(session.getDetail(selectedIntent.getStringExtra(EntityDetailActivity.DETAIL_ID)), session.getEvaluationContext(new AndroidInstanceInitializer(session)));
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

    @Override
    public void deliverError(Exception e) {
        displayException(e);
    }


    @Override
    protected boolean onForwardSwipe() {
        // If user has picked an entity, move along to form entry
        if (selectedIntent != null) {
            if (inAwesomeMode &&
                    detailView != null &&
                    detailView.getCurrentTab() < detailView.getTabCount() - 1) {
                return false;
            }

            if (!mViewMode) {
                select();
            }
        }
        return true;
    }

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
        if (loader == null && !EntityLoaderTask.attachToActivity(this)) {
            EntityLoaderTask theloader = new EntityLoaderTask(shortSelect, asw.getEvaluationContext());
            theloader.attachListener(this);
            theloader.execute(selectDatum.getNodeset());
        }
    }

    private void startTimer() {
        if (!DeveloperPreferences.isListRefreshEnabled()) {
            return;
        }
        synchronized (timerLock) {
            if (myTimer == null) {
                myTimer = new Timer();
                myTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!cancelled) {
                                    triggerRebuild();
                                }
                            }
                        });
                    }
                }, 15 * 1000, 15 * 1000);
                cancelled = false;
            }
        }
    }

    private void stopTimer() {
        synchronized (timerLock) {
            if (myTimer != null) {
                myTimer.cancel();
                myTimer = null;
                cancelled = true;
            }
        }
    }
}
