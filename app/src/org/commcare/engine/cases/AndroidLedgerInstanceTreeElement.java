package org.commcare.engine.cases;

import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.instance.LedgerInstanceTreeElement;
import org.commcare.cases.query.PredicateProfile;
import org.commcare.cases.query.QueryContext;
import org.commcare.cases.util.QueryUtils;
import org.commcare.cases.query.handlers.StaticLookupQueryHandler;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.SqlStorageIterator;
import org.commcare.android.database.user.models.ACase;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.DataUtil;
import org.javarosa.xpath.expr.XPathExpression;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class AndroidLedgerInstanceTreeElement extends LedgerInstanceTreeElement {

    private Hashtable<String, Integer> primaryIdMapping;

    public AndroidLedgerInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<Ledger> storage) {
        super(instanceRoot, storage);
        primaryIdMapping = null;
        addStaticQueryHandler();
    }

    private void addStaticQueryHandler() {
        this.getQueryPlanner().addQueryHandler(new StaticLookupQueryHandler() {
            @Override
            public Collection<PredicateProfile> collectPredicateProfiles(Vector<XPathExpression> predicates, QueryContext context, EvaluationContext evaluationContext) {
                return null;
            }

            @Override
            protected boolean canHandle(String attributeName) {
                return attributeName.equals(Ledger.INDEX_ENTITY_ID) && primaryIdMapping != null;
            }

            @Override
            protected Vector<Integer> getMatches(String attributeName, String valueToMatch) {
                return QueryUtils.wrapSingleResult(primaryIdMapping.get(valueToMatch));
            }
        });
    }

    @Override
    protected synchronized void loadElements() {
        if (elements != null) {
            return;
        }
        elements = new Vector<>();
        primaryIdMapping = new Hashtable<>();
        int mult = 0;
        for (IStorageIterator i = ((SqlStorage<ACase>)getStorage()).iterate(false, new String[]{Ledger.INDEX_ENTITY_ID}); i.hasMore(); ) {
            int id = i.peekID();
            elements.addElement(buildElement(this, id, null, mult));
            objectIdMapping.put(DataUtil.integer(id), DataUtil.integer(mult));
            primaryIdMapping.put(((SqlStorageIterator)i).getIncludedMetadata(Ledger.INDEX_ENTITY_ID), DataUtil.integer(id));
            mult++;
            i.nextID();
        }
    }
}
