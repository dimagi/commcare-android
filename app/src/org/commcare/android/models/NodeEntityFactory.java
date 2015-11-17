package org.commcare.android.models;

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
        getDetail().populateEvaluationContextVariables(nodeContext);
        //return new AsyncEntity<TreeReference>(detail.getFields(), nodeContext, data);

        int length = detail.getHeaderForms().length;
        Object[] details = new Object[length];
        String[] sortDetails = new String[length];
        String[] backgroundDetails = new String[length];
        boolean[] relevancyDetails = new boolean[length];
        int count = 0;
        for (DetailField f : this.getDetail().getFields()) {
            try {
                details[count] = f.getTemplate().evaluate(nodeContext);
                Text sortText = f.getSort();
                Text backgroundText = f.getBackground();
                if (sortText == null) {
                    sortDetails[count] = null;
                } else {
                    sortDetails[count] = sortText.evaluate(nodeContext);
                }
                if (backgroundText == null) {
                    backgroundDetails[count] = "";
                } else {
                    backgroundDetails[count] = backgroundText.evaluate(nodeContext);
                }
                relevancyDetails[count] = f.isRelevant(nodeContext);
            } catch (XPathSyntaxException | XPathException xpe) {
                xpe.printStackTrace();
                details[count] = "<invalid xpath: " + xpe.getMessage() + ">";
                backgroundDetails[count] = "";
                // assume that if there's an error, user should see it
                relevancyDetails[count] = true;
            }
            count++;
        }

        return new Entity<>(details, sortDetails, backgroundDetails, relevancyDetails, data);
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
