package org.commcare.adapters;


import android.content.Context;
import android.database.DataSetObserver;
import android.os.AsyncTask.Status;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormRecordListActivity.FormRecordFilter;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.FormEntry;
import org.commcare.suite.model.Suite;
import org.commcare.suite.model.Text;
import org.commcare.tasks.FormRecordLoadListener;
import org.commcare.tasks.FormRecordLoaderTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.views.IncompleteFormRecordView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * Responsible for delegating the loading of form lists and performing
 * filtering over them.
 *
 * @author ctsims
 */
public class IncompleteFormListAdapter extends BaseAdapter implements FormRecordLoadListener {
    private final Context context;

    private final List<DataSetObserver> observers;

    private FormRecordFilter filter;

    /**
     * All loaded form records of a given status, with no filtering.
     */
    private List<FormRecord> records;

    /**
     * Filtered form records. getView reads from this to display all the forms.
     */
    private final List<FormRecord> current = new ArrayList<>();

    /**
     * Maps FormRecord ID to an array of text that will be shown to the user
     * and query-able. Text should includes modified date, record title, and
     * form name.
     */
    private final Hashtable<Integer, String[]> searchCache = new Hashtable<>();

    /**
     * The last query made, used to filter forms.
     */
    private String query = "";

    /**
     * The current query split up by spaces. Used for filtering forms.
     */
    private String[] queryPieces = new String[0];

    private FormRecordLoaderTask loader;

    /**
     * Maps form namespace (unique id for forms) to their form title
     * (entry-point text). Needed because FormRecords don't have form title
     * info, but do have the namespace.
     */
    private final Hashtable<String, Text> names;

    public IncompleteFormListAdapter(Context context,
                                     AndroidCommCarePlatform platform,
                                     FormRecordLoaderTask loader) {
        this.context = context;
        this.filter = null;
        this.loader = loader;

        observers = new ArrayList<>();
        names = new Hashtable<>();

        loader.addListener(this);

        // create a mapping from form definition IDs to their entry point text
        for (Suite s : platform.getInstalledSuites()) {
            for (Enumeration en = s.getEntries().elements(); en.hasMoreElements(); ) {
                Entry entry = (Entry) en.nextElement();
                if (!entry.isView()) {
                    String namespace = ((FormEntry)entry).getXFormNamespace();
                    //Some of our old definitions for views still come in as entries with dead
                    //namespaces for now, so check. Can clean up when FormEntry's enforce a
                    //namespace invariant
                    if(namespace != null) {
                        names.put(namespace, entry.getText());
                    }
                }
            }
        }
    }

    /**
     * Add a newly-loaded form to the current list if it satisfies the current
     * query.
     */
    @Override
    public void notifyPriorityLoaded(FormRecord record, boolean isLoaded) {
        if (isLoaded && satisfiesQuery(record)) {
            current.add(record);
            notifyDataSetChanged();
        }
    }

    /**
     * Notify observers that the form list has loaded/potentially been updated.
     */
    @Override
    public void notifyLoaded() {
        notifyDataSetChanged();
    }

    /**
     * Load new records and text if the given FormFilter differs from the
     * current filter.
     *
     * @param newFilter update the internal FormFilter to this value
     */
    public void setFilterAndResetRecords(FormRecordFilter newFilter) {
        if (!newFilter.equals(this.filter)) {
            setFormFilter(newFilter);
            resetRecords();
        }
    }

    /**
     * Reload form record list for current filter status and collect pertinent
     * text data using FormRecordLoaderTask; results will then be re-filtered
     * and displayed via callbacks.
     */
    public void resetRecords() {
        // reload the form records, even if they are currently being loaded
        if (loader.getStatus() == Status.RUNNING) {
            loader.cancel(false);
            loader = loader.spawn();
        } else if (loader.getStatus() == Status.FINISHED) {
            loader = loader.spawn();
        }

        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);

        // choose a default filter if none set
        if (filter == null) {
            filter = FormRecordFilter.SubmittedAndPending;
        }

        records = new Vector<>();
        String currentAppId = CommCareApplication._().getCurrentApp().getAppRecord().getApplicationId();
        // Grab all form records that satisfy ANY of the statuses in the filter, AND belong to the
        // currently seated app
        for (String status : filter.getStatus()) {
            records.addAll(storage.getRecordsForValues(
                    new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                    new Object[]{status, currentAppId}));
        }

