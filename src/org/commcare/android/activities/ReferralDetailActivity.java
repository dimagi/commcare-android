/**
 * 
 */
package org.commcare.android.activities;

import java.util.Vector;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Referral;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.util.CommCareSession;

import android.content.Intent;

/**
 * @author ctsims
 *
 */
public class ReferralDetailActivity extends EntityDetailActivity<Referral> {

	@Override
	protected Referral readObjectFromIncomingIntent(Intent i) {
		String id = getIntent().getStringExtra(CommCareSession.STATE_REFERRAL_ID);
		String type = getIntent().getStringExtra(AndroidCommCarePlatform.STATE_REFERRAL_TYPE);

		SqlIndexedStorageUtility<Referral> storage = CommCareApplication._().getStorage(Referral.STORAGE_KEY, Referral.class);
		
		Vector<Integer> ids = storage.getIDsForValues(new String[] {Referral.REFERRAL_ID, Referral.REFERRAL_TYPE} , new String[] {id, type});
		
		assert(ids.size() == 1);
		
		return storage.read(ids.elementAt(0));
	}

	@Override
	protected void loadOutgoingIntent(Intent i) {
		i.putExtra(CommCareSession.STATE_REFERRAL_ID, entity.getElement().getReferralId());
		i.putExtra(AndroidCommCarePlatform.STATE_REFERRAL_TYPE, entity.getElement().getType());
	}

}
