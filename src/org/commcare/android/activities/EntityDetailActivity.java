/**
 * 
 */
package org.commcare.android.activities;

import java.util.Vector;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.Entity;
import org.commcare.android.models.EntityFactory;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.suite.model.Entry;
import org.commcare.util.CommCareSession;
import org.javarosa.core.services.storage.Persistable;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public abstract class EntityDetailActivity<T extends Persistable> extends ListActivity implements DetailCalloutListener {
	private AndroidCommCarePlatform platform;
	
	private static final int CALL_OUT = 0;
		
	Entry prototype;
	
	Entity<T> entity;
	
	EntityDetailAdapter adapter;
	EntityFactory<T> factory;
	
	Button next;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.entity_detail);
        next = (Button)findViewById(R.id.entity_select_button);
        next.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
		        Intent i = new Intent(EntityDetailActivity.this.getIntent());
		        loadOutgoingIntent(i);
		        setResult(RESULT_OK, i);

		        finish();
			}
        	
        });
        
        
        platform = CommCareApplication._().getCommCarePlatform();
        
        String passedCommand = getIntent().getStringExtra(CommCareSession.STATE_COMMAND_ID);
        
		Vector<Entry> entries = platform.getSession().getEntriesForCommand(passedCommand == null ? platform.getSession().getCommand() : passedCommand);
		prototype = entries.elementAt(0);

        factory = new EntityFactory<T>(platform.getSession().getDetail(prototype.getLongDetailId()), platform.getLoggedInUser());
		
	    entity = factory.getEntity(readObjectFromIncomingIntent(getIntent()));
        
        setTitle(getString(R.string.app_name) + " > " + "Details");
        
        refreshView();
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	adapter = new EntityDetailAdapter(this, platform, factory.getDetail(), entity, this);
    	setListAdapter(adapter);
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        //Shouldn't be possible
    }
    
    protected abstract T readObjectFromIncomingIntent(Intent i);
    
    protected abstract void loadOutgoingIntent(Intent i);
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	switch(requestCode) {
    	case CALL_OUT:
    		if(resultCode == RESULT_CANCELED) {
    			refreshView();
    			return;
    		} else {
    			long duration = intent.getLongExtra(CallOutActivity.CALL_DURATION, 0);
    			
		        Intent i = new Intent(EntityDetailActivity.this.getIntent());
		        loadOutgoingIntent(i);
		        i.putExtra(CallOutActivity.CALL_DURATION, duration);
		        setResult(RESULT_OK, i);

		        finish();
		        return;
    		}
    	default:
    		super.onActivityResult(requestCode, resultCode, intent);
    	}
    }


	public void callRequested(String phoneNumber) {
		Intent intent = new Intent(getApplicationContext(), CallOutActivity.class);
		intent.putExtra(CallOutActivity.PHONE_NUMBER, phoneNumber);
		this.startActivityForResult(intent, CALL_OUT);
	}


	public void addressRequested(String address) {
		Intent call = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + address));
        startActivity(call);
	}
}
