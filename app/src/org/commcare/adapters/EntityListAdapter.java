package org.commcare.adapters;

import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.dalvik.R;
import org.commcare.models.AsyncNodeEntityFactory;
import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DisplayData;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.CachingAsyncImageLoader;
import org.commcare.utils.FileUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.EntityView;
import org.commcare.views.GridEntityView;
import org.commcare.views.media.AudioButton;
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

    public static final int ENTITY_TYPE = 0;
    public static final int ACTION_TYPE = 1;
    public static final int DIVIDER_TYPE = 2;
    public static final int DIVIDER_ID = -2;

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

    public EntityListAdapter(CommCareActivity activity, Detail detail,
                             List<TreeReference> references,
                             List<Entity<TreeReference>> full,
                             int[] sort,
                             NodeEntityFactory factory) {
        this.detail = detail;
        if (detail.getCustomActions() != null) {
            actionsCount = detail.getCustomActions().size();
            dividerCount = 1;
        } else {
            actionsCount = 0;
            dividerCount = 0;
        }

        this.full = full;
        this.references = references;
        this.commCareActivity = activity;
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
            mImageLoader = new CachingAsyncImageLoader(commCareActivity);
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
        return full.size() + actionsCount + dividerCount;
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
                return dividerPosition + detail.getCustomActions().indexOf(getAction(position));
            case DIVIDER_TYPE:
                return DIVIDER_ID;
            default:
                throw new RuntimeException("Invalid view type");
        }
    }

    private Action getAction(int position) {
        return detail.getCustomActions().get(position - (dividerPosition + 1));
    }

    @Override
    public int getItemViewType(int position) {
        if (actionsCount > 0) {
            if (position > dividerPosition) {
                return ACTION_TYPE;
            } else {
                return DIVIDER_TYPE;
            }
        } else {
            return ENTITY_TYPE;
        }
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

        if (usesGridView) {
            // if we use a <grid>, setup an AdvancedEntityView
            return getGridView(entity, (GridEntityView)convertView);
        } else {
            return getListEntityView(entity, (EntityView)convertView, position);
        }
    }

    private View getGridView(Entity<TreeReference> entity, GridEntityView emv) {
        int[] titleColor = AndroidUtil.getThemeColorIDs(commCareActivity, new int[]{R.attr.entity_select_title_text_color});
        if (emv == null) {
            emv = new GridEntityView(commCareActivity, detail, entity, currentSearchTerms, mImageLoader, mFuzzySearchEnabled);
        } else {
            emv.setSearchTerms(currentSearchTerms);
            emv.setViews(commCareActivity, detail, entity);
        }
        emv.setTitleTextColor(titleColor[0]);
        return emv;
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
            actionCardView = (FrameLayout)LayoutInflater.from(parent.getContext()).inflate(R.layout.action_card, parent, false);
        }

        final Action action = getAction(position);
        DisplayData displayData = action.getDisplay().evaluate();

        String audioURI = displayData.getAudioURI();
        if (audioURI != null) {
            AudioButton audioButton = (AudioButton)actionCardView.findViewById(R.id.audio);
            if (FileUtil.referenceFileExists(audioURI)) {
                audioButton.setVisibility(View.VISIBLE);
                audioButton.resetButton(audioURI, true);
            }
        }

        String imageURI = displayData.getImageURI();
        if (imageURI != null) {
            ImageView icon = (ImageView)actionCardView.findViewById(R.id.icon);
            int iconDimension = (int)commCareActivity.getResources().getDimension(R.dimen.menu_icon_size);
            Bitmap b = MediaUtil.inflateDisplayImage(commCareActivity, imageURI, iconDimension, iconDimension);
            if (b != null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageBitmap(b);
            }
        }

        TextView text = (TextView)actionCardView.findViewById(R.id.text);
        text.setText(displayData.getName().toUpperCase());

        ImageButton performActionButton = (ImageButton)actionCardView.findViewById(R.id.launch_action);
        performActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EntitySelectActivity.triggerDetailAction(action, commCareActivity);
            }
        });

        return actionCardView;
    }

    private LinearLayout getDividerView(LinearLayout convertView, ViewGroup parent) {
        if (convertView == null) {
            return (LinearLayout)LayoutInflater.from(parent.getContext()).inflate(R.layout.line_separator, parent, false);
        }
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
            searchTerms[i] = StringUtils.normalize(searchTerms[i]);
        }
        currentSearchTerms = searchTerms;
        searchQuery = filterRaw;
        entityFilterer =
                new EntityStringFilterer(this, searchTerms, mAsyncMode,
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
