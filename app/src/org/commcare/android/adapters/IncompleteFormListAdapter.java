package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.tasks.FormRecordLoaderTask;
import org.commcare.android.tasks.FormRecordLoadListener;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.IncompleteFormRecordView;
import org.commcare.dalvik.activities.FormRecordListActivity.FormRecordFilter;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Suite;
import org.commcare.suite.model.Text;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.AsyncTask.Status;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * Responsible for delegating the loading of form lists and performing
 * filtering over them.
 *
 * @author ctsims
 *
 */
public class IncompleteFormListAdapter extends BaseAdapter implements FormRecordLoadListener {
    private final Context context;
    
    @NonNull
    private final List<DataSetObserver> observers;
    
    @Nullable
    private FormRecordFilter filter;

    /**
     * All loaded form records of a given status, with no filtering.
     */
    private List<FormRecord> records;

    /**
     * Filtered form records. getView reads from this to display all the forms.
     */
    private final List<FormRecord> current = new ArrayList<FormRecord>();

    /**
     * Maps FormRecord ID to an array of text that will be shown to the user
     * and query-able. Text should includes modified date, record title, and
     * form name.
     */
    private final Hashtable<Integer, String[]> searchCache = new Hashtable<Integer, String[]>();

    /**
     * The last query made, used to filter forms.
     */
    @NonNull
    private String query = "";

    /**
     * The current query split up by spaces. Used for filtering forms.
     */
    @NonNull
    private String [] queryPieces = new String[0];

    private FormRecordLoaderTask loader;

    /**
     * Maps form namespace (unique id for forms) to their form title
     * (entry-point text). Needed because FormRecords don't have form title
     * info, but do have the namespace.
     */
    @NonNull
    private final Hashtable<String, Text> names;

    public IncompleteFormListAdapter(Context context, @NonNull AndroidCommCarePlatform platform, @NonNull FormRecordLoaderTask loader) throws SessionUnavailableException{
        this.context = context;
        this.filter = null;
        this.loader = loader;

        observers = new ArrayList<DataSetObserver>();
        names = new Hashtable<String, Text>();

        loader.addListener(this);

        // create a mapping from form definition IDs to their entry point text
        for (Suite s : platform.getInstalledSuites()) {
            for (Enumeration en = s.getEntries().elements(); en.hasMoreElements() ;) {
                Entry entry = (Entry)en.nextElement();
                if (entry.getXFormNamespace() != null) {
                    // Ensure that entry is actually <entry> and not a <view>,
                    // which can't define a form
                    names.put(entry.getXFormNamespace(), entry.getText());
                }
            }
        }
    }

    /**
     * Add a newly-loaded form to the current list if it satisfies the current
     * query.
     */
    @Override
    public void notifyPriorityLoaded(@NonNull FormRecord record, boolean isLoaded) {
        if (isLoaded && satisfiesQuery(record)) {
            current.add(record);
        }
    }

    /**
     * Notify observers that the form list has loaded/potentially been updated.
     */
    @Override
    public void notifyLoaded() {
        this.notifyDataSetChanged();
    }

    /**
     * Load new records and text if the given FormFilter differs from the
     * current filter.
     *
     * @param newFilter update the internal FormFilter to this value
     */
    public void setFilterAndResetRecords(@NonNull FormRecordFilter newFilter) {
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
        } else if(loader.getStatus() == Status.FINISHED) {
            loader = loader.spawn();
        }

        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);

        // choose a default filter if none set
        if (filter == null) {
            filter = FormRecordFilter.SubmittedAndPending;
        }

        records = new Vector<FormRecord>();
        // for each type of status in the filter, grab all the records that satisfy it
        for (String status : filter.getStatus()) {
            records.addAll(storage.getRecordsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {status}));
        }

        // Sort FormRecords by modification time, most recent first.
        Collections.sort(records, new Comparator<FormRecord>() {
            public int compare(@NonNull FormRecord left, @NonNull FormRecord right) {
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

        // load specific data about the 'records' into the searchCache, such as
        // record title, form name, modified date
        loader.init(searchCache, names);
        loader.execute(records.toArray(new FormRecord[0]));
    }

    public int findRecordPosition(int formRecordId) {
        for(int i = 0 ; i < current.size() ; ++i) {
            FormRecord record = current.get(i);
            if(record.getID() == formRecordId) {
                return i;
            }
        }
        return -1;
    }

    /*
     * (non-Javadoc)
     * @see android.widget.BaseAdapter#notifyDataSetChanged()
     */
    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        for (DataSetObserver observer: observers) {
            observer.onChanged();
        }
    }
    
    /*
     * (non-Javadoc)
     * @see android.widget.BaseAdapter#notifyDataSetInvalidated()
     */
    @Override
    public void notifyDataSetInvalidated() {
        super.notifyDataSetInvalidated();
        resetRecords();
        for (DataSetObserver observer: observers) {
            observer.onChanged();
        }
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
    public boolean isEnabled(int i) {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getCount()
     */
    public int getCount() {
        return current.size();
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    public Object getItem(int i) {
        return current.get(i);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    public long getItemId(int i) {
        //Skeeeeetccchhyyyy maybe?
        return current.get(i).getID();
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemViewType(int)
     */
    public int getItemViewType(int i) {
        return 0;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    @NonNull
    public View getView(int i, View v, ViewGroup vg) {
        FormRecord r = current.get(i);
        IncompleteFormRecordView ifrv = (IncompleteFormRecordView)v;
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

    /* (non-Javadoc)
     * @see android.widget.Adapter#getViewTypeCount()
     */
    public int getViewTypeCount() {
        return 1;
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
    
    public void setFormFilter(FormRecordFilter filter) {
        this.filter = filter;
    }
    
    @Nullable
    public FormRecordFilter getFilter() {
        return this.filter;
    }

    /**
     * Filter loaded FormRecords by those whose data contains any word in the
     * query field.
     *
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
            return;
        }

        // collect all forms that have text data that contains pieces.
        for (FormRecord r : records) {
            if (satisfiesQuery(r)) {
                current.add(r);
            }
        }
    }

    /**
     * Does the form record have text that contains one of the query segments?
     *
     * @param r Lookup this form record's text and compare to the current query
     *          segments.
     * @return Did the text corresponding to the form record argument contain
     * any of the query segments?
     */
    private boolean satisfiesQuery(@NonNull FormRecord r) {
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
    public void applyTextFilter(@NonNull String newQuery) {
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

    public void release() {
        if(loader.getStatus() == Status.RUNNING) {
            loader.cancel(false);
        }
    }

    public boolean isValid(int i) {
        return names.containsKey(current.get(i).getFormNamespace());
    }
}
