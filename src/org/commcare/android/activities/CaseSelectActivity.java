/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Case;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.util.CommCareSession;

import android.content.Intent;

/**
 * @author ctsims
 *
 */
public class CaseSelectActivity extends EntitySelectActivity<Case> {

	protected SqlIndexedStorageUtility<Case> getStorage() throws SessionUnavailableException{
		return CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class);
	}
	
	protected Intent getDetailIntent(Case c) {
		Intent i = new Intent(getApplicationContext(), CaseDetailActivity.class);

        i.putExtra(CommCareSession.STATE_CASE_ID, c.getCaseId());
        return i;
	}

	@Override
	protected Case getEntityFromID(String uniqueid) throws SessionUnavailableException {
		SqlIndexedStorageUtility<Case> storage = getStorage();
		try { 
			return storage.getRecordForValue(Case.META_CASE_ID, uniqueid);
		} catch(Exception e) {
			return null;
		}
	}
}
