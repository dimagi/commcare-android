package org.commcare.models;

import org.commcare.logging.XPathErrorLogger;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.util.List;

/**
 * @author ctsims
 */
public class NodeEntityFactory {
    private boolean mEntitySetInitialized = false;
    private static final Object mPreparationLock = new Object();

    protected final EvaluationContext ec;
    protected final Detail detail;

    public NodeEntityFactory(Detail d, EvaluationContext ec) {
        this.detail = d;
        this.ec = ec;
    }

    public Detail getDetail() {
        return detail;
    }

    public Entity<TreeReference> getEntity(TreeReference data) {
        EvaluationContext nodeContext = new EvaluationContext(ec, data);
        detail.populateEvaluationContextVariables(nodeContext);

        int length = detail.getHeaderForms().length;
        String extraKey = loadExternalDataKey(nodeContext);

        Object[] details = new Object[length];
        String[] sortDetails = new String[length];
        boolean[] relevancyDetails = new boolean[length];
        int count = 0;
        for (DetailField f : detail.getFields()) {
            try {
                details[count] = f.getTemplate().evaluate(nodeContext);
                Text sortText = f.getSort();
                if (sortText == null) {
                    sortDetails[count] = null;
                } else {
                    sortDetails[count] = sortText.evaluate(nodeContext);
                }
                relevancyDetails[count] = f.isRelevant(nodeContext);
            } catch (XPathSyntaxException e) {
                storeErrorDetails(e, count, details, relevancyDetails);
            } catch (XPathException xpe) {
                XPathErrorLogger.INSTANCE.logErrorToCurrentApp(xpe);
                storeErrorDetails(xpe, count, details, relevancyDetails);
            }
            count++;
        }

        return new Entity<>(details, sortDetails, relevancyDetails, data, extraKey);
    }

    /**
     * Evaluate the lookup's 'template' detail block and use result as key for
     * attaching external data to the entity.
     */
    protected String loadExternalDataKey(EvaluationContext nodeContext) {
        if (detail.getCallout() != null) {
            DetailField calloutResponseDetail = detail.getCallout().getResponseDetail();
            if (calloutResponseDetail != null) {
                Object template = calloutResponseDetail.getTemplate().evaluate(nodeContext);
                if (template instanceof String) {
                    return (String)template;
                }
            }
        }
        return null;
    }

    private static void storeErrorDetails(Exception e, int index,
                                          Object[] details,
                                          boolean[] relevancyDetails) {
        e.printStackTrace();
        details[index] = "<invalid xpath: " + e.getMessage() + ">";
        // assume that if there's an error, user should see it
        relevancyDetails[index] = true;
    }

    public List<TreeReference> expandReferenceList(TreeReference treeReference) {
        return ec.expandReference(treeReference);
    }

    /**
     * Performs the underlying work to prepare the entity set
     * (see prepareEntities()). Separated out to enforce timing
     * related to preparing and utilizing results
     */
    protected void prepareEntitiesInternal() {
        //No implementation in normal factory
    }

    /**
     * Optional: Allows the factory to make all of the entities that it has
     * returned "Ready" by performing any lazy evaluation needed for optimum
     * usage. This preparation occurs asynchronously, and the returned entity
     * set should not be manipulated until it has completed.
     */
    public final void prepareEntities() {
        synchronized (mPreparationLock) {
            prepareEntitiesInternal();
            mEntitySetInitialized = true;
        }
    }

    /**
     * Performs the underlying work to check on the entitySet preparation
     * (see isEntitySetReady()). Separated out to enforce timing
     * related to preparing and utilizing results
     */
    protected boolean isEntitySetReadyInternal() {
        return true;
    }

    /**
     * Called only after a call to prepareEntities, this signals whether
     * the entities returned are ready for bulk operations.
     *
     * @return True if entities returned from the factory are again ready
     * for use. False otherwise.
     */
    public final boolean isEntitySetReady() {
        synchronized (mPreparationLock) {
            if (!mEntitySetInitialized) {
                throw new RuntimeException("A Node Entity Factory was not prepared before usage. prepareEntities() must be called before a call to isEntitySetReady()");
            }
            return isEntitySetReadyInternal();
        }
    }
}
