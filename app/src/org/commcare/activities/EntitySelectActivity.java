package org.commcare.activities;

import static org.commcare.activities.HomeScreenBaseActivity.RESULT_RESTART;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.jakewharton.rxbinding2.widget.AdapterViewItemClickEvent;
import com.jakewharton.rxbinding2.widget.RxAdapterView;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.EntitySelectCalloutSetup;
import org.commcare.activities.components.EntitySelectViewSetup;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.EntityLoadingProgressListener;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.dalvik.R;
import org.commcare.fragments.ContainerFragment;
import org.commcare.gis.EntityMapActivity;
import org.commcare.gis.EntityMapboxActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.provider.IdentityCalloutHandler;
import org.commcare.provider.SimprintsCalloutProcessing;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.EntityDatum;
import org.commcare.tasks.EntityLoaderListener;
import org.commcare.tasks.EntityLoaderTask;
import org.commcare.tasks.PrimeEntityCacheHelper;
import org.commcare.utils.AndroidHereFunctionHandler;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.EntityDetailUtils;
import org.commcare.utils.EntitySelectRefreshTimer;
import org.commcare.utils.SerializationUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.EntityView;
import org.commcare.views.TabbedDetailView;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.LocationNotificationHandler;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.media.AudioController;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.HereFunctionHandlerListener;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.OrderedHashtable;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;

/**
 * @author ctsims
 */
