package org.commcare.android.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.javarosa.model.Text;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Pair;

/**
 * Loads textual information for a list of FormRecords.
 *
 * This text currently includes the form name, record title, and last modified
 * date
 *
 * @author ctsims
 *
 */
public class FormRecordLoaderTask extends AsyncTask<FormRecord, Pair<Integer, ArrayList<String>>, Integer> {

    private Hashtable<String,String> descriptorCache;
    private SqlStorage<SessionStateDescriptor> descriptorStorage;
    private AndroidCommCarePlatform platform;
    private Hashtable<Integer, String[]> searchCache;
    private Context context;

    // Functions to call when some or all of the data has been loaded.  Data
    // can be loaded normally, or be given precedence (priority), determining
    // which callback is dispatched to the listeners.
    private ArrayList<FormRecordLoadListener> listeners = new ArrayList<FormRecordLoadListener>();

    // These are all synchronized together
    private Queue<FormRecord> priorityQueue;

    // The IDs of FormRecords that have been loaded
    private HashSet<Integer> loaded;

    // Maps form namespace (unique id for forms) to their form title
    // (entry-point text). Needed because FormRecords don't have form title
    // info, but do have the namespace.
    private Hashtable<String, Text> formNames;

    // Is the background task done loading all the FormRecord information?
    private boolean loadingComplete = false;

    public FormRecordLoaderTask(Context c, SqlStorage<SessionStateDescriptor> descriptorStorage, AndroidCommCarePlatform platform) {
        this(c, descriptorStorage, null, platform);
    }

    public FormRecordLoaderTask(Context c, SqlStorage<SessionStateDescriptor> descriptorStorage, Hashtable<String,String> descriptorCache, AndroidCommCarePlatform platform) {
        this.context = c;
        this.descriptorStorage = descriptorStorage;
        this.descriptorCache = descriptorCache;
        this.platform = platform;
    }

    /**
     * Create a copy of this loader task.
     */
    public FormRecordLoaderTask spawn() {
        FormRecordLoaderTask task = new FormRecordLoaderTask(context, descriptorStorage, descriptorCache, platform);
        task.setListeners(listeners);
        return task;
    }

    /**
     * Pass in hashtables that will be used to store data that is loaded.
     *
     * @param searchCache maps FormRecord ID to an array of query-able form descriptor text
     * @param formNames map from form namespaces to their titles
     */
    public void init(Hashtable<Integer, String[]> searchCache, Hashtable<String, Text> formNames) {
        this.searchCache = searchCache;

        if (descriptorCache == null) {
            descriptorCache = new Hashtable<String, String>();
        }

        priorityQueue = new LinkedList<FormRecord>();
        loaded = new HashSet<Integer>();
        this.formNames = formNames;
    }

    /**
     * Set the listeners list, whose callbacks will be executed once the data
     * has been loaded.
     *
     * @param listeners a list of objects to call when data is done loading
     */
    public void setListeners(ArrayList<FormRecordLoadListener> listeners) {
        this.listeners.addAll(listeners);
    }

    /**
     * Add a listener to the list that is called once the data has been loaded.
     *
     * @param listener an objects to call when data is done loading
     */
    public void addListener(FormRecordLoadListener listener) {
        this.listeners.add(listener);
    }

