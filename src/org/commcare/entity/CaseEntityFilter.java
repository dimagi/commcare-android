/**
 * 
 */
package org.commcare.entity;

import java.util.Hashtable;

import org.commcare.android.models.Case;
import org.commcare.suite.model.Filter;
import org.javarosa.core.services.storage.EntityFilter;

/**
 * @author ctsims
 *
 */
public class CaseEntityFilter extends EntityFilter<Case> {
	
	Filter primary;
	
	public CaseEntityFilter(Filter primary) {
		this.primary = primary;
	}
		
	public int preFilter(int id, Hashtable<String, Object> metaData) {
		// this apparently isn't supported yet
		if(metaData == null) {
			return EntityFilter.PREFILTER_FILTER;
		}
		if(primary.isEmpty()) {
			return EntityFilter.PREFILTER_FILTER;
		} else {
			if(primary.paramSet(Filter.TYPE)) {
				if(!metaData.get("case-type").equals(primary.getParam(Filter.TYPE))) {
					return EntityFilter.PREFILTER_EXCLUDE;
				}
			}
		}
		return EntityFilter.PREFILTER_FILTER;
	}

	public boolean matches(Case c) {
		if(primary.isEmpty()) {
			return !c.isClosed();
		} else {
			//Apparently meta data isn't supported yet, so we have to do this here, too...
			if(primary.paramSet(Filter.TYPE)) {
				if(!c.getTypeId().equals(primary.getParam(Filter.TYPE))) {
					return false;
				}
			}
			
			if(c.isClosed()) {
				if(primary.paramSet(Filter.SHOW_CLOSED)) {
					return new Boolean(true).toString().equals(primary.getParam(Filter.SHOW_CLOSED));
				} else {
					return false;
				}
			}
			
			//TODO: Only admin or user
			return true;
		}
	}
}
