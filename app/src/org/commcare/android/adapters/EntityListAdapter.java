package org.commcare.android.adapters;

import android.app.Activity;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import org.commcare.android.models.AsyncNodeEntityFactory;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.CachingAsyncImageLoader;
import org.commcare.android.util.StringUtils;
import org.commcare.android.view.EntityView;
import org.commcare.android.view.GridEntityView;
import org.commcare.android.view.HorizontalMediaView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.suite.model.Detail;
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

    private int actionPosition = -1;
    private final boolean actionEnabled;

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
        actionEnabled = detail.getCustomAction() != null;

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
            filterValues("");
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
        if (actionEnabled) {
            actionPosition = current.size();
        }
    }

    void setCurrentSearchTerms(String[] searchTerms) {
        currentSearchTerms = searchTerms;
    }

    private void filterValues(String filterRaw) {
        synchronized (mSyncLock) {
            if (entitySearcher != null) {
                entitySearcher.finish();
            }
            String[] searchTerms = filterRaw.split("\\s+");
            for (int i = 0; i < searchTerms.length; ++i) {
                searchTerms[i] = StringUtils.normalize(searchTerms[i]);
            }
            entitySearcher = new EntitySearcher(this, filterRaw, searchTerms, mAsyncMode, mFuzzySearchEnabled, mNodeFactory, full, context);
            entitySearcher.start();
        }
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
        return getCount(false, false);
    }

    /**
     * Get number of items, with a parameter to decide whether or not action counts as an item.
     */
    public int getCount(boolean ignoreAction, boolean fullCount) {
        //Always one extra element if the action is defined
        return (fullCount ? full.size() : current.size()) + (actionEnabled && !ignoreAction ? 1 : 0);
    }

    @Override
    public TreeReference getItem(int position) {
        return current.get(position).getElement();
    }

    @Override
    public long getItemId(int position) {
        if (actionEnabled) {
            if (position == actionPosition) {
                return SPECIAL_ACTION;
            }
        }
        return references.indexOf(current.get(position).getElement());
    }

    @Override
    public int getItemViewType(int position) {
        if (actionEnabled) {
            if (position == actionPosition) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Note that position gives a unique "row" id, EXCEPT that the header row AND the first content row
     * are both assigned position 0 -- this is not an issue for current usage, but it could be in future
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (actionEnabled && position == actionPosition) {
            HorizontalMediaView tiav = (HorizontalMediaView)convertView;

            if (tiav == null) {
                tiav = new HorizontalMediaView(context);
            }
            tiav.setDisplay(detail.getCustomAction().getDisplay());
            tiav.setBackgroundResource(R.drawable.list_bottom_tab);
            //We're gonna double pad this because we want to give it some visual distinction
            //and keep the icon more centered
            int padding = (int)context.getResources().getDimension(R.dimen.entity_padding);
            tiav.setPadding(padding, padding, padding, padding);
            return tiav;
        }

        Entity<TreeReference> entity = current.get(position);
        // if we use a <grid>, setup an AdvancedEntityView
        if (usesGridView) {
            GridEntityView emv = (GridEntityView)convertView;
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
        // if not, just use the normal row
        else {
            EntityView emv = (EntityView)convertView;

            if (emv == null) {
                emv = EntityView.buildEntryEntityView(context, detail, entity, null, currentSearchTerms, position, mFuzzySearchEnabled);
            } else {
                emv.setSearchTerms(currentSearchTerms);
                emv.refreshViewsForNewEntity(entity, entity.getElement().equals(selected), position);
            }
            return emv;
        }

    }

    @Override
    public int getViewTypeCount() {
        return actionEnabled ? 2 : 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return getCount() > 0;
    }

    public void applyFilter(String s) {
        filterValues(s);
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
}
