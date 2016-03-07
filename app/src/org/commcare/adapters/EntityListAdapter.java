package org.commcare.adapters;

import android.app.Activity;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

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

import java.util.ArrayList;
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

    public static final int SPECIAL_ACTION = -2;

    private int actionsStartPosition = 0;
    private final int actionsCount;

    private boolean mFuzzySearchEnabled = true;

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

    private EntitySearcher entitySearcher = null;
    private final Object mSyncLock = new Object();

    private final CachingAsyncImageLoader mImageLoader;   // Asyncronous image loader, allows rows with images to scroll smoothly
    private boolean usesGridView = false;  // false until we determine the Detail has at least one <grid> block

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
        setCurrent(new ArrayList<Entity<TreeReference>>());
        this.references = references;

        this.context = activity;
        this.observers = new ArrayList<>();

        mNodeFactory = factory;

        //TODO: I am a bad person and I should feel bad. This should get encapsulated 
        //somewhere in the factory as a callback (IE: How to sort/or whether to or  something)
        mAsyncMode = (factory instanceof AsyncNodeEntityFactory);

        //TODO: Maybe we can actually just replace by checking whether the node is ready?
        if (!mAsyncMode) {
            if (sort.length != 0) {
                sort(sort);
            }
            applyFilter("");
        } else {
            setCurrent(new ArrayList<>(full));
        }

        if (android.os.Build.VERSION.SDK_INT >= 14) {
            mImageLoader = new CachingAsyncImageLoader(context);
        } else {
            mImageLoader = null;
        }

        usesGridView = detail.usesGridView();
        this.mFuzzySearchEnabled = CommCarePreferences.isFuzzySearchEnabled();
    }

    /**
     * Set the current display set for this adapter
     */
    void setCurrent(List<Entity<TreeReference>> arrayList) {
        current = arrayList;
        if (actionsCount > 0) {
            actionsStartPosition = current.size();
        }
    }

    void setCurrentSearchTerms(String[] searchTerms) {
        currentSearchTerms = searchTerms;
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
            emv = EntityView.buildEntryEntityView(context, detail, entity, null, currentSearchTerms, position, mFuzzySearchEnabled);
        } else {
            emv.setSearchTerms(currentSearchTerms);
            emv.refreshViewsForNewEntity(entity, entity.getElement().equals(selected), position);
        }
        return emv;
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

    public void applyFilter(String filterRaw) {
        synchronized (mSyncLock) {
            if (entitySearcher != null) {
                entitySearcher.finish();
            }
            String[] searchTerms = filterRaw.split("\\s+");
            for (int i = 0; i < searchTerms.length; ++i) {
                searchTerms[i] = StringUtils.normalize(searchTerms[i]);
            }
            entitySearcher = new EntitySearcher(this, searchTerms, mAsyncMode, mFuzzySearchEnabled, mNodeFactory, full, context);
            entitySearcher.start();
        }
    }

    public void filterByKey(List<Object> objs) {
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
    public void signalKilled() {
        synchronized (mSyncLock) {
            if (entitySearcher != null) {
                entitySearcher.finish();
            }
        }
    }

    /**
     * Get action's index in detail's list of actions given position in adapter
     */
    public int getActionIndex(int positionInAdapter) {
        return positionInAdapter - (getCurrentCountWithActions() - 1);
    }
}
