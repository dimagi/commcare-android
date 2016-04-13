package org.commcare.tasks;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.Pair;

import org.commcare.CommCareApplication;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.ACase;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.models.database.user.models.SessionStateDescriptor;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Text;
import org.commcare.tasks.templates.ManagedAsyncTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.expr.XPathEqExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathStringLiteral;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Vector;
import java.util.Set;

/**
 * Loads textual information for a list of FormRecords.
 * <p/>
 * This text currently includes the form name, record title, and last modified
 * date
 *
 * @author ctsims
 */
public class FormRecordLoaderTask extends ManagedAsyncTask<FormRecord, Pair<FormRecord, ArrayList<String>>, Integer> {

    private Hashtable<String, String> descriptorCache;
    private final SqlStorage<SessionStateDescriptor> descriptorStorage;
    private final AndroidCommCarePlatform platform;
    private Hashtable<Integer, String[]> searchCache;
    private final Context context;

    // Functions to call when some or all of the data has been loaded.  Data
    // can be loaded normally, or be given precedence (priority), determining
    // which callback is dispatched to the listeners.
    private final ArrayList<FormRecordLoadListener> listeners = new ArrayList<>();

    // These are all synchronized together
    final private Queue<FormRecord> priorityQueue = new LinkedList<>();

    // The IDs of FormRecords that have been loaded
    private final Set<Integer> loaded = new HashSet<>();

    // Maps form namespace (unique id for forms) to their form title
    // (entry-point text). Needed because FormRecords don't have form title
    // info, but do have the namespace.
    private Hashtable<String, Text> formNames;

    // Is the background task done loading all the FormRecord information?
    private boolean loadingComplete = false;

    public FormRecordLoaderTask(Context c, SqlStorage<SessionStateDescriptor> descriptorStorage, AndroidCommCarePlatform platform) {
        this(c, descriptorStorage, null, platform);
    }

