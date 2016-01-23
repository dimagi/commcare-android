package org.commcare.android.cases;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.ledger.instance.LedgerChildElement;
import org.commcare.cases.ledger.instance.LedgerInstanceTreeElement;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.DataUtil;

import java.util.HashSet;
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
    }

    @Override
    protected Hashtable<String, Integer> getKeyMapping(String keyId) {
        if (keyId.equals(Ledger.INDEX_ENTITY_ID) && primaryIdMapping != null) {
            return primaryIdMapping;
        } else {
            return null;
        }
    }

    @Override
    protected synchronized void getLedgers() {
        if (ledgers != null) {
            return;
        }
        objectIdMapping = new Hashtable<>();
        ledgers = new Vector<>();
        primaryIdMapping = new Hashtable<>();
        int mult = 0;
        for (IStorageIterator i = ((SqlStorage<ACase>) getStorage()).iterate(false, Ledger.INDEX_ENTITY_ID); i.hasMore(); ) {
            int id = i.peekID();
            ledgers.addElement(new LedgerChildElement(this, id, null, mult));
            objectIdMapping.put(DataUtil.integer(id), DataUtil.integer(mult));
            primaryIdMapping.put(((SqlStorageIterator) i).getPrimaryId(), DataUtil.integer(id));
            mult++;
            i.nextID();
        }
    }


    @Override
    protected Vector<Integer> union(Vector<Integer> selectedCases, Vector<Integer> cases) {
        //This is kind of (ok, so really) awkward looking, but we can't use sets in 
        //ccj2me (Thanks, Nokia!) also, there's no _collections_ interface in
        //j2me (thanks Sun!) so this is what we get.
        HashSet<Integer> selected = new HashSet<>(selectedCases);
        selected.addAll(selectedCases);

        HashSet<Integer> other = new HashSet<>();
        other.addAll(cases);

        selected.retainAll(other);

        selectedCases.clear();
        selectedCases.addAll(selected);
        return selectedCases;
    }
}
