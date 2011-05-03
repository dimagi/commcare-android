/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.adapters.CallRecordAdapter;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.util.SessionUnavailableException;
import org.javarosa.core.services.storage.Persistable;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * @author ctsims
 *
 */
public class CallLogActivity<T extends Persistable> extends ListActivity {
	
	LinearLayout header;
	CallRecordAdapter adapter;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(getString(R.string.app_name) + " > " + "Call Logs");
        
        refreshView();
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	try {
    		adapter = new CallRecordAdapter(this, managedQuery(android.provider.CallLog.Calls.CONTENT_URI,null, null, null, Calls.DATE + " DESC"));
    		this.setListAdapter(adapter);
    	} catch(SessionUnavailableException sue) {
    		//TODO: login and return
    	}
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
    	String number = (String)adapter.getItem(position);
        Intent detail = CommCareApplication._().getCallListener().getDetailIntent(this,number);
        if(detail == null) {
        	//Start normal callout activity
        	Intent i = new Intent(this, CallOutActivity.class);
        	i.putExtra(CallOutActivity.PHONE_NUMBER, number);
        	startActivity(i);
        } else {
        	startActivity(detail);
        }
        
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
//    	switch(requestCode){
//    	case CONFIRM_SELECT:
//    		if(resultCode == RESULT_OK) {
//    	        // create intent for return and store path
//    	        Intent i = new Intent(this.getIntent());
//    	        
//    	        i.putExtras(intent.getExtras());
//    	        setResult(RESULT_OK, i);
//
//    	        finish();
//        		return;
//    		} else {
//    	        Intent i = new Intent(this.getIntent());
//    	        setResult(RESULT_CANCELED, i);
//        		return;
//    		}
//    	default:
//    		super.onActivityResult(requestCode, resultCode, intent);
//    	}
//    }
}
