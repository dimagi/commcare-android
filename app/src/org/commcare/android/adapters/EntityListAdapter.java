package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.commcare.android.models.Entity;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.CachingAsyncImageLoader;
import org.commcare.android.view.GridEntityView;
import org.commcare.android.util.StringUtils;
import org.commcare.android.view.EntityView;
import org.commcare.android.view.TextImageAudioView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.odk.collect.android.views.media.AudioController;

import android.content.Context;
import android.database.DataSetObserver;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

/**
 * @author ctsims
 * @author wspride
 * 
 * This adapter class handles displaying the cases for a CommCareODK user.
 * Depending on the <grid> block of the Detail this adapter is constructed with, cases might be
 * displayed as normal EntityViews or as AdvancedEntityViews
 */
public class EntityListAdapter implements ListAdapter {

    public static final int SPECIAL_ACTION = -2;

    private int actionPosition = -1;
    private boolean actionEnabled; 

    private boolean mFuzzySearchEnabled = true;

    Context context;
    Detail detail;

    List<DataSetObserver> observers;

    List<Entity<TreeReference>> full;
    List<Entity<TreeReference>> current;
    List<TreeReference> references;

    TextToSpeech tts;
    AudioController controller;

    private TreeReference selected;

    private boolean hasWarned;

    int currentSort[] = {};
    boolean reverseSort = false;

    private String[] currentSearchTerms;

    public static int SCALE_FACTOR = 1;   // How much we want to degrade the image quality to enable faster laoding. TODO: get cleverer
    private CachingAsyncImageLoader mImageLoader;   // Asyncronous image loader, allows rows with images to scroll smoothly
    private boolean usesGridView = false;  // false until we determine the Detail has at least one <grid> block

    private boolean inAwesomeMode = false;

    public EntityListAdapter(Context context, Detail detail, List<TreeReference> references, List<Entity<TreeReference>> full, 
            int[] sort, TextToSpeech tts, AudioController controller) throws SessionUnavailableException {
        this.detail = detail;

        this.full = full;
        current = new ArrayList<Entity<TreeReference>>();
        this.references = references;

        this.context = context;
        this.observers = new ArrayList<DataSetObserver>();

        if(sort.length != 0) {
            sort(sort);
        }
        filterValues("");
        this.tts = tts;
        this.controller = controller;
        if(android.os.Build.VERSION.SDK_INT >= 14){
            mImageLoader = new CachingAsyncImageLoader(context, SCALE_FACTOR);
        }
        else{
            mImageLoader = null;
        }
        if(detail.getCustomAction() != null) {
        }
        usesGridView = detail.usesGridView();
        this.mFuzzySearchEnabled = CommCarePreferences.isFuzzySearchEnabled();
        
        actionEnabled = detail.getCustomAction() != null;
    }

