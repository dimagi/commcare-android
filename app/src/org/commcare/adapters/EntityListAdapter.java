package org.commcare.adapters;

import static org.commcare.cases.util.StringUtils.normalize;

import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.dalvik.R;
import org.commcare.interfaces.AndroidSortableEntityAdapter;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.session.SessionInstanceBuilder;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Detail;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.CachingAsyncImageLoader;
import org.commcare.views.EntityActionViewUtils;
import org.commcare.views.EntityView;
import org.commcare.views.EntityViewTile;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.OrderedHashtable;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This adapter class handles displaying the cases for a CommCareODK user.
 * Depending on the <grid> block of the Detail this adapter is constructed with, cases might be
 * displayed as normal EntityViews or as AdvancedEntityViews
 *
 * @author ctsims
 * @author wspride
 */
public class EntityListAdapter extends AndroidSortableEntityAdapter implements ListAdapter {
    public static final int ENTITY_TYPE = 0;
    public static final int ACTION_TYPE = 1;
    public static final int DIVIDER_TYPE = 2;

    private int dividerPosition = 0;
    private final int actionsCount;
    private final int dividerCount;

    private boolean mFuzzySearchEnabled = true;
    private boolean isFilteringByCalloutResult = false;

    private final CommCareActivity commCareActivity;
    private final Detail detail;

    private final List<DataSetObserver> observers;

    private final List<Entity<TreeReference>> full;
    private List<Entity<TreeReference>> current;
    private final List<TreeReference> references;
    private final List<Action> actions;

    private TreeReference selected;

    private final NodeEntityFactory mNodeFactory;

    private String[] currentSearchTerms;
    private String searchQuery = "";

    private EntityFiltererBase entityFilterer = null;

    // Asyncronous image loader, allows rows with images to scroll smoothly
    private final CachingAsyncImageLoader mImageLoader;

    // false until we determine the Detail has at least one <grid> block
    private boolean usesCaseTiles = false;

    // key to data mapping used to attach callout results to individual entities
    private OrderedHashtable<String, String> calloutResponseData = new OrderedHashtable<>();

    private final boolean selectActivityInAwesomeMode;

    public EntityListAdapter(CommCareActivity activity, Detail detail,
                             List<TreeReference> references,
                             List<Entity<TreeReference>> full, NodeEntityFactory factory,
                             boolean hideActions, List<Action> actions, boolean inAwesomeMode) {
        super(full, detail, factory);
        this.detail = detail;
        this.selectActivityInAwesomeMode = inAwesomeMode;
        this.actions = actions;
        if (actions == null || actions.isEmpty() || hideActions) {
            actionsCount = 0;
            dividerCount = 0;
        } else {
            actionsCount = actions.size();
            dividerCount = 2;
        }

        this.full = full;
        this.references = references;
        this.commCareActivity = activity;
        this.observers = new ArrayList<>();
        this.mNodeFactory = factory;
        mImageLoader = new CachingAsyncImageLoader(commCareActivity);
        this.usesCaseTiles = detail.usesEntityTileView();
        this.mFuzzySearchEnabled = MainConfigurablePreferences.isFuzzySearchEnabled();

        setCurrent(new ArrayList<>(full));
    }

    /**
     * Set the current display set for this adapter
     */
    void setCurrent(List<Entity<TreeReference>> arrayList) {
        current = arrayList;
        if (actionsCount > 0) {
            dividerPosition = current.size();
        }
        update();
    }

    void clearSearch() {
        currentSearchTerms = null;
        searchQuery = "";
    }