public class EntitySelectActivity extends SaveSessionCommCareActivity
        implements EntityLoaderListener, HereFunctionHandlerListener {
    private SessionWrapper session;
    private AndroidSessionWrapper asw;

    private static final String ICDS_DOMAIN_NAME = "icds-cas.commcarehq.org";

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

    private static final int CLICK_DEBOUNCE_TIME = 500;

    private EntityListAdapter adapter;
    private LinearLayout header;

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
    private boolean hideActionsFromOptionsMenu;
    private boolean hideActionsFromEntityList;

    private EntitySelectSearchUI entitySelectSearchUI;

    private Detail shortSelect;
    private Callout customCallout;

    private DataSetObserver mListStateObserver;
    public OnClickListener barcodeScanOnClickListener;
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
    private static final AndroidHereFunctionHandler hereFunctionHandler = new AndroidHereFunctionHandler();

    private boolean containsHereFunction = false;
    private boolean locationChangedWhileLoading = false;

    // Handler for displaying alert dialog when no location providers are found
    private final LocationNotificationHandler locationNotificationHandler =
            new LocationNotificationHandler(this);
    private AdapterView visibleView;
    private TextView progressTv;
    private int lastProgress = 0;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        createDataSetObserver();
        restoreSavedState(savedInstanceState);

        if (savedInstanceState == null) {
            hereFunctionHandler.clearLocation();
        }

        refreshTimer = new EntitySelectRefreshTimer();
        asw = CommCareApplication.instance().getCurrentSessionWrapper();
        session = asw.getSession();

        if (session.getCommand() == null) {
            // Avoid session-dependent setup if the session has ended, and we'll finish in onResume
            return;
        }

        selectDatum = (EntityDatum)session.getNeededDatum();
        shortSelect = session.getDetail(selectDatum.getShortDetail());
        if (shortSelect.forcesLandscape()) {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        this.customCallout = initCustomCallout();

        mNoDetailMode = selectDatum.getLongDetail() == null;
        mViewMode = session.isViewCommand(session.getCommand());

        // Don't show actions (e.g. 'register patient', 'claim patient') at all  when in the
        // middle of workflow triggered by a (sync) action. Also hide them from the entity
        // list (but not the options menu) when we are showing the entity list in grid mode
        hideActionsFromEntityList = session.isRemoteRequestCommand(session.getCommand()) ||
                shortSelect.shouldBeLaidOutInGrid();
        hideActionsFromOptionsMenu = session.isRemoteRequestCommand(session.getCommand());

        boolean isOrientationChange = savedInstanceState != null;
        setupUI(isOrientationChange);

        // On some devices, onCreateOptionsMenu() can get called before onCreate() has completed
        // if the action bar is in use. Since we can't deploy all of the logic in onCreateOptionsMenu
        // until this has happened, we should try to do it again when onCreate is complete
        invalidateOptionsMenu();
    }

    private Callout initCustomCallout() {
        Callout customCallout = shortSelect.getCallout();
        if (customCallout != null && ICDS_DOMAIN_NAME.equals(ReportingUtils.getDomain())) {
            customCallout.setAssumePlainTextValues();
        }
        return customCallout;
    }

    private void createDataSetObserver() {
        mListStateObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();

                entitySelectSearchUI.setSearchBannerState();
            }
        };
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
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setupLandscapeDualPaneView();
            } else {
                setContentView(R.layout.entity_select_layout);
                restoreExistingSelection(isOrientationChange);
            }
        } else {
            setContentView(R.layout.entity_select_layout);
        }

        GridView gridView = this.findViewById(R.id.screen_entity_select_grid);
        ListView listView = this.findViewById(R.id.screen_entity_select_list);
        if (shortSelect.shouldBeLaidOutInGrid()) {
            visibleView = gridView;
            gridView.setVisibility(View.VISIBLE);
            gridView.setNumColumns(shortSelect.getNumEntitiesToDisplayPerRow());
            listView.setVisibility(View.GONE);
        } else {
            visibleView = listView;
            listView.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
            EntitySelectViewSetup.setupDivider(this, listView, shortSelect.usesEntityTileView());
        }
        progressTv = findViewById(R.id.progress_text);
        RxAdapterView.itemClickEvents(visibleView)
                .subscribeOn(AndroidSchedulers.mainThread())
                .throttleFirst(CLICK_DEBOUNCE_TIME, TimeUnit.MILLISECONDS)
                .subscribe((Consumer<AdapterViewItemClickEvent>)clickEvent ->
                        onEntitySelected(clickEvent.position()));

        header = findViewById(R.id.entity_select_header);
        entitySelectSearchUI = new EntitySelectSearchUI(this);
        restoreLastQueryString();
        persistAdapterState(visibleView);
        setUpCalloutClickListener();
        setupMapNav();
    }

    private void setUpCalloutClickListener() {
        if (this.customCallout == null) {
            barcodeScanOnClickListener = EntitySelectCalloutSetup.makeBarcodeClickListener(this);
        } else {
            isCalloutAutoLaunching = this.customCallout.isAutoLaunching();
            barcodeScanOnClickListener =
                    EntitySelectCalloutSetup.makeCalloutClickListener(this, customCallout,
                            evalContext());
        }
    }

    private void setupLandscapeDualPaneView() {
        setContentView(R.layout.screen_compound_select);
        View.inflate(this, R.layout.entity_select_layout, findViewById(R.id.screen_compound_select_left_pane));
        inAwesomeMode = true;

        rightFrame = findViewById(R.id.screen_compound_select_right_pane);

        TextView message = findViewById(R.id.screen_compound_select_prompt);
        //use the old method here because some Android versions don't like Spannables for titles
        message.setText(Localization.get("select.placeholder.message", new String[]{Localization.get("cchq.case")}));
    }

    @Override
    public boolean shouldListenToSyncComplete() {
        return true;
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

    private void persistAdapterState(AdapterView view) {
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

    private void setupUIFromAdapter(AdapterView view) {
        view.setAdapter(adapter);
        if (view instanceof ListView) {
            EntitySelectViewSetup.setupDivider(this, (ListView)view, shortSelect.usesEntityTileView());
        }
        findViewById(R.id.progress_container).setVisibility(View.GONE);
        entitySelectSearchUI.setSearchBannerState();
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
    protected void onStart() {
        super.onStart();
        hereFunctionHandler.registerListener(this);
    }

    @Override
    public void onResumeSessionSafe() {
        if (session.getCommand() == null) {
            Intent i = new Intent(this.getIntent());
            setResult(RESULT_CANCELED, i);
            finish();
        } else if (!isFinishing() && !isStartingDetailActivity) {
            if (adapter != null) {
                adapter.registerDataSetObserver(mListStateObserver);
            }

            if (!resuming && !mNoDetailMode && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
                if (resumeSelectedEntity()) {
                    return;
                }
            }

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
                selectDatum.getEntityFromID(evalContext(),
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
    protected void refreshView() {
        try {
            rebuildHeaders();

            if (adapter == null) {
                loadEntities();
            } else {
                refreshTimer.start(this);
            }
        } catch (RuntimeException re) {
            new UserfacingErrorHandling<>().createErrorDialog(this, re.getMessage(), true);
        }
    }

    private void rebuildHeaders() {
        //TODO: Get ec into these text's
        String[] headers = new String[shortSelect.getFields().length];

        for (int i = 0; i < headers.length; ++i) {
            headers[i] = shortSelect.getFields()[i].getHeader().evaluate();
        }

        header.removeAllViews();

        // only add headers if we're not using case tiles
        if (!shortSelect.usesEntityTileView()) {
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
            setProgressText(StringUtils.getStringRobust(this, R.string.entity_list_initializing));
            EntityLoaderTask entityLoader = new EntityLoaderTask(shortSelect, selectDatum, evalContext());
            entityLoader.attachListener(this);
            entityLoader.executeParallel(selectDatum.getNodeset());
            observePrimeCacheWorker();
            return true;
        }
        return false;
    }

    private void observePrimeCacheWorker() {
        if (shortSelect.shouldOptimize()) {
            PrimeEntityCacheHelper primeEntityCacheHelper =
                    CommCareApplication.instance().getCurrentApp().getPrimeEntityCacheHelper();
            primeEntityCacheHelper.getProgressState().observe(this, triple -> {
                if (triple != null && triple.getFirst().contentEquals(selectDatum.getDataId()) &&
                        triple.getSecond().contentEquals(shortSelect.getId())) {
                    deliverProgress(triple.getThird());
                }
            });
        }
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

        if (refreshTimer != null) {
            refreshTimer.stop();
        }

        if (adapter != null) {
            adapter.unregisterDataSetObserver(mListStateObserver);
        }

        hereFunctionHandler.forbidGpsUse();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        saveLastQueryString();
        hereFunctionHandler.unregisterListener();
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

    public void onEntitySelected(int itemPosition) {
        if (adapter.getItemViewType(itemPosition) == EntityListAdapter.ENTITY_TYPE) {
            TreeReference selection = adapter.getItem(itemPosition);
            if (HiddenPreferences.isEntityDetailLoggingEnabled()) {
                Logger.log(EntityDetailActivity.class.getSimpleName(), selectDatum.getLongDetail());
            }
            try {
                if (inAwesomeMode) {
                    displayReferenceAwesome(selection, itemPosition);
                    updateSelectedItem(selection, false);
                } else {
                    Intent i = EntityDetailUtils.getDetailIntent(getApplicationContext(),
                            selection, null, selectDatum, asw);
                    i.putExtra("entity_detail_index", itemPosition);
                    if (mNoDetailMode) {
                        // Not actually launching detail intent because there's no confirm detail available
                        returnWithResult(i);
                    } else {
                        startActivityForResult(i, CONFIRM_SELECT);
                    }
                }
            } catch (XPathException e) {
                new UserfacingErrorHandling<>().logErrorAndShowDialog(this, e, true);
            }
        }
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
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
                } else if (resultCode == RESULT_RESTART) {
                    this.setResult(RESULT_RESTART, intent);
                    this.finish();
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
        }
    }

    private void processBarcodeFetch(int resultCode, Intent intent) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            String result = intent.getStringExtra("SCAN_RESULT");
            if (result != null) {
                result = result.trim();
            }
            entitySelectSearchUI.setSearchText(result);
        }
    }

    private void processCalloutResult(int resultCode, Intent intent) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            if (intent.hasExtra(IntentCallout.INTENT_RESULT_VALUE)) {
                handleSearchStringCallout(intent);
            } else if (IdentityCalloutHandler.isIdentificationResponse(intent)) {
                handleFingerprintMatchCallout(intent, IdentityCalloutHandler.GENERALIZED_IDENTITY_PROVIDER);
            } else if (SimprintsCalloutProcessing.isIdentificationResponse(intent)) {
                handleFingerprintMatchCallout(intent, SimprintsCalloutProcessing.SIMPRINTS_IDENTITY_PROVIDER);
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
            entitySelectSearchUI.setSearchText(result.trim());
        } else {
            for (String key : this.customCallout.getResponses()) {
                result = intent.getExtras().getString(key);
                if (result != null) {
                    entitySelectSearchUI.setSearchText(result);
                    return;
                }
            }
        }
    }

    private void handleFingerprintMatchCallout(
            Intent intent,
            @IdentityCalloutHandler.IdentityProvider String identityProvider) {

        OrderedHashtable<String, String> guidToMatchConfidenceMap = null;
        if (identityProvider.contentEquals(SimprintsCalloutProcessing.SIMPRINTS_IDENTITY_PROVIDER)) {
            guidToMatchConfidenceMap = SimprintsCalloutProcessing.getConfidenceMatchesFromCalloutResponse(intent);
        } else if (identityProvider.contentEquals(IdentityCalloutHandler.GENERALIZED_IDENTITY_PROVIDER)) {
            guidToMatchConfidenceMap = IdentityCalloutHandler.getConfidenceMatchesFromCalloutResponse(intent);
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_SORT, MENU_SORT, Localization.get("select.menu.sort")).setIcon(
                android.R.drawable.ic_menu_sort_alphabetically);
        if (isMappingEnabled) {
            menu.add(0, MENU_MAP, MENU_MAP, Localization.get("select.menu.map")).setIcon(
                    android.R.drawable.ic_menu_mapmode);
        }

        if (entitySelectSearchUI != null) {
            // Only execute this portion if setupUI() has completed; the presence of the action bar
            // can sometimes cause this method to get called before onCreate() has completed
            tryToAddSearchActionToAppBar(this, menu, entitySelectSearchUI.getActionBarInstantiator());
            setupActionOptionsMenu(menu);
        }

        MenuItem settingsItem = menu.findItem(R.id.menu_settings);
        if (settingsItem != null) {
            settingsItem.setTitle(Localization.get("select.menu.settings"));
        }

        return true;
    }

    private void setupActionOptionsMenu(Menu menu) {
        if (shortSelect != null && !hideActionsFromOptionsMenu) {
            int indexToAddActionAt = MENU_ACTION;
            for (Action action : shortSelect.getCustomActions(evalContext())) {
                if (action != null) {
                    ViewUtil.addActionToMenu(this, action, menu, indexToAddActionAt, MENU_ACTION_GROUP);
                    indexToAddActionAt += 1;
                }
            }
            entitySelectSearchUI.setupActionImage(this.customCallout);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // only enable sorting once entity loading is complete
        menu.findItem(MENU_SORT).setEnabled(adapter != null);
        // hide sorting menu when using async loading strategy
        menu.findItem(MENU_SORT).setVisible((shortSelect == null || shortSelect.hasSortField()));

        if (menu.findItem(R.id.menu_settings) != null) {
            // For the same reason as in onCreateOptionsMenu(), we may be trying to call this
            // before we're ready
            menu.findItem(R.id.menu_settings).setVisible(!CommCareApplication.instance().isConsumerApp());
        }

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
                Intent i = new Intent(this,
                        HiddenPreferences.shouldUseMapboxMap() ? EntityMapboxActivity.class : EntityMapActivity.class);
                this.startActivityForResult(i, MAP_SELECT);
                return true;
            // handling click on the barcode scanner's actionbar
            // trying to set the onclicklistener in its view in the onCreateOptionsMenu method does not work because it returns null
            case R.id.barcode_scan_action_bar:
                barcodeScanOnClickListener.onClick(null);
                return true;
            // this is needed because superclasses do not implement the menu_settings click
            case R.id.menu_settings:
                HomeScreenBaseActivity.createPreferencesMenu(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void triggerDetailAction(int index) {
        Action action = shortSelect.getCustomActions(evalContext()).get(index);

        triggerDetailAction(action, this);
    }

    public static void triggerDetailAction(Action action, CommCareActivity activity) {
        try {
            CommCareApplication.instance().getCurrentSessionWrapper().executeStackActions(action.getStackOperations());
        } catch (XPathTypeMismatchException e) {
            new UserfacingErrorHandling<>().logErrorAndShowDialog(activity, e, true);
            return;
        }

        activity.setResult(HomeScreenBaseActivity.RESULT_RESTART);
        activity.finish();
    }

    private void createSortMenu() {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this, Localization.get("select.menu.sort"));
        dialog.setChoiceItems(getSortOptionsList());
        showAlertDialog(dialog);
    }

    private DialogChoiceItem[] getSortOptionsList() {
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
            View.OnClickListener listener = v -> {
                adapter.sortEntities(new int[]{keyArray[index]});
                adapter.filterByString(entitySelectSearchUI.getSearchText().toString());
                dismissAlertDialog();
            };
            DialogChoiceItem item = new DialogChoiceItem(namesList.get(i), -1, listener);
            choiceItems[i] = item;
        }
        return choiceItems;
    }

    @Override
    public void deliverLoadResult(List<Entity<TreeReference>> entities,
                                  List<TreeReference> references,
                                  NodeEntityFactory factory, int focusTargetIndex) {
        loader = null;
        setProgressText(StringUtils.getStringRobust(this, R.string.entity_list_finishing));
        adapter = new EntityListAdapter(this, shortSelect, references, entities, factory,
                hideActionsFromEntityList, shortSelect.getCustomActions(evalContext()), inAwesomeMode);
        visibleView.setAdapter(adapter);
        adapter.registerDataSetObserver(this.mListStateObserver);
        containerFragment.setData(adapter);

        if (entitySelectSearchUI != null) {
            entitySelectSearchUI.restoreSearchString();
        }

        // Pre-select entity if one was provided in original intent
        if (!resuming && !mNoDetailMode && inAwesomeMode && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
            TreeReference entity = selectDatum.getEntityFromID(evalContext(),
                    this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));

            if (entity != null) {
                displayReferenceAwesome(entity, adapter.getPosition(entity));
                updateSelectedItem(entity, true);
            }
        }

        findViewById(R.id.progress_container).setVisibility(View.GONE);

        if (adapter != null) {
            // filter by additional session data (search string, callout result data)
            // Relevant when user navigates so far forward in the session that
            // the entity list needs to be reloaded upon returning to it
            restoreAdapterStateFromSession();
        }

        if (inAwesomeMode) {
            updateSelectedItem(true);
        } else if (focusTargetIndex != -1) {
            visibleView.setSelection(focusTargetIndex);
        }

        refreshTimer.start(this);

        if (locationChangedWhileLoading) {
            Log.i("HereFunctionHandler", "location changed while reloading");
            locationChangedWhileLoading = false;
            loadEntities();
        }
    }

    private void setProgressText(String message) {
        progressTv.setText(message);
    }

    private void restoreAdapterStateFromSession() {
        entitySelectSearchUI.restoreSearchString();

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
                ListView view = this.findViewById(R.id.screen_entity_select_list);
                view.setSelection(adapter.getPosition(selected));
            }
        }
    }

    @Override
    public void attachLoader(EntityLoaderTask task) {
        findViewById(R.id.progress_container).setVisibility(View.VISIBLE);
        this.loader = task;
    }

    private void displayReferenceAwesome(final TreeReference selection, int detailIndex) {
        selectedIntent = EntityDetailUtils.getDetailIntent(getApplicationContext(),
                selection, getIntent(), selectDatum, asw);
        //this should be 100% "fragment" able
        if (!rightFrameSetup) {
            findViewById(R.id.screen_compound_select_prompt).setVisibility(View.GONE);
            View.inflate(this, R.layout.entity_detail, rightFrame);
            Button next = findViewById(R.id.entity_select_button);
            //use the old method here because some Android versions don't like Spannables for titles
            next.setText(Localization.get("select.detail.confirm"));
            next.setOnClickListener(v -> performEntitySelect());

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

            detailView = rightFrame.findViewById(R.id.entity_detail_tabs);
            detailView.setRoot(detailView);

            Detail detail = session.getDetail(selectedIntent.getStringExtra(EntityDetailActivity.DETAIL_ID));
            factory = new NodeEntityFactory(detail, session.getEvaluationContext(new AndroidInstanceInitializer(session)));

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
        displayCaseListLoadException(e);
    }

    @Override
    public void deliverProgress(Integer[] values) {
        EntityLoadingProgressListener.EntityLoadingProgressPhase phase =
                EntityLoadingProgressListener.EntityLoadingProgressPhase.fromInt(values[0]);
        int phaseProgress = values[1] * 100 / values[2];
        int totalPhases = 1;
        if(shortSelect.isCacheEnabled()){
            // with caching we deliver progress in 3 separate phases
            totalPhases = 3;
        }
        int weightedProgress = (phase.getValue() - 1) * 100 / totalPhases + phaseProgress / totalPhases;
        if (weightedProgress != lastProgress) {
            lastProgress = weightedProgress;
            String progressDisplay = weightedProgress + "%";
            setProgressText(
                    StringUtils.getStringRobust(this, R.string.entity_list_loading,
                            new String[]{progressDisplay}));
        }
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

    public static AndroidHereFunctionHandler getHereFunctionHandler() {
        return hereFunctionHandler;
    }

    @Override
    public void onEvalLocationChanged() {
        boolean loaded = loadEntities();
        if (!loaded) {
            locationChangedWhileLoading = true;
        }
    }

    @Override
    public void onHereFunctionEvaluated() {
        if (!containsHereFunction) {  // First time here() is evaluated
            hereFunctionHandler.clearLocation();
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

    protected EntityListAdapter getAdapter() {
        return adapter;
    }

    public EvaluationContext evalContext() {
        return asw.getEvaluationContext();
    }
}
