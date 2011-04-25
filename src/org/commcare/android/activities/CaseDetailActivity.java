/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.Case;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.util.CommCareSession;

import android.content.Intent;

/**
 * @author ctsims
 *
 */
public class CaseDetailActivity extends EntityDetailActivity<Case> {

	@Override
	protected Case readObjectFromIncomingIntent(Intent i) throws SessionUnavailableException{
		String id = getIntent().getStringExtra(CommCareSession.STATE_CASE_ID);
		
		return CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class).getRecordForValue(Case.META_CASE_ID, id);
	}

	@Override
	protected void loadOutgoingIntent(Intent i) {
		i.putExtra(CommCareSession.STATE_CASE_ID, entity.getElement().getCaseId());		
	}
}