    public void clearCalloutResponseData() {
        isFilteringByCalloutResult = false;
        setCurrent(full);
        calloutResponseData.clear();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    /**
     * Includes action, if enabled, as an item.
     */
    @Override
    public int getCount() {
        return getCurrentCountWithActions();
    }

    public int getFullCount() {
        return full.size();
    }

    public int getCurrentCount() {
        return current.size();
    }

    public int getCurrentCountWithActions() {
        return current.size() + actionsCount + dividerCount;
    }

    @Override
    public TreeReference getItem(int position) {
        return current.get(position).getElement();
    }

    @Override
    public long getItemId(int position) {
        int type = getItemViewType(position);
        switch (type) {
            case ENTITY_TYPE:
                return references.indexOf(current.get(position).getElement());
            case ACTION_TYPE:
                return dividerPosition + actions.indexOf(getAction(position));
            case DIVIDER_TYPE:
                return -2;
            default:
                throw new RuntimeException("Invalid view type");
        }
    }

    private Action getAction(int position) {
        int baseActionPosition = dividerPosition + 1;
        return actions.get(position - baseActionPosition);
    }

    @Override
    public int getItemViewType(int position) {
        if (actionsCount > 0) {
            if (position > dividerPosition && position != getCount() - 1) {
                return ACTION_TYPE;
            } else if (position == dividerPosition || position == getCount() - 1) {
                return DIVIDER_TYPE;
            }
        }
        return ENTITY_TYPE;
    }

    /**
     * Note that position gives a unique "row" id, EXCEPT that the header row AND the first content row
     * are both assigned position 0 -- this is not an issue for current usage, but it could be in future
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int type = getItemViewType(position);
        switch (type) {
            case ENTITY_TYPE:
                return getEntityView(position, convertView);
            case ACTION_TYPE:
                return getActionView(position, (FrameLayout)convertView, parent);
            case DIVIDER_TYPE:
                return getDividerView((LinearLayout)convertView, parent);
            default:
                throw new RuntimeException("Invalid view type");
        }
    }

    private View getEntityView(int position, View convertView) {
        Entity<TreeReference> entity = current.get(position);

        if (usesCaseTiles) {
            // if we use a <grid>, setup an AdvancedEntityView
            return getTileView(entity, (EntityViewTile)convertView);
        } else {
            return getListEntityView(entity, (EntityView)convertView, position);
        }
    }

    private View getTileView(Entity<TreeReference> entity, EntityViewTile tile) {
        int[] titleColor = AndroidUtil.getThemeColorIDs(commCareActivity, new int[]{R.attr.entity_select_title_text_color});
        if (tile == null) {
            tile = EntityViewTile.createTileForEntitySelectDisplay(commCareActivity, detail, entity,
                        currentSearchTerms, mImageLoader, mFuzzySearchEnabled, selectActivityInAwesomeMode);
        } else {
            tile.setSearchTerms(currentSearchTerms);
            tile.addFieldViews(commCareActivity, detail, entity);
        }
        tile.setTitleTextColor(titleColor[0]);
        return tile;
    }

    private View getListEntityView(Entity<TreeReference> entity, EntityView emv, int position) {
        if (emv == null) {
            emv = EntityView.buildEntryEntityView(
                    commCareActivity, detail, entity,
                    currentSearchTerms, position, mFuzzySearchEnabled,
                    getCalloutDataForEntity(entity));
        } else {
            emv.setSearchTerms(currentSearchTerms);
            if (detail.getCallout() != null) {
                emv.setExtraData(detail.getCallout().getResponseDetailField(), getCalloutDataForEntity(entity));
            }
            emv.refreshViewsForNewEntity(entity, entity.getElement().equals(selected), position);
        }
        return emv;
    }

    private String getCalloutDataForEntity(Entity<TreeReference> entity) {
        if (entity.extraKey != null) {
            return calloutResponseData.get(entity.extraKey);
        } else {
            return null;
        }
    }

    private View getActionView(int position, FrameLayout actionCardView, ViewGroup parent) {
        if (actionCardView == null) {
            actionCardView = (FrameLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.action_card, parent, false);
        }

        EntityActionViewUtils.buildActionView(actionCardView,
                getAction(position),
                commCareActivity);

        return actionCardView;
    }

    private LinearLayout getDividerView(LinearLayout convertView, ViewGroup parent) {
        if (convertView == null) {
            return (LinearLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.line_separator, parent, false);
        }
        convertView.setOnClickListener(null);
        convertView.setEnabled(false);
        convertView.setFocusable(false);
        return convertView;
    }

    @Override
    public int getViewTypeCount() {
        return (actionsCount > 0) ? 3 : 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return getCount() > 0;
    }

    public synchronized void filterByString(String filterRaw) {
        if (entityFilterer != null) {
            entityFilterer.cancelSearch();
        }
        // split by whitespace
        String[] searchTerms = filterRaw.split("\\s+");
        for (int i = 0; i < searchTerms.length; ++i) {
            searchTerms[i] = normalize(searchTerms[i]);
        }
        currentSearchTerms = searchTerms;
        searchQuery = filterRaw;
        entityFilterer =
                new EntityStringFilterer(this, searchTerms,
                        mFuzzySearchEnabled, mNodeFactory, full, commCareActivity);
        entityFilterer.start();
    }

    /**
     * Filter entity list to only include entities that have extra keys present
     * in the provided mapping.  Reorders entities by the key ordering of the
     * mapping.
     */
    public synchronized void filterByKeyedCalloutData(OrderedHashtable<String, String> keyToExtraDataMapping) {
        calloutResponseData = keyToExtraDataMapping;

        if (entityFilterer != null) {
            entityFilterer.cancelSearch();
        }
        LinkedHashSet<String> keysToFilterBy = new LinkedHashSet<>();
        for (Enumeration en = calloutResponseData.keys(); en.hasMoreElements(); ) {
            String key = (String)en.nextElement();
            keysToFilterBy.add(key);
        }

        isFilteringByCalloutResult = true;
        entityFilterer =
                new EntityKeyFilterer(this, mNodeFactory, full, commCareActivity, keysToFilterBy);
        entityFilterer.start();
    }

    void update() {
        for (DataSetObserver o : observers) {
            o.onChanged();
        }
    }

    public void sortEntities(int[] keys) {
        sort(keys);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        if (!observers.contains(observer)) {
            this.observers.add(observer);
        }
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        this.observers.remove(observer);
    }

    public void notifyCurrentlyHighlighted(TreeReference chosen) {
        this.selected = chosen;
        update();
    }

    public int getPosition(TreeReference chosen) {
        for (int i = 0; i < current.size(); ++i) {
            Entity<TreeReference> e = current.get(i);
            if (e.getElement().equals(chosen)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Signal that this adapter is dying. If we are doing any asynchronous work,
     * we need to stop doing so.
     */
    public synchronized void signalKilled() {
        if (entityFilterer != null) {
            entityFilterer.cancelSearch();
        }
    }

    public String getSearchNotificationText() {
        if (isFilteringByCalloutResult) {
            return Localization.get("select.callout.search.status", new String[]{
                    "" + getCurrentCount(),
                    "" + getFullCount()});
        } else {
            return Localization.get("select.search.status", new String[]{
                    "" + getCurrentCount(),
                    "" + getFullCount(),
                    searchQuery});
        }
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public boolean isFilteringByCalloutResult() {
        return isFilteringByCalloutResult;
    }

    public boolean hasCalloutResponseData() {
        return !calloutResponseData.isEmpty();
    }

    public void loadCalloutDataFromSession() {
        OrderedHashtable<String, String> externalData =
                (OrderedHashtable<String, String>)CommCareApplication.instance()
                        .getCurrentSession()
                        .getCurrentFrameStepExtra(SessionInstanceBuilder.KEY_ENTITY_LIST_EXTRA_DATA);
        if (externalData != null) {
            filterByKeyedCalloutData(externalData);
        }
    }

    public void saveCalloutDataToSession() {
        if (isFilteringByCalloutResult) {
            SessionWrapper session = CommCareApplication.instance().getCurrentSession();
            session.removeExtraFromCurrentFrameStep(SessionInstanceBuilder.KEY_ENTITY_LIST_EXTRA_DATA);
            session.addExtraToCurrentFrameStep(SessionInstanceBuilder.KEY_ENTITY_LIST_EXTRA_DATA,
                    calloutResponseData);
        }
    }

}