    /*
     * (non-Javadoc)
     * @see android.os.AsyncTask#doInBackground(java.lang.Object[])
     */
    @Override
    protected Integer doInBackground(FormRecord... params) {
        int loadedFormCount = 0;

        // Load text information for every FormRecord passed in, unless task is
        // cancelled before that.
        while (loadedFormCount < params.length && !isCancelled()) {
            FormRecord current = null;
            synchronized(priorityQueue) {
                //If we have one to do immediately, grab it
                if (!priorityQueue.isEmpty()) {
                    current = priorityQueue.poll();
                    loaded.add(current.getID());

                    //Don't increment progress yet, we'll do so
                    //when we get to this record later.
                    // XXX: PLM: ^^ it is _very_ indirect how this occurs (by
                    // deffering to the conditional below). Instead of using
                    // loadedFormCount in the while-condition we should use
                    // loaded.size()
                }

                //If we don't need to jump the queue, grab the next one.
                if (current == null) {
                    current = params[loadedFormCount++];
                    // If we already loaded this record (due to priority),
                    // we don't need to go through this
                    if (loaded.contains(current.getID())) {
                        continue;
                    } else {
                        loaded.add(current.getID());
                    }
                }
            }
            // Otherwise, let's try to load some text about this record: last
            // modified date, title of the record, and form name
            ArrayList<String> recordTextDesc = new ArrayList<String>();

            // Get the date in a searchable format.
            recordTextDesc.add(android.text.format.DateUtils.formatDateTime(context, current.lastModified().getTime(), android.text.format.DateUtils.FORMAT_NO_MONTH_DAY | android.text.format.DateUtils.FORMAT_NO_YEAR).toLowerCase());

            // Grab our record hash
            SessionStateDescriptor ssd = null;
            try {
             ssd = descriptorStorage.getRecordForValue(SessionStateDescriptor.META_FORM_RECORD_ID, current.getID());
            } catch (NoSuchElementException nsee) {
                //s'all good
            }
            String dataTitle = "";
            if(ssd != null) {
                String descriptor = ssd.getSessionDescriptor();
                if (!descriptorCache.containsKey(descriptor)) {
                    AndroidSessionWrapper asw = new AndroidSessionWrapper(platform);
                    asw.loadFromStateDescription(ssd);
                    try {
                        dataTitle = asw.getTitle();
                    } catch (RuntimeException e){
                        dataTitle = "[Unavailable]";
                    }

                    if (dataTitle == null) {
                        dataTitle = "";
                    }

                    descriptorCache.put(descriptor, dataTitle);
                } else {
                    dataTitle = descriptorCache.get(descriptor);
                }
            }

            recordTextDesc.add(dataTitle);

            if (formNames.containsKey(current.getFormNamespace())) {
                Text name = formNames.get(current.getFormNamespace());
                recordTextDesc.add(name.evaluate());
            }

            // Copy data into search task and notify anything waiting on this
            // record.
            this.publishProgress(new Pair<Integer, ArrayList<String>>(current.getID(), recordTextDesc));
        }
        return 1;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // Tell users of the data being loaded that it isn't ready yet.
        this.loadingComplete = false;
    }

    /**
     * Has all the FormRecords' textual data been loaded yet? Used to let
     * users of the data only start accessing it once it is all there.
     */
    public boolean doneLoadingFormRecords() {
        return this.loadingComplete;
    }

    /* (non-Javadoc)
     * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        this.loadingComplete = true;

        for (FormRecordLoadListener listener : this.listeners) {
            if (listener != null) {
                listener.notifyLoaded();
            }
        }

        // free up things we don't need to spawn new tasks
        priorityQueue = null;
        loaded = null;
        formNames = null;
    }

    /* (non-Javadoc)
     * @see android.os.AsyncTask#onProgressUpdate(Progress[])
     */
    @Override
    protected void onProgressUpdate(Pair<Integer, ArrayList<String>>... values) {
        super.onProgressUpdate(values);

        // copy a single form record's data out of method arguments
        String[] vals = new String[values[0].second.size()];

        for (int i = 0; i < vals.length; ++i) {
            vals[i] = values[0].second.get(i);
        }

        // store the loaded data in the search cache
        this.searchCache.put(values[0].first, vals);

        for (FormRecordLoadListener listener : this.listeners) {
            if (listener != null) {
                // XXX: PLM: pretty sure loaded.contains(values[0].first) is always true at this point.
                // Should really refactor the following line so that this is
                // only called if we pulled the value from the priority queue
                listener.notifyPriorityLoaded(values[0].first, loaded.contains(values[0].first));
            }
        }
    }

    public boolean registerPriority(FormRecord record) {
        synchronized(priorityQueue) {
            if(loaded.contains(record.getID())) {
                return false;
            }
            //Otherwise, if we already have it in the queue, just move along
            else if(priorityQueue.contains(record)) {
                return true;
            } else {
                priorityQueue.add(record);
                return true;
            }
        }
    }

}
