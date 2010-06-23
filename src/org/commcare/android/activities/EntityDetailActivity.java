/**
 * 
 */
package org.commcare.android.activities;

import java.util.Vector;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.models.Entity;
import org.commcare.android.models.EntityFactory;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CommCarePlatformProvider;
import org.commcare.android.util.CallListener;
import org.commcare.suite.model.Entry;

import android.app.ListActivity;
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
public class EntityDetailActivity extends ListActivity implements CallListener {
	private AndroidCommCarePlatform platform;
	
	private static final int CALL_OUT = 0;
	
	Entry prototype;
	
	Entity<Case> entity;
	
	EntityDetailAdapter adapter;
	EntityFactory<Case> factory;
	
	Button next;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.entity_detail);
        next = (Button)findViewById(R.id.entity_select_button);
        next.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
		        Intent i = new Intent(EntityDetailActivity.this.getIntent());
		        i.putExtra(GlobalConstants.STATE_CASE_ID, entity.getElement().getCaseId());
		        setResult(RESULT_OK, i);

		        finish();
			}
        	
        });
        
        platform = CommCarePlatformProvider.unpack(getIntent().getBundleExtra(GlobalConstants.COMMCARE_PLATFORM), this);
        
		Vector<Entry> entries = platform.getEntriesForCommand(getIntent().getStringExtra(GlobalConstants.STATE_COMMAND_ID));
		prototype = entries.elementAt(0);
		
		String id = getIntent().getStringExtra(GlobalConstants.STATE_CASE_ID);
        
        setTitle(getString(R.string.app_name) + " > " + "Details");
        
        factory = new EntityFactory<Case>(platform.getDetail(prototype.getLongDetailId()));
        
        Case c = (new SqlIndexedStorageUtility<Case>(Case.STORAGE_KEY, Case.class, this)).getRecordForValue(Case.META_CASE_ID, id);
        
        entity = factory.getEntity(c);
        
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
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	switch(requestCode) {
    	case CALL_OUT:
    		refreshView();
    		return;
    	default:
    		super.onActivityResult(requestCode, resultCode, intent);
    	}
    }


	public void callRequested(String phoneNumber) {
		Intent intent = new Intent(Intent.ACTION_CALL);
		intent.setData(Uri.parse("tel:" + phoneNumber));
		this.startActivityForResult(intent, CALL_OUT);
	}
}
