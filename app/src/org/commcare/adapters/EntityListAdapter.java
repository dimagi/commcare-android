package org.commcare.adapters;

import android.app.Activity;
import android.database.DataSetObserver;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.models.AsyncNodeEntityFactory;
import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Detail;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.CachingAsyncImageLoader;
import org.commcare.utils.StringUtils;
import org.commcare.views.EntityView;
import org.commcare.views.GridEntityView;
import org.commcare.views.HorizontalMediaView;
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
public class EntityListAdapter implements ListAdapter {
    private static final String KEY_ENTITY_LIST_EXTRA_DATA = "entity-list-data";

    public static final int SPECIAL_ACTION = -2;

    private int actionsStartPosition = 0;
    private final int actionsCount;

    private boolean mFuzzySearchEnabled = true;
    private boolean isFilteringByCalloutResult = false;

    private final Activity context;
    private final Detail detail;

    private final List<DataSetObserver> observers;

    private final List<Entity<TreeReference>> full;
    private List<Entity<TreeReference>> current;
    private final List<TreeReference> references;

    private TreeReference selected;

    private int[] currentSort = {};
    private boolean reverseSort = false;

    private final NodeEntityFactory mNodeFactory;
    private boolean mAsyncMode = false;

    private String[] currentSearchTerms;
    private String searchQuery = "";

    private EntityFiltererBase entityFilterer = null;

    // Asyncronous image loader, allows rows with images to scroll smoothly
    private final CachingAsyncImageLoader mImageLoader;

    // false until we determine the Detail has at least one <grid> block
    private boolean usesGridView = false;
    // key to data mapping used to attach callout results to individual entities
    private OrderedHashtable<String, String> calloutResponseData = new OrderedHashtable<>();

