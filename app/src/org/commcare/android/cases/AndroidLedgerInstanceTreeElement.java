/**
 * 
 */
package org.commcare.android.cases;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.instance.CaseChildElement;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.ledger.instance.LedgerChildElement;
import org.commcare.cases.ledger.instance.LedgerInstanceTreeElement;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.DataUtil;

/**
 * @author ctsims
 *
 */
public class AndroidLedgerInstanceTreeElement extends LedgerInstanceTreeElement {
	SqlStorageIterator<Ledger> iter;
	
	public AndroidLedgerInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<Ledger> storage) {
		super(instanceRoot, storage);
	}
	
	
	protected synchronized void getLedgers() {
		if(ledgers != null) {
			return;
		}
		objectIdMapping = new Hashtable<Integer, Integer>();
		ledgers = new Vector<LedgerChildElement>();
		int mult = 0;
		for(IStorageIterator i = ((SqlStorage<ACase>)getStorage()).iterate(false); i.hasMore();) {
			int id = i.nextID();
			ledgers.addElement(new LedgerChildElement(this, id, null, mult));
			objectIdMapping.put(DataUtil.integer(id), DataUtil.integer(mult));
			mult++;
		}
	}
	
	
	@Override
	protected Vector<Integer> union(Vector<Integer> selectedCases, Vector<Integer> cases) {
		//This is kind of (ok, so really) awkward looking, but we can't use sets in 
		//ccj2me (Thanks, Nokia!) also, there's no _collections_ interface in
		//j2me (thanks Sun!) so this is what we get.
		HashSet<Integer> selected = new HashSet<Integer>(selectedCases);
		selected.addAll(selectedCases);
		
		HashSet<Integer> other = new HashSet<Integer>();
		other.addAll(cases);
		
		selected.retainAll(other);
		
		selectedCases.clear();
		selectedCases.addAll(selected);
		return selectedCases;
	}
}
