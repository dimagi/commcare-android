/**
 * 
 */
package org.commcare.entity;

import java.util.Hashtable;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Case;
import org.commcare.android.models.Referral;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Filter;
import org.javarosa.core.services.storage.EntityFilter;

/**
 * @author ctsims
 *
 */
public class ReferralEntityFilter extends EntityFilter<Referral> {
	
	Filter primary;
	SqlIndexedStorageUtility<Case> storage;
	
	public ReferralEntityFilter(Filter primary, SqlIndexedStorageUtility<Case> storage) {
		this.primary = primary;
		this.storage = storage;
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
				//Can't handle this with meta data here.
				return EntityFilter.PREFILTER_FILTER;
			}
		}
		return EntityFilter.PREFILTER_FILTER;
	}

	public boolean matches(Referral r) {
		if(primary.isEmpty()) {
			return r.isPending();
		} else {
			//Apparently meta data isn't supported yet, so we have to do this here, too...
			if(primary.paramSet(Filter.TYPE)) {
				if(!storage.getRecordForValue(Case.META_CASE_ID,r.getLinkedId()).getTypeId().equals(primary.getParam(Filter.TYPE))) {
					return false;
				}
			}
			
			if(!r.isPending()) {
				if(primary.paramSet(Filter.SHOW_RESOLVED)) {
					return new Boolean(true).toString().equals(primary.getParam(Filter.SHOW_RESOLVED));
				} else {
					return false;
				}
			}
			
			//TODO: Only admin or user
			return true;
		}
	}
}