    public EntityListAdapter(Activity activity, Detail detail,
                             List<TreeReference> references,
                             List<Entity<TreeReference>> full,
                             int[] sort,
                             NodeEntityFactory factory) {
        this.detail = detail;
        if (detail.getCustomActions() != null) {
            actionsCount = detail.getCustomActions().size();
        } else {
            actionsCount = 0;
        }

        this.full = full;
        this.references = references;
        this.context = activity;
        this.observers = new ArrayList<>();
        this.mNodeFactory = factory;

        //TODO: I am a bad person and I should feel bad. This should get encapsulated 
        //somewhere in the factory as a callback (IE: How to sort/or whether to or  something)
        mAsyncMode = (factory instanceof AsyncNodeEntityFactory);

        //TODO: Maybe we can actually just replace by checking whether the node is ready?
        if (!mAsyncMode) {
            if (sort.length != 0) {
                sort(sort);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 14) {
            mImageLoader = new CachingAsyncImageLoader(context);
        } else {
            mImageLoader = null;
        }

        this.usesGridView = detail.usesGridView();
        this.mFuzzySearchEnabled = CommCarePreferences.isFuzzySearchEnabled();

        setCurrent(new ArrayList<>(full));
    }

    /**
     * Set the current display set for this adapter
     */
    void setCurrent(List<Entity<TreeReference>> arrayList) {
        current = arrayList;
        if (actionsCount > 0) {
            actionsStartPosition = current.size();
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

    private void sort(int[] fields) {
        //The reversing here is only relevant if there's only one sort field and we're on it
        sort(fields, (currentSort.length == 1 && currentSort[0] == fields[0]) && !reverseSort);
    }

    private void sort(int[] fields, boolean reverse) {
        this.reverseSort = reverse;
        currentSort = fields;

        java.util.Collections.sort(full, new EntitySorter(detail.getFields(), reverseSort, currentSort));
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

    public int getFullCountWithActions() {
        return full.size() + actionsCount;
    }

    public int getCurrentCountWithActions() {
        return current.size() + actionsCount;
    }

    @Override
    public TreeReference getItem(int position) {
        return current.get(position).getElement();
    }

    @Override
    public long getItemId(int position) {
        if (actionsCount > 0 && position >= actionsStartPosition) {
            return SPECIAL_ACTION;
        }
        return references.indexOf(current.get(position).getElement());
    }

    @Override
    public int getItemViewType(int position) {
        if (actionsCount > 0 && position >= actionsStartPosition) {
            return 1;
        }
        return 0;
    }

    /**
     * Note that position gives a unique "row" id, EXCEPT that the header row AND the first content row
     * are both assigned position 0 -- this is not an issue for current usage, but it could be in future
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (actionsCount > 0 && position >= actionsStartPosition) {
            return getActionView(position, (HorizontalMediaView)convertView);
        }

        Entity<TreeReference> entity = current.get(position);
        // if we use a <grid>, setup an AdvancedEntityView
        if (usesGridView) {
            return getGridView(entity, (GridEntityView)convertView);
        } else {
            return getEntityView(entity, (EntityView)convertView, position);
        }
    }

    private View getActionViewNew(int position, CardView tiav) {
        if (tiav == null) {
            tiav = (CardView) View.inflate(context, R.layout.action_card, null);
        }
        Action currentAction = detail.getCustomActions().get(position - actionsStartPosition);
        tiav.
        tiav.setDisplay(currentAction.getDisplay());
        tiav.setBackgroundResource(R.drawable.list_bottom_tab);
        //We're gonna double pad this because we want to give it some visual distinction
        //and keep the icon more centered
        int padding = (int)context.getResources().getDimension(R.dimen.entity_padding);
        tiav.setPadding(padding, padding, padding, padding);
        return tiav;
    }
    private View getActionView(int position, HorizontalMediaView tiav) {
        if (tiav == null) {
            tiav = new HorizontalMediaView(context);
        }
        Action currentAction = detail.getCustomActions().get(position - actionsStartPosition);
        tiav.setDisplay(currentAction.getDisplay());
        tiav.setBackgroundResource(R.drawable.list_bottom_tab);
        //We're gonna double pad this because we want to give it some visual distinction
        //and keep the icon more centered
        int padding = (int)context.getResources().getDimension(R.dimen.entity_padding);
        tiav.setPadding(padding, padding, padding, padding);
        return tiav;
    }

    private View getGridView(Entity<TreeReference> entity, GridEntityView emv) {
        int[] titleColor = AndroidUtil.getThemeColorIDs(context, new int[]{R.attr.entity_select_title_text_color});
        if (emv == null) {
            emv = new GridEntityView(context, detail, entity, currentSearchTerms, mImageLoader, mFuzzySearchEnabled);
        } else {
            emv.setSearchTerms(currentSearchTerms);
            emv.setViews(context, detail, entity);
        }
        emv.setTitleTextColor(titleColor[0]);
        return emv;
    }

    private View getEntityView(Entity<TreeReference> entity, EntityView emv, int position) {
        if (emv == null) {
            emv = EntityView.buildEntryEntityView(
                    context, detail, entity,
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

    @Override
    public int getViewTypeCount() {
        return (actionsCount > 0) ? 2 : 1;
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
            searchTerms[i] = StringUtils.normalize(searchTerms[i]);
        }
        currentSearchTerms = searchTerms;
        searchQuery = filterRaw;
        entityFilterer =
                new EntityStringFilterer(this, searchTerms, mAsyncMode,
                        mFuzzySearchEnabled, mNodeFactory, full, context);
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
                new EntityKeyFilterer(this, mNodeFactory, full, context, keysToFilterBy);
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

    public int[] getCurrentSort() {
        return currentSort;
    }

    public boolean isCurrentSortReversed() {
        return reverseSort;
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

    /**
     * Get action's index in detail's list of actions given position in adapter
     */
    public int getActionIndex(int positionInAdapter) {
        return positionInAdapter - (getCurrentCountWithActions() - 1);
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
                (OrderedHashtable<String, String>)CommCareApplication._()
                        .getCurrentSession()
                        .getCurrentFrameStepExtra(KEY_ENTITY_LIST_EXTRA_DATA);
        if (externalData != null) {
            filterByKeyedCalloutData(externalData);
        }
    }

    public void saveCalloutDataToSession() {
        if (isFilteringByCalloutResult) {
            CommCareApplication._().getCurrentSession().addExtraToCurrentFrameStep(KEY_ENTITY_LIST_EXTRA_DATA, calloutResponseData);
        }
    }
}