        // Sort FormRecords by modification time, most recent first.
        Collections.sort(records, new Comparator<FormRecord>() {
            public int compare(FormRecord left, FormRecord right) {
                long leftModTime = left.lastModified().getTime();
                long rightModTime = right.lastModified().getTime();

                if (leftModTime > rightModTime) {
                    return -1;
                } else if (leftModTime == rightModTime) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        searchCache.clear();
        current.clear();
        notifyDataSetChanged();

        // load specific data about the 'records' into the searchCache, such as
        // record title, form name, modified date
        loader.init(searchCache, names);
        loader.execute(records.toArray(new FormRecord[records.size()]));
    }

    public int findRecordPosition(int formRecordId) {
        for (int i = 0; i < current.size(); ++i) {
            FormRecord record = current.get(i);
            if (record.getID() == formRecordId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();

        for (DataSetObserver observer : observers) {
            observer.onChanged();
        }
    }

    @Override
    public void notifyDataSetInvalidated() {
        super.notifyDataSetInvalidated();
        resetRecords();
        for (DataSetObserver observer : observers) {
            observer.onChanged();
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

    @Override
    public int getCount() {
        return current.size();
    }

    @Override
    public Object getItem(int i) {
        return current.get(i);
    }

    @Override
    public long getItemId(int i) {
        //Skeeeeetccchhyyyy maybe?
        return current.get(i).getID();
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View v, ViewGroup vg) {
        FormRecord r = current.get(i);
        IncompleteFormRecordView ifrv = (IncompleteFormRecordView) v;
        if (ifrv == null) {
            ifrv = new IncompleteFormRecordView(context, names);
        }

        if (searchCache.containsKey(r.getID())) {
            ifrv.setParams(r, searchCache.get(r.getID())[1], r.lastModified().getTime());
        } else {
            // notify the loader that we need access to this record immediately
            loader.registerPriority(r);
            // TODO: PLM: once the priority item is reloaded in the async task,
            // there is no hook to explicitly re-set the title. That is, the
            // local notifyPriorityLoaded method should probably be defined to
            // reset the params of this record. It will eventually get reset,
            // once this method is called again...
            ifrv.setParams(r, "Loading...", r.lastModified().getTime());
        }

        return ifrv;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    public void setFormFilter(FormRecordFilter filter) {
        this.filter = filter;
    }

    public FormRecordFilter getFilter() {
        return this.filter;
    }

    /**
     * Filter loaded FormRecords by those whose data contains any word in the
     * query field.
     * <p/>
     * Reads from FormRecords in the 'records' field and moves them into the
     * cleared out the 'current' field.
     */
    private void filterValues() {
        // If FormRecords are still being loaded, wait for them to finish.
        // Upon load completion this method will get called.
        if (!loader.doneLoadingFormRecords()) {
            return;
        }

        current.clear();

        if ("".equals(query)) {
            current.addAll(records);
        } else {
            // collect all forms that have text data that contains pieces.
            for (FormRecord r : records) {
                if (satisfiesQuery(r)) {
                    current.add(r);
                }
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Does the form record have text that contains one of the query segments?
     *
     * @param r Lookup this form record's text and compare to the current query
     *          segments.
     * @return Did the text corresponding to the form record argument contain
     * any of the query segments?
     */
    private boolean satisfiesQuery(FormRecord r) {
        if (queryPieces.length == 0) {
            // empty queries always pass
            return true;
        }

        for (String cacheValue : searchCache.get(r.getID())) {
            for (String piece : this.queryPieces) {
                if (cacheValue.toLowerCase().contains(piece)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Re-filter form listing based on query parameter.
     *
     * @param newQuery set the current query to this value.
     */
    public void applyTextFilter(String newQuery) {
        if (this.query.trim().equals(newQuery.trim())) {
            // don't perform filtering if old and new queries are same, modulo
            // whitespace
            return;
        }

        this.query = newQuery;

        // split the query up into segments, by whitespace.
        if ("".equals(this.query)) {
            this.queryPieces = new String[0];
        } else {
            this.queryPieces = newQuery.toLowerCase().split(" ");
        }

        filterValues();

        for (DataSetObserver o : observers) {
            o.onChanged();
        }
    }


    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        this.observers.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        this.observers.remove(observer);
    }

    public void release() {
        if (loader.getStatus() == Status.RUNNING) {
            loader.cancel(false);
        }
    }

    public boolean isValid(int i) {
        return names.containsKey(current.get(i).getFormNamespace());
    }
}
