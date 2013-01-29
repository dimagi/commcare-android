/**
 * 
 */
package org.commcare.android.tasks;

import org.apache.http.HttpResponse;
import org.commcare.android.database.user.models.User;
import org.commcare.android.tasks.templates.HttpCalloutTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.xml.CommCareTransactionParserFactory;

import android.content.Context;

/**
 * This task is responsible for taking user credentials and attempting to
 * log in a user with local data. If the credentials represent a user who
 * doesn't exist, this task will attempt to fetch and create a key record
 * for the user specified.
 * 
 * @author ctsims
 *
 */
public class ManageKeyRecordTask extends HttpCalloutTask {
	String username; 
	String password;
	
	protected boolean proceed = false;
	
	public ManageKeyRecordTask(Context c, String username, String password) {
		super(c);
		this.username = username;
		this.password = password;
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.HttpCalloutTask#doSetupTaskBeforeRequest()
	 */
	@Override
	protected boolean doSetupTaskBeforeRequest() {
		try {
			User u = CommCareApplication._().getSession().getLoggedInUser();
			if(u == null) {
				CommCareApplication._().logout();
				proceed = false;
				return true;
			}
			u.setCachedPwd(password);
			proceed = true;
			return true;
		} catch(SessionUnavailableException sue) {
			CommCareApplication._().logout();
			proceed = false;
			return true;
		}
	}

	
	//CTS: These will be fleshed out to comply with the server's Key Request/response protocol
	

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.HttpCalloutTask#doHttpRequest()
	 */
	@Override
	protected HttpResponse doHttpRequest() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.HttpCalloutTask#getTransactionParserFactory()
	 */
	@Override
	protected CommCareTransactionParserFactory getTransactionParserFactory() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.HttpCalloutTask#doResponseOther(org.apache.http.HttpResponse)
	 */
	@Override
	protected HttpCalloutOutcomes doResponseOther(HttpResponse response) {
		// TODO Auto-generated method stub
		return null;
	}

}
