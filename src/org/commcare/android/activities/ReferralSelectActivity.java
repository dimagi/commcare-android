/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Referral;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.util.CommCareSession;

import android.content.Intent;

/**
 * @author ctsims
 *
 */
public class ReferralSelectActivity extends EntitySelectActivity<Referral> {

	@Override
	protected SqlIndexedStorageUtility<Referral> getStorage() throws SessionUnavailableException{
		return CommCareApplication._().getStorage(Referral.STORAGE_KEY, Referral.class);
	}

	@Override
	protected Intent getDetailIntent(Referral r) {
		Intent i = new Intent(getApplicationContext(), ReferralDetailActivity.class);

        i.putExtra(CommCareSession.STATE_REFERRAL_ID, r.getReferralId());
        i.putExtra(AndroidCommCarePlatform.STATE_REFERRAL_TYPE, r.getType());
        return i;
	}

}