    private void filterValues(String filterRaw) {

        String[] searchTerms = filterRaw.split("\\s+");
        for(int i = 0 ; i < searchTerms.length ; ++i) {
            searchTerms[i] = StringUtils.normalize(searchTerms[i]);
        }

        current.clear();

        full:
            for(Entity<TreeReference> e : full) {
                if("".equals(filterRaw)) {
                    current.add(e);
                    continue;
                }

                boolean add = false;
                filter:
                    for(String filter: searchTerms) {
                        add = false;
                        for(int i = 0 ; i < e.getNumFields(); ++i) {
                            String field = StringUtils.normalize(e.getFieldString(i));
                            if(field.toLowerCase().contains(filter)) {
                                add = true;
                                continue filter;
                            } else {
                                //We possibly now want to test for edit distance for fuzzy matching
                                if(mFuzzySearchEnabled) {
                                    String sortField = e.getSortField(i);
                                    if(sortField != null) {
                                        //We always fuzzy match on the sort field and only if it is available
                                        //(as a way to restrict possible matching)
                                        sortField = StringUtils.normalize(sortField);
                                        for(String fieldChunk : sortField.split(" ")) {
                                            if(StringUtils.fuzzyMatch(fieldChunk, filter)) {
                                                add = true;
                                                continue filter;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if(!add) { break; }
                    }
                if(add) {
                    current.add(e);
                    continue full;
                }
            }
        this.currentSearchTerms = searchTerms;

        if(actionEnabled) {
            actionPosition = current.size(); 
        }
    }

    private void sort(int[] fields) {
        //The reversing here is only relevant if there's only one sort field and we're on it
        sort(fields, (currentSort.length == 1 && currentSort[0] == fields[0]) ? !reverseSort : false);
    }

    private void sort(int[] fields, boolean reverse) {

        this.reverseSort = reverse;

        hasWarned = false;

        currentSort = fields;

        java.util.Collections.sort(full, new Comparator<Entity<TreeReference>>() {


            public int compare(Entity<TreeReference> object1, Entity<TreeReference> object2) {
                for(int i = 0 ; i < currentSort.length ; ++i) {
                    boolean reverseLocal = (detail.getFields()[currentSort[i]].getSortDirection() == DetailField.DIRECTION_DESCENDING) ^ reverseSort;
                    int cmp =  (reverseLocal ? -1 : 1) * getCmp(object1, object2, currentSort[i]);
                    if(cmp != 0 ) { return cmp;}
                }
                return 0;
            }

            private int getCmp(Entity<TreeReference> object1, Entity<TreeReference> object2, int index) {

                int i = detail.getFields()[index].getSortType();

                String a1 = object1.getSortField(index);
                String a2 = object2.getSortField(index);

                if(a1 == null) { a1 = object1.getFieldString(i); }
                if(a2 == null) { a2 = object2.getFieldString(i); }

                //TODO: We might want to make this behavior configurable (Blanks go first, blanks go last, etc);
                //For now, regardless of typing, blanks are always smaller than non-blanks
                if(a1.equals("")) {
                    if(a2.equals("")) { return 0; }
                    else { return -1; }
                } else if(a2.equals("")) {
                    return 1;
                }

                Comparable c1 = applyType(i, a1);
                Comparable c2 = applyType(i, a2);

                if(c1 == null || c2 == null) {
                    //Don't do something smart here, just bail.
                    return -1;
                }

                return c1.compareTo(c2);
            }

            private Comparable applyType(int sortType, String value) {
                try {
                    if(sortType == Constants.DATATYPE_TEXT) {
                        return value.toLowerCase();
                    } else if(sortType == Constants.DATATYPE_INTEGER) {
                        //Double int compares just fine here and also
                        //deals with NaN's appropriately

                        double ret = XPathFuncExpr.toInt(value);
                        if(Double.isNaN(ret)){
                            String[] stringArgs = new String[3];
                            stringArgs[2] = value;
                            if(!hasWarned){
                                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Bad_Case_Filter, stringArgs));
                                hasWarned = true;
                            }
                        }
                        return ret;
                    } else if(sortType == Constants.DATATYPE_DECIMAL) {
                        double ret = XPathFuncExpr.toDouble(value);
                        if(Double.isNaN(ret)){

                            String[] stringArgs = new String[3];
                            stringArgs[2] = value;
                            if(!hasWarned){
                                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Bad_Case_Filter, stringArgs));
                                hasWarned = true;
                            }
                        }
                        return ret;
                    } else {
                        //Hrmmmm :/ Handle better?
                        return value;
                    } 
                } catch(XPathTypeMismatchException e) {
                    //So right now this will fail 100% silently, which is bad.
                    return null;
                }

            }

        });
    }

    /* (non-Javadoc)
     * @see android.widget.ListAdapter#areAllItemsEnabled()
     */
    public boolean areAllItemsEnabled() {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.ListAdapter#isEnabled(int)
     */
    public boolean isEnabled(int position) {
        return true;
    }

    /*
     * Includes action, if enabled, as an item.
     * 
     * (non-Javadoc)
     * @see android.widget.Adapter#getCount()
     */
    public int getCount() {
        return getCount(false, false);
    }

    /*
     * Returns total number of items, ignoring any filter.
     */
    public int getFullCount() {
        return getCount(false, true);
    }

    /*
     * Get number of items, with a parameter to decide whether or not action counts as an item.
     */
    public int getCount(boolean ignoreAction, boolean fullCount) {
        //Always one extra element if the action is defined
        return (fullCount ? full.size() : current.size()) + (actionEnabled && !ignoreAction ? 1 : 0);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    public TreeReference getItem(int position) {
        return current.get(position).getElement();
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    public long getItemId(int position) {
        if(actionEnabled) {
            if(position == actionPosition) {
                return SPECIAL_ACTION;
            }
        }
        return references.indexOf(current.get(position).getElement());
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemViewType(int)
     */
    public int getItemViewType(int position) {
        if(actionEnabled) {
            if(position == actionPosition) {
                return 1;
            }
        }
        return 0;
    }

    public void setController(AudioController controller) {
        this.controller = controller;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    /* Note that position gives a unique "row" id, EXCEPT that the header row AND the first content row
     * are both assigned position 0 -- this is not an issue for current usage, but it could be in future
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        if(actionEnabled && position == actionPosition) {
            TextImageAudioView tiav =(TextImageAudioView)convertView;

            if(tiav == null) {
                tiav = new TextImageAudioView(context);
            }
            tiav.setDisplay(detail.getCustomAction().getDisplay());
            tiav.setBackgroundResource(R.drawable.list_bottom_tab);
            //We're gonna double pad this because we want to give it some visual distinction
            //and keep the icon more centered
            int padding = (int) context.getResources().getDimension(R.dimen.entity_padding);
            tiav.setPadding(padding, padding, padding, padding);
            return tiav;
        }

        Entity<TreeReference> entity = current.get(position);
        // if we use a <grid>, setup an AdvancedEntityView
        if(usesGridView){
            GridEntityView emv =(GridEntityView)convertView;

            if(emv == null) {
                emv = new GridEntityView(context, detail, entity, currentSearchTerms, mImageLoader, controller, mFuzzySearchEnabled);
            } else{
               emv.setSearchTerms(currentSearchTerms);
                emv.setViews(context, detail, entity);
            }
            return emv;

        } 
        // if not, just use the normal row
        else{
            EntityView emv =(EntityView)convertView;

            if (emv == null) {
                emv = new EntityView(context, detail, entity, tts, currentSearchTerms, controller, position, mFuzzySearchEnabled);
            } else {
                emv.setSearchTerms(currentSearchTerms);
                emv.refreshViewsForNewEntity(entity, entity.getElement().equals(selected), position);
            }
            return emv;
        }

    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getViewTypeCount()
     */
    public int getViewTypeCount() {
        return actionEnabled? 2 : 1;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#hasStableIds()
     */
    public boolean hasStableIds() {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#isEmpty()
     */
    public boolean isEmpty() {
        return getCount() > 0;
    }

    public void applyFilter(String s) {
        filterValues(s);
        update();
    }

    private void update() {
        for(DataSetObserver o : observers) {
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

    /* (non-Javadoc)
     * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
     */
    public void registerDataSetObserver(DataSetObserver observer) {
        this.observers.add(observer);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
     */
    public void unregisterDataSetObserver(DataSetObserver observer) {
        this.observers.remove(observer);
    }

    public void notifyCurrentlyHighlighted(TreeReference chosen) {
        this.selected = chosen;
        update();
    }

    public void setAwesomeMode(boolean awesome){
        inAwesomeMode = awesome;
    }

    public int getPosition(TreeReference chosen) {
        for(int i = 0 ; i < current.size() ; ++i) {
            Entity<TreeReference> e = current.get(i);
            if(e.getElement().equals(chosen)) {
                return i;
            }
        }
        return -1;
    }
}
