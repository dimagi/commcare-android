package org.commcare.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.EntitySelectCalloutSetup;
import org.commcare.activities.components.EntitySelectViewSetup;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.dalvik.R;
import org.commcare.fragments.ContainerFragment;
import org.commcare.logic.DetailCalloutListenerDefaultImpl;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.provider.SimprintsCalloutProcessing;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.EntityDatum;
import org.commcare.tasks.EntityLoaderListener;
import org.commcare.tasks.EntityLoaderTask;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.DetailCalloutListener;
import org.commcare.utils.EntityDetailUtils;
import org.commcare.utils.EntitySelectRefreshTimer;
import org.commcare.utils.HereFunctionHandler;
import org.commcare.utils.SerializationUtil;
import org.commcare.views.EntityView;
import org.commcare.views.TabbedDetailView;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.LocationNotificationHandler;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.media.AudioController;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.OrderedHashtable;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.commcare.android.javarosa.IntentCallout;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ctsims
 */
public class EntitySelectActivity extends SaveSessionCommCareActivity
        implements TextWatcher, EntityLoaderListener,
        OnItemClickListener, DetailCalloutListener {
    private CommCareSession session;
    private AndroidSessionWrapper asw;

    public static final String EXTRA_ENTITY_KEY = "esa_entity_key";
    private static final String CONTAINS_HERE_FUNCTION = "contains_here_function";
    private static final String MAPPING_ENABLED = "map_view_enabled";
    private static final String LOCATION_CHANGED_WHILE_LOADING = "location_changed_while_loading";
    private static final String IS_AUTO_LAUNCHING = "is_auto_launching";

    private static final int CONFIRM_SELECT = 0;
    private static final int MAP_SELECT = 2;
    public static final int BARCODE_FETCH = 1;
    public static final int CALLOUT = 3;

    private static final int MENU_SORT = Menu.FIRST + 1;
    private static final int MENU_MAP = Menu.FIRST + 2;
    private static final int MENU_ACTION = Menu.FIRST + 3;

    private static final int MENU_ACTION_GROUP = Menu.FIRST + 1;

    private EditText searchbox;
    private TextView searchResultStatus;
    private ImageButton clearSearchButton;
    private View searchBanner;
    private EntityListAdapter adapter;
    private LinearLayout header;
    private SearchView searchView;
    private MenuItem searchItem;
    private MenuItem barcodeItem;

    private EntityDatum selectDatum;

    private boolean mResultIsMap = false;
    private boolean isMappingEnabled = false;

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
    private boolean isCalloutAutoLaunching;

    private boolean resuming = false;
    private boolean isStartingDetailActivity = false;

    private boolean rightFrameSetup = false;
    private NodeEntityFactory factory;

    private ContainerFragment<EntityListAdapter> containerFragment;
    private EntitySelectRefreshTimer refreshTimer;

    // Function handler for handling XPath evaluation of the function here().
    // Although only one instance is created, which is used by NodeEntityFactory,
    // every instance of EntitySelectActivity registers itself (one at a time)
    // to listen to the handler and refresh whenever a new location is obtained.
    private static final HereFunctionHandler hereFunctionHandler = new HereFunctionHandler();
    private boolean containsHereFunction = false;
    private boolean locationChangedWhileLoading = false;
    private boolean hideActions;

    // Handler for displaying alert dialog when no location providers are found
    private final LocationNotificationHandler locationNotificationHandler =
            new LocationNotificationHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createDataSetObserver();
        restoreSavedState(savedInstanceState);

        if (savedInstanceState == null) {
            hereFunctionHandler.refreshLocation();
        }

        refreshTimer = new EntitySelectRefreshTimer();
        asw = CommCareApplication._().getCurrentSessionWrapper();
        session = asw.getSession();

        // avoid session dependent when there is no command
        if (session.getCommand() != null) {
            selectDatum = (EntityDatum)session.getNeededDatum();
            shortSelect = session.getDetail(selectDatum.getShortDetail());
            mNoDetailMode = selectDatum.getLongDetail() == null;

            // Don't show actions (e.g. 'register patient', 'claim patient') when
            // in the middle on workflow triggered by an (sync) action.
            hideActions = session.isSyncCommand(session.getCommand());

            boolean isOrientationChange = savedInstanceState != null;
            setupUI(isOrientationChange);
        }
    }

    private void createDataSetObserver() {
        mListStateObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();

                setSearchBannerState();
            }
        };
    }

    private void setSearchBannerState() {
        if (!"".equals(adapter.getSearchQuery())) {
            showSearchBanner();
        } else if (adapter.isFilteringByCalloutResult()) {
            showSearchBannerWithClearButton();
        } else {
            searchBanner.setVisibility(View.GONE);
            clearSearchButton.setVisibility(View.GONE);
        }
    }

    private void showSearchBannerWithClearButton() {
        showSearchBanner();
        clearSearchButton.setVisibility(View.VISIBLE);
    }

    private void showSearchBanner() {
        searchResultStatus.setText(adapter.getSearchNotificationText());
        searchResultStatus.setVisibility(View.VISIBLE);
        searchBanner.setVisibility(View.VISIBLE);
    }

    private void restoreSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            isMappingEnabled = savedInstanceState.getBoolean(MAPPING_ENABLED);
            containsHereFunction = savedInstanceState.getBoolean(CONTAINS_HERE_FUNCTION);
            locationChangedWhileLoading =
                    savedInstanceState.getBoolean(LOCATION_CHANGED_WHILE_LOADING);
            isCalloutAutoLaunching = savedInstanceState.getBoolean(IS_AUTO_LAUNCHING);
        }
    }

    private void setupUI(boolean isOrientationChange) {
        if (this.getString(R.string.panes).equals("two") && !mNoDetailMode) {
            //See if we're on a big 'ol screen.
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setupLandscapeDualPaneView();
            } else {
                setContentView(R.layout.entity_select_layout);

                restoreExistingSelection(isOrientationChange);
            }
        } else {
            setContentView(R.layout.entity_select_layout);
        }

        ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));
        view.setOnItemClickListener(this);

        EntitySelectViewSetup.setupDivider(this, view, shortSelect.usesGridView());
        setupToolbar(view);
        setupMapNav();
    }

    private void setupLandscapeDualPaneView() {
        setContentView(R.layout.screen_compound_select);
        View.inflate(this, R.layout.entity_select_layout, (ViewGroup)findViewById(R.id.screen_compound_select_left_pane));
        inAwesomeMode = true;

        rightFrame = (FrameLayout)findViewById(R.id.screen_compound_select_right_pane);

        TextView message = (TextView)findViewById(R.id.screen_compound_select_prompt);
        //use the old method here because some Android versions don't like Spannables for titles
        message.setText(Localization.get("select.placeholder.message", new String[]{Localization.get("cchq.case")}));
    }

    private void restoreExistingSelection(boolean isOrientationChange) {
        // Restore detail screen for selection from landscape mode as we move into portrait mode.
        if (isOrientationChange) {
            Intent intent = this.getIntent();

            TreeReference selectedRef = SerializationUtil.deserializeFromIntent(intent,
                    EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
            if (selectedRef != null) {
                // remove the reference from this intent, ensuring we
                // don't re-launch the detail for an entity even after
                // it being de-selected.
                intent.removeExtra(EntityDetailActivity.CONTEXT_REFERENCE);

                // include selected entity in the launching detail intent
                Intent detailIntent = EntityDetailUtils.getDetailIntent(getApplicationContext(), selectedRef, null, selectDatum, asw);

                isStartingDetailActivity = true;
                startActivityForResult(detailIntent, CONFIRM_SELECT);
            }
        }
    }

    private void setupToolbar(ListView view) {
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
        searchBanner = findViewById(R.id.search_result_banner);
        searchResultStatus = (TextView)findViewById(R.id.search_results_status);
        clearSearchButton = (ImageButton)findViewById(R.id.clear_search_button);
        clearSearchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.clearCalloutResponseData();
                refreshView();
            }
        });
        clearSearchButton.setVisibility(View.GONE);
        header = (LinearLayout)findViewById(R.id.entity_select_header);

        mViewMode = session.isViewCommand(session.getCommand());

        ImageButton barcodeButton = (ImageButton)findViewById(R.id.barcodeButton);

        Callout callout = shortSelect.getCallout();
        if (callout == null) {
            barcodeScanOnClickListener = EntitySelectCalloutSetup.makeBarcodeClickListener(this);
        } else {
            isCalloutAutoLaunching = callout.isAutoLaunching();
            barcodeScanOnClickListener = EntitySelectCalloutSetup.makeCalloutClickListener(this, callout);
            if (callout.getImage() != null) {
                EntitySelectCalloutSetup.setupImageLayout(this, barcodeButton, callout.getImage());
            }
        }

        barcodeButton.setOnClickListener(barcodeScanOnClickListener);

        searchbox.addTextChangedListener(this);
        searchbox.requestFocus();

        persistAdapterState(view);

        restoreLastQueryString();

        if (!isUsingActionBar()) {
            searchbox.setText(lastQueryString);
        }
    }

    private void persistAdapterState(ListView view) {
        FragmentManager fm = this.getSupportFragmentManager();

        containerFragment = (ContainerFragment)fm.findFragmentByTag("entity-adapter");

        // stateHolder and its previous state aren't null if the activity is
        // being created due to an orientation change.
        if (containerFragment == null) {
            containerFragment = new ContainerFragment<>();
            fm.beginTransaction().add(containerFragment, "entity-adapter").commit();
        } else {
            adapter = containerFragment.getData();
            // on orientation change
            if (adapter != null) {
                setupUIFromAdapter(view);
            }
        }
    }

    private void setupUIFromAdapter(ListView view) {
        view.setAdapter(adapter);
        EntitySelectViewSetup.setupDivider(this, view, shortSelect.usesGridView());

        findViewById(R.id.entity_select_loading).setVisibility(View.GONE);

        setSearchBannerState();
    }

    private void setupMapNav() {
        for (DetailField field : shortSelect.getFields()) {
            if ("address".equals(field.getTemplateForm())) {
                isMappingEnabled = true;
                break;
            }
        }
    }

    @Override
    protected void onResumeSessionSafe() {
        if (!isFinishing() && !isStartingDetailActivity) {
            if (adapter != null) {
                adapter.registerDataSetObserver(mListStateObserver);
            }

            if (!resuming && !mNoDetailMode && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
                if (resumeSelectedEntity()) {
                    return;
                }
            }

            hereFunctionHandler.registerEvalLocationListener(this);
            if (containsHereFunction) {
                hereFunctionHandler.allowGpsUse();
            }

            refreshView();
            if (isCalloutAutoLaunching) {
                isCalloutAutoLaunching = false;
                barcodeScanOnClickListener.onClick(null);
            }
        }
    }

    private boolean resumeSelectedEntity() {
        TreeReference selectedEntity =
                selectDatum.getEntityFromID(asw.getEvaluationContext(),
                        this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));

        if (selectedEntity != null) {
            if (inAwesomeMode) {
                if (adapter != null) {
                    displayReferenceAwesome(selectedEntity, adapter.getPosition(selectedEntity));
                    updateSelectedItem(selectedEntity, true);
                }
            } else {
                //Once we've done the initial dispatch, we don't want to end up triggering it later.
                this.getIntent().removeExtra(EXTRA_ENTITY_KEY);

                Intent i = EntityDetailUtils.getDetailIntent(getApplicationContext(), selectedEntity, null, selectDatum, asw);
                if (adapter != null) {
                    i.putExtra("entity_detail_index", adapter.getPosition(selectedEntity));
                    i.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID,
                            selectDatum.getShortDetail());
                }
                startActivityForResult(i, CONFIRM_SELECT);
                return true;
            }
        }
        return false;
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        try {
            rebuildHeaders();

            if (adapter == null) {
                loadEntities();
            } else {
                refreshTimer.start(this);
            }
        } catch (RuntimeException re) {
            UserfacingErrorHandling.createErrorDialog(this, re.getMessage(), true);
        }
    }

    private void rebuildHeaders() {
        //TODO: Get ec into these text's
        String[] headers = new String[shortSelect.getFields().length];

        for (int i = 0; i < headers.length; ++i) {
            headers[i] = shortSelect.getFields()[i].getHeader().evaluate();
        }

        header.removeAllViews();

        // only add headers if we're not using grid mode
        if (!shortSelect.usesGridView()) {
            boolean hasCalloutResponseData = (adapter != null && adapter.hasCalloutResponseData());
            //Hm, sadly we possibly need to rebuild this each time.
            EntityView v =
                    EntityView.buildHeadersEntityView(this, shortSelect, headers, hasCalloutResponseData);
            header.addView(v);
        }
    }

    public boolean loadEntities() {
        if (adapter != null) {
            // Store extra data to be reloaded upon load task completion
            adapter.saveCalloutDataToSession();
        }

        if (loader == null && !EntityLoaderTask.attachToActivity(this)) {
            EntityLoaderTask entityLoader = new EntityLoaderTask(shortSelect, asw.getEvaluationContext());
            entityLoader.attachListener(this);
            entityLoader.executeParallel(selectDatum.getNodeset());
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(CONTAINS_HERE_FUNCTION, containsHereFunction);
        savedInstanceState.putBoolean(MAPPING_ENABLED, isMappingEnabled);
        savedInstanceState.putBoolean(LOCATION_CHANGED_WHILE_LOADING, locationChangedWhileLoading);
        savedInstanceState.putBoolean(IS_AUTO_LAUNCHING, isCalloutAutoLaunching);
    }

    @Override
    protected void onPause() {
        super.onPause();

        refreshTimer.stop();

        if (adapter != null) {
            adapter.unregisterDataSetObserver(mListStateObserver);
        }

        hereFunctionHandler.forbidGpsUse();
        hereFunctionHandler.unregisterEvalLocationListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        refreshTimer.stop();
        saveLastQueryString();
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
    }



    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
        if (id != EntityListAdapter.DIVIDER_TYPE && id != EntityListAdapter.ACTION_TYPE) {
            TreeReference selection = adapter.getItem(position);
            if (CommCarePreferences.isEntityDetailLoggingEnabled()) {
                Logger.log(EntityDetailActivity.class.getSimpleName(), selectDatum.getLongDetail());
            }
            if (inAwesomeMode) {
                displayReferenceAwesome(selection, position);
                updateSelectedItem(selection, false);
            } else {
                Intent i = EntityDetailUtils.getDetailIntent(getApplicationContext(),
                        selection, null, selectDatum, asw);
                i.putExtra("entity_detail_index", position);
                if (mNoDetailMode) {
                    // Not actually launching detail intent because there's no confirm detail available
                    returnWithResult(i);
                } else {
                    startActivityForResult(i, CONFIRM_SELECT);
                }
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
                isStartingDetailActivity = false;
                if (resultCode == RESULT_OK && !mViewMode) {
                    // create intent for return and store path
                    returnWithResult(intent);
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
                            this.displayReferenceAwesome(r, adapter.getPosition(r));
                            updateSelectedItem(r, true);
                        }
                        AudioController.INSTANCE.releaseCurrentMediaEntity();
                    }
                }
                break;
            case MAP_SELECT:
                if (resultCode == RESULT_OK) {
                    TreeReference r =
                            SerializationUtil.deserializeFromIntent(intent,
                                    EntityDetailActivity.CONTEXT_REFERENCE,
                                    TreeReference.class);

                    if (inAwesomeMode) {
                        this.displayReferenceAwesome(r, adapter.getPosition(r));
                    } else {
                        Intent i = EntityDetailUtils.getDetailIntent(getApplicationContext(),
                                r, null, selectDatum, asw);
                        if (mNoDetailMode) {
                            returnWithResult(i);
                        } else {
                            //To go back to map mode if confirm is false
                            mResultIsMap = true;
                            i.putExtra("entity_detail_index", adapter.getPosition(r));
                            startActivityForResult(i, CONFIRM_SELECT);
                        }
                    }
                } else {
                    refreshView();
                }
                break;
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
            if (intent.hasExtra(IntentCallout.INTENT_RESULT_VALUE)) {
                handleSearchStringCallout(intent);
            } else if (SimprintsCalloutProcessing.isIdentificationResponse(intent)) {
                handleFingerprintMatchCallout(intent);
            } else {
                Toast.makeText(this,
                        Localization.get("select.callout.search.invalid"),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleSearchStringCallout(Intent intent) {
        String result = intent.getStringExtra(IntentCallout.INTENT_RESULT_VALUE);
        if (result != null) {
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

    private void handleFingerprintMatchCallout(Intent intent) {
        OrderedHashtable<String, String> guidToMatchConfidenceMap =
                SimprintsCalloutProcessing.getConfidenceMatchesFromCalloutResponse(intent);
        adapter.filterByKeyedCalloutData(guidToMatchConfidenceMap);
        refreshView();
    }

    /**
     * Finish this activity, including all extras from the given intent in the finishing intent
     */
    private void returnWithResult(Intent intent) {
        if (adapter != null) {
            adapter.saveCalloutDataToSession();
        }
        Intent i = new Intent(this.getIntent());
        i.putExtras(intent.getExtras());
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void afterTextChanged(Editable incomingEditable) {
        final String incomingString = incomingEditable.toString();
        final String currentSearchText = getSearchText().toString();
        if (!"".equals(currentSearchText) && incomingString.equals(currentSearchText)) {
            filterString = currentSearchText;
            if (adapter != null) {
                adapter.filterByString(filterString);
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
        if (isMappingEnabled) {
            menu.add(0, MENU_MAP, MENU_MAP, Localization.get("select.menu.map")).setIcon(
                    android.R.drawable.ic_menu_mapmode);
        }

        tryToAddActionSearchBar(this, menu, new ActionBarInstantiator() {
            // again, this should be unnecessary...
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onActionBarFound(MenuItem searchItem, SearchView searchView, MenuItem barcodeItem) {
                EntitySelectActivity.this.searchItem = searchItem;
                EntitySelectActivity.this.searchView = searchView;
                EntitySelectActivity.this.barcodeItem = barcodeItem;
                // restore last query string in the searchView if there is one
                if (lastQueryString != null && lastQueryString.length() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        searchItem.expandActionView();
                    }
                    searchView.setQuery(lastQueryString, false);
                    if (adapter != null) {
                        adapter.filterByString(lastQueryString == null ? "" : lastQueryString);
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
                        filterString = newText;
                        if (adapter != null) {
                            adapter.filterByString(newText);
                        }
                        return false;
                    }
                });
            }
        });

        setupActionOptionsMenu(menu);

        return true;
    }

    private void setupActionOptionsMenu(Menu menu) {
        if (shortSelect != null && !hideActions) {
            int actionIndex = MENU_ACTION;
            for (Action action : shortSelect.getCustomActions()) {
                if (action != null) {
                    ViewUtil.addDisplayToMenu(this, menu, actionIndex, MENU_ACTION_GROUP,
                            action.getDisplay().evaluate());
                    actionIndex += 1;
                }
            }
            if (shortSelect.getCallout() != null && shortSelect.getCallout().getImage() != null) {
                EntitySelectCalloutSetup.setupImageLayout(this, barcodeItem, shortSelect.getCallout().getImage());
            }
        }
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
        // only enable sorting once entity loading is complete
        menu.findItem(MENU_SORT).setEnabled(adapter != null);
        // hide sorting menu when using async loading strategy
        menu.findItem(MENU_SORT).setVisible((shortSelect == null || !shortSelect.useAsyncStrategy()));
        menu.findItem(R.id.menu_settings).setVisible(!CommCareApplication._().isConsumerApp());

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getGroupId() == MENU_ACTION_GROUP) {
            triggerDetailAction(item.getItemId() - MENU_ACTION);
        }
        switch (item.getItemId()) {
            case MENU_SORT:
                createSortMenu();
                return true;
            case MENU_MAP:
                Intent i = new Intent(this, EntityMapActivity.class);
                this.startActivityForResult(i, MAP_SELECT);
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

    private void triggerDetailAction(int index) {
        Action action = shortSelect.getCustomActions().get(index);

        triggerDetailAction(action, this);
    }

    public static void triggerDetailAction(Action action, CommCareActivity activity) {
        try {
            CommCareApplication._().getCurrentSessionWrapper().executeStackActions(action.getStackOperations());
        } catch (XPathTypeMismatchException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(activity, e, true);
            return;
        }

        activity.setResult(CommCareHomeActivity.RESULT_RESTART);
        activity.finish();
    }

    private void createSortMenu() {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this, Localization.get("select.menu.sort"));
        dialog.setChoiceItems(getSortOptionsList(dialog));
        showAlertDialog(dialog);
    }

    private DialogChoiceItem[] getSortOptionsList(final PaneledChoiceDialog dialog) {
        DetailField[] fields = session.getDetail(selectDatum.getShortDetail()).getFields();
        List<String> namesList = new ArrayList<>();

        final int[] keyArray = new int[fields.length];
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
                keyArray[added] = i;
                added++;
            }
        }

        DialogChoiceItem[] choiceItems = new DialogChoiceItem[namesList.size()];
        for (int i = 0; i < namesList.size(); i++) {
            final int index = i;
            View.OnClickListener listener = new View.OnClickListener() {
                public void onClick(View v) {
                    adapter.sortEntities(new int[]{keyArray[index]});
                    adapter.filterByString(getSearchText().toString());
                    dismissAlertDialog();
                }
            };
            DialogChoiceItem item = new DialogChoiceItem(namesList.get(i), -1, listener);
            choiceItems[i] = item;
        }
        return choiceItems;
    }

    @Override
    public void deliverLoadResult(List<Entity<TreeReference>> entities,
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

        EntitySelectViewSetup.setupDivider(this, view, shortSelect.usesGridView());

        adapter = new EntityListAdapter(this, detail, references, entities, order, factory, hideActions);

        view.setAdapter(adapter);
        adapter.registerDataSetObserver(this.mListStateObserver);
        containerFragment.setData(adapter);

        // Pre-select entity if one was provided in original intent
        if (!resuming && !mNoDetailMode && inAwesomeMode && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
            TreeReference entity =
                    selectDatum.getEntityFromID(asw.getEvaluationContext(),
                            this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));

            if (entity != null) {
                displayReferenceAwesome(entity, adapter.getPosition(entity));
                updateSelectedItem(entity, true);
            }
        }

        findViewById(R.id.entity_select_loading).setVisibility(View.GONE);

        if (adapter != null) {
            // filter by additional session data (search string, callout result data)
            // Relevant when user navigates so far forward in the session that
            // the entity list needs to be reloaded upon returning to it
            restoreAdapterStateFromSession();
        }

        //In landscape we want to select something now. Either the top item, or the most recently selected one
        if (inAwesomeMode) {
            updateSelectedItem(true);
        }

        refreshTimer.start(this);

        if (locationChangedWhileLoading) {
            Log.i("HereFunctionHandler", "location changed while reloading");
            locationChangedWhileLoading = false;
            loadEntities();
        }
    }

    private void restoreAdapterStateFromSession() {
        if (filterString != null && !"".equals(filterString)) {
            adapter.filterByString(filterString);
        }

        adapter.loadCalloutDataFromSession();
    }

    private void updateSelectedItem(boolean forceMove) {
        TreeReference chosen = null;
        if (selectedIntent != null) {
            chosen = SerializationUtil.deserializeFromIntent(selectedIntent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
        }
        updateSelectedItem(chosen, forceMove);
    }

    private void updateSelectedItem(TreeReference selected, boolean forceMove) {
        if (adapter != null && selected != null) {
            adapter.notifyCurrentlyHighlighted(selected);
            if (forceMove) {
                ListView view = ((ListView)this.findViewById(R.id.screen_entity_select_list));
                view.setSelection(adapter.getPosition(selected));
            }
        }
    }

    @Override
    public void attachLoader(EntityLoaderTask task) {
        findViewById(R.id.entity_select_loading).setVisibility(View.VISIBLE);
        this.loader = task;
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
        selectedIntent = EntityDetailUtils.getDetailIntent(getApplicationContext(),
                selection, getIntent(), selectDatum, asw);
        //this should be 100% "fragment" able
        if (!rightFrameSetup) {
            findViewById(R.id.screen_compound_select_prompt).setVisibility(View.GONE);
            View.inflate(this, R.layout.entity_detail, rightFrame);
            Button next = (Button)findViewById(R.id.entity_select_button);
            //use the old method here because some Android versions don't like Spannables for titles
            next.setText(Localization.get("select.detail.confirm"));
            next.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    performEntitySelect();
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
            detailView.showMenu();

            if (detail.isCompound()) {
                // border around right panel doesn't look right when there are tabs
                rightFrame.setBackgroundDrawable(null);
            }

            rightFrameSetup = true;
        }

        detailView.refresh(factory.getDetail(), selection, detailIndex);
    }

    private void performEntitySelect() {
        if (adapter != null) {
            adapter.saveCalloutDataToSession();
        }
        Intent i = new Intent(EntitySelectActivity.this.getIntent());
        i.putExtra(SessionFrame.STATE_DATUM_VAL, selectedIntent.getStringExtra(SessionFrame.STATE_DATUM_VAL));
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void deliverLoadError(Exception e) {
        displayCaseListFilterException(e);
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
                performEntitySelect();
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

    public void onEvalLocationChanged() {
        boolean loaded = loadEntities();
        if (!loaded) {
            locationChangedWhileLoading = true;
        }
    }

    public static HereFunctionHandler getHereFunctionHandler() {
        return hereFunctionHandler;
    }

    public void onHereFunctionEvaluated() {
        if (!containsHereFunction) {  // First time here() is evaluated
            hereFunctionHandler.refreshLocation();
            hereFunctionHandler.allowGpsUse();
            containsHereFunction = true;

            if (!hereFunctionHandler.locationProvidersFound()) {
                locationNotificationHandler.sendEmptyMessage(0);
            }
        }
    }

    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }

    @Override
    public String getActivityTitle() {
        return null;
    }
}
