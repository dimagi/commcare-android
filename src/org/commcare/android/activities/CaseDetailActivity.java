/**
 * 
 */
package org.commcare.android.activities;

import java.util.Vector;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.Case;
import org.commcare.android.models.Entity;
import org.commcare.android.models.EntityFactory;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.suite.model.Entry;
import org.commcare.util.CommCareSession;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;

/**
 * @author ctsims
 *
 */
public class CaseDetailActivity extends EntityDetailActivity<Case> {

	@Override
	protected Case readObjectFromIncomingIntent(Intent i) {
		String id = getIntent().getStringExtra(CommCareSession.STATE_CASE_ID);
		
		return CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class).getRecordForValue(Case.META_CASE_ID, id);
	}

	@Override
	protected void loadOutgoingIntent(Intent i) {
		i.putExtra(CommCareSession.STATE_CASE_ID, entity.getElement().getCaseId());		
	}
}