    private FormRecordLoaderTask(Context c, SqlStorage<SessionStateDescriptor> descriptorStorage, Hashtable<String, String> descriptorCache, AndroidCommCarePlatform platform) {
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
     * @param formNames   map from form namespaces to their titles
     */
    public void init(Hashtable<Integer, String[]> searchCache, Hashtable<String, Text> formNames) {
        this.searchCache = searchCache;

        if (descriptorCache == null) {
            descriptorCache = new Hashtable<>();
        }

        priorityQueue.clear();
        loaded.clear();
        this.formNames = formNames;
    }

    /**
     * Set the listeners list, whose callbacks will be executed once the data
     * has been loaded.
     *
     * @param listeners a list of objects to call when data is done loading
     */
    private void setListeners(ArrayList<FormRecordLoadListener> listeners) {
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

    @Override
    protected Integer doInBackground(FormRecord... params) {
        // Load text information for every FormRecord passed in, unless task is
        // cancelled before that.
        FormRecord current;
        int loadedFormCount = 0;
        while (loadedFormCount < params.length && !isCancelled()) {
            synchronized (priorityQueue) {
                //If we have one to do immediately, grab it
                if (!priorityQueue.isEmpty()) {
                    current = priorityQueue.poll();
                } else {
                    current = params[loadedFormCount++];
                }
                if (loaded.contains(current.getID())) {
                    // skip if we already loaded this record due to priority queue
                    continue;
                }
            }
            // load text about this record: last modified date, title of the record, and form name
            ArrayList<String> recordTextDesc = loadRecordText(current);

            loaded.add(current.getID());
            // Copy data into search task and notify anything waiting on this
            // record.
            this.publishProgress(new Pair<>(current, recordTextDesc));
        }
        return 1;
    }

    private ArrayList<String> loadRecordText(FormRecord current) {
        ArrayList<String> recordTextDesc = new ArrayList<>();
        // Get the date in a searchable format.
        recordTextDesc.add(DateUtils.formatDateTime(context, current.lastModified().getTime(), DateUtils.FORMAT_NO_MONTH_DAY | DateUtils.FORMAT_NO_YEAR).toLowerCase());

        String dataTitle = loadDataTitle(current.getID());
        recordTextDesc.add(dataTitle);

        if (formNames.containsKey(current.getFormNamespace())) {
            Text name = formNames.get(current.getFormNamespace());
            recordTextDesc.add(name.evaluate());
        }
        return recordTextDesc;
    }

    private String loadDataTitle(int formRecordId) {
        // Grab our record hash
        SessionStateDescriptor ssd = null;
        try {
            ssd = descriptorStorage.getRecordForValue(SessionStateDescriptor.META_FORM_RECORD_ID, formRecordId);
        } catch (NoSuchElementException nsee) {
            //s'all good
        }
        String dataTitle = "";
        if (ssd != null) {
            String descriptor = ssd.getSessionDescriptor();
            if (!descriptorCache.containsKey(descriptor)) {
                AndroidSessionWrapper asw = new AndroidSessionWrapper(platform);
                asw.loadFromStateDescription(ssd);
                try {
                    dataTitle = getTitleFromSession(asw);
                } catch (RuntimeException e) {
                    dataTitle = "[Unavailable]";
                }

                if (dataTitle == null) {
                    dataTitle = "";
                }

                descriptorCache.put(descriptor, dataTitle);
            } else {
                return descriptorCache.get(descriptor);
            }
        }
        return dataTitle;
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
        priorityQueue.clear();
        loaded.clear();
        formNames = null;
    }

    @Override
    protected void onProgressUpdate(Pair<FormRecord, ArrayList<String>>... values) {
        super.onProgressUpdate(values);

        // copy a single form record's data out of method arguments
        String[] vals = new String[values[0].second.size()];

        for (int i = 0; i < vals.length; ++i) {
            vals[i] = values[0].second.get(i);
        }

        // store the loaded data in the search cache
        this.searchCache.put(values[0].first.getID(), vals);

        for (FormRecordLoadListener listener : this.listeners) {
            if (listener != null) {
                // XXX: PLM: pretty sure loaded.contains(values[0].first) is
                // always true at this point.
                listener.notifyPriorityLoaded(values[0].first,
                        loaded.contains(values[0].first.getID()));
            }
        }
    }

    public boolean registerPriority(FormRecord record) {
        synchronized (priorityQueue) {
            if (loaded.contains(record.getID())) {
                return false;
            } else if (priorityQueue.contains(record)) {
                // if we already have it in the queue, just move along
                return true;
            } else {
                priorityQueue.add(record);
                return true;
            }
        }
    }

    private static String getTitleFromSession(AndroidSessionWrapper androidSessionWrapper) {
        //TODO: Most of this mimicks what we need to do in entrydetail activity, remove it from there
        //and generalize the walking

        // get a copy of the session
        CommCareSession session = new CommCareSession(androidSessionWrapper.getSession());

        // Walk backwards until we find something with a long detail
        while (session.getFrame().getSteps().size() > 0 &&
                (!SessionFrame.STATE_DATUM_VAL.equals(session.getNeededData()) ||
                        session.getNeededDatum().getLongDetail() == null)) {
            session.stepBack();
        }
        if (session.getFrame().getSteps().size() == 0) {
            return null;
        }

        EvaluationContext ec = androidSessionWrapper.getEvaluationContext();

        //Get the value that was chosen for this item
        String value = session.getPoppedStep().getValue();

        SessionDatum datum = session.getNeededDatum();

        //Now determine what nodeset that was going to be used to load this select
        TreeReference nodesetRef = datum.getNodeset().clone();
        Vector<XPathExpression> predicates = nodesetRef.getPredicate(nodesetRef.size() - 1);
        predicates.add(new XPathEqExpr(XPathEqExpr.EQ, XPathReference.getPathExpr(datum.getValue()), new XPathStringLiteral(value)));

        Vector<TreeReference> elements = ec.expandReference(nodesetRef);

        //If we got our ref, awesome. Otherwise we need to bail.
        if (elements.size() != 1) {
            return null;
        }

        //Now generate a context for our element
        EvaluationContext element = new EvaluationContext(ec, elements.firstElement());


        //Ok, so get our Text.
        Text t = session.getDetail(datum.getLongDetail()).getTitle().getText();
        boolean isPrettyPrint = true;

        //CTS: this is... not awesome.
        //But we're going to use this to test whether we _need_ an evaluation context
        //for this. (If not, the title doesn't have prettyprint for us)
        try {
            String outcome = t.evaluate();
            if (outcome != null) {
                isPrettyPrint = false;
            }
        } catch (Exception e) {
            //Cool. Got us a fancy string.
        }

        if (isPrettyPrint) {
            //Now just get the detail title for that element
            return t.evaluate(element);
        } else {
            //Otherwise, this is _almost certainly_ a case. See if it is, and
            //if so, grab the case name. otherwise, who knows?
            SqlStorage<ACase> storage = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class);
            try {
                ACase ourCase = storage.getRecordForValue(ACase.INDEX_CASE_ID, value);
                if (ourCase != null) {
                    return ourCase.getName();
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
    }


}
