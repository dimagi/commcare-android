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
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.model.Case;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.DataUtil;

/**
 * @author ctsims
 *
 */
public class AndroidCaseInstanceTreeElement extends CaseInstanceTreeElement {
    SqlStorageIterator<ACase> iter;
    
    public AndroidCaseInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<ACase> storage, boolean reportMode) {
        super(instanceRoot, storage, reportMode);
    }
    
    
    protected synchronized void getCases() {
        if(cases != null) {
            return;
        }
        objectIdMapping = new Hashtable<Integer, Integer>();
        cases = new Vector<CaseChildElement>();
        int mult = 0;
        for(IStorageIterator i = ((SqlStorage<ACase>)storage).iterate(false); i.hasMore();) {
            int id = i.nextID();
            cases.addElement(new CaseChildElement(this, id, null, mult));
            objectIdMapping.put(DataUtil.integer(id), DataUtil.integer(mult));
            mult++;
        }
    }
    
    
    /*
     * (non-Javadoc)
     * @see org.commcare.cases.util.StorageBackedTreeRoot#union(java.util.Vector, java.util.Vector)
     */
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
    
    @Override
    protected Vector<Integer> getMatchesForValue(String filterIndex, Object o, IStorageUtilityIndexed<?> storage, Vector<String> previousFetchKeys, Vector<Object> previousFetchValues) {
        SqlStorage<ACase> sqlStorage = ((SqlStorage<ACase>)storage);
        String[] names = new String[previousFetchKeys.size() + 1];
        String[] values = new String[previousFetchValues.size() + 1];
        int i = 0;
        for(i = 0 ; i < previousFetchKeys.size(); ++i) {
            names[i] = previousFetchKeys.elementAt(i);
            values[i] = (String)previousFetchValues.elementAt(i);
        }
        names[i] = filterIndex;
        values[i] = (String)o;
        
        
        return sqlStorage.getIDsForValues(names, values);
    }
}
