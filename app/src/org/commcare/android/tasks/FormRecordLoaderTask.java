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
import org.commcare.suite.model.Text;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class FormRecordLoaderTask extends AsyncTask<FormRecord, Pair<Integer, ArrayList<String>>, Integer> {

    private Hashtable<String,String> descriptorCache;
    private SqlStorage<SessionStateDescriptor> descriptorStorage;
    private AndroidCommCarePlatform platform;
    private Hashtable<Integer, String[]> searchCache;
    private Context context;
    private FormRecordLoadListener listener;
    
    //These are all synchronized together
    private Queue<FormRecord> priorityQueue;
    private HashSet<Integer> loaded;
    
    private Hashtable<String,Text> formNames;
    
    public FormRecordLoaderTask(Context c, SqlStorage<SessionStateDescriptor> descriptorStorage, AndroidCommCarePlatform platform) {
        this(c, descriptorStorage, null, platform);
    }
    
    public FormRecordLoaderTask(Context c, SqlStorage<SessionStateDescriptor> descriptorStorage, Hashtable<String,String> descriptorCache, AndroidCommCarePlatform platform) {
        this.context = c;
        this.descriptorStorage = descriptorStorage;
        this.descriptorCache = descriptorCache;
        this.platform = platform;
    }
    
    public FormRecordLoaderTask spawn() {
        FormRecordLoaderTask task = new FormRecordLoaderTask(context, descriptorStorage, descriptorCache, platform);
        task.setListener(listener);
        return task;
    }
    
    public void init(Hashtable<Integer, String[]> searchCache, Hashtable<String,Text> formNames) {
        this.searchCache = searchCache;
        if(descriptorCache == null) {
            descriptorCache = new Hashtable<String,String>();
        }
        priorityQueue = new LinkedList<FormRecord>();
        loaded = new HashSet<Integer>();
        this.formNames = formNames;
    }
    
    public void setListener(FormRecordLoadListener listener) {
        this.listener = listener;
    }

    /*
     * (non-Javadoc)
     * @see android.os.AsyncTask#doInBackground(java.lang.Object[])
     */
    @Override
    protected Integer doInBackground(FormRecord... params) {
        int progress = 0;
        int target = params.length;
        while(progress < target && !isCancelled()) {
            FormRecord current = null;
            synchronized(priorityQueue) {
                //If we have one to do immediately, grab it
                if(!priorityQueue.isEmpty()) {
                    current = priorityQueue.poll();
                    loaded.add(current.getID());
                    
                    //Don't increment progress yet, we'll do so
                    //when we get to this record later.
                }
                
                //If we don't need to jump the queue, grab the next one.
                if(current == null) {
                    current = params[progress++];
                    //If we already loaded this record (due to priority), 
                    //we don't need to go through this
                    if(loaded.contains(current.getID())) {
                        continue;
                    } else {
                        loaded.add(current.getID());
                    }
                }
            }
            //Otherwise, let's get this record ready.
            
            ArrayList<String> cache = new ArrayList<String>();

            //Get the date in a searchable format.
            cache.add(android.text.format.DateUtils.formatDateTime(context, current.lastModified().getTime(), android.text.format.DateUtils.FORMAT_NO_MONTH_DAY | android.text.format.DateUtils.FORMAT_NO_YEAR).toLowerCase()); 
            
            //Grab our record hash
            SessionStateDescriptor ssd = null;
            try {
             ssd = descriptorStorage.getRecordForValue(SessionStateDescriptor.META_FORM_RECORD_ID, current.getID());
            } catch(NoSuchElementException nsee) {
                //s'all good
            }
            String dataTitle = "";
            if(ssd != null) {
                String descriptor = ssd.getSessionDescriptor();
                if(!descriptorCache.containsKey(descriptor)) {
                    AndroidSessionWrapper asw = new AndroidSessionWrapper(platform);
                    asw.loadFromStateDescription(ssd);
                    try{
                        dataTitle = asw.getTitle();
                    } catch(RuntimeException e){
                        dataTitle = "[Unavailable]";
                    }
                    dataTitle = dataTitle == null ? "" : dataTitle;
                    
                    descriptorCache.put(descriptor, dataTitle);
                }
                else {
                    dataTitle = descriptorCache.get(descriptor);
                }
            }
            
            cache.add(dataTitle);
            
            if(formNames.containsKey(current.getFormNamespace())) {
                Text name = formNames.get(current.getFormNamespace());
                cache.add(name.evaluate());
            }
            
            
            
            //Notify anyhting waiting on this record
            this.publishProgress(new Pair<Integer, ArrayList<String>>(current.getID(), cache));
        }
        return 1;
    }
    
    /* (non-Javadoc)
     * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        if(listener != null) {
            listener.notifyLoaded();
        }
        
        //free up everything except the cache, which we might use later.
        SqlStorage<SessionStateDescriptor> descriptorStorage = null;
        AndroidCommCarePlatform platform = null;
        Hashtable<Integer, String[]> searchCache = null;
        Context context = null;
        FormRecordLoadListener listener = null;
        
        //These are all synchronized together
        Queue<FormRecord> priorityQueue = null;
        HashSet<Integer> loaded = null;

    }
    
    /* (non-Javadoc)
     * @see android.os.AsyncTask#onProgressUpdate(Progress[])
     */
    @Override
    protected void onProgressUpdate(Pair<Integer, ArrayList<String>>... values) {
        super.onProgressUpdate(values);
        String[] vals = new String[values[0].second.size()];
        for(int i = 0 ; i < vals.length ; ++i) {
            vals[i] = values[0].second.get(i);
        }
        this.searchCache.put(values[0].first, vals);
        if(listener != null) {
            listener.notifyPriorityLoaded(values[0].first, loaded.contains(values[0].first));
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
