/**
 * 
 */
package org.commcare.dalvik.activities;

import java.util.Vector;

import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;

/**
 * @author ctsims
 *
 */
public class EntityDetailActivity extends Activity implements DetailCalloutListener {
	private CommCareSession session;
	
	private static final int CALL_OUT = 0;
	
	public static final String IS_DEAD_END = "eda_ide";
	
	public static final String CONTEXT_REFERENCE = "eda_crid";

	public static final String DETAIL_ID = "eda_detail_id";
		
	Entry prototype;
	
	Entity<TreeReference> entity;
	
	EntityDetailAdapter adapter;
	NodeEntityFactory factory;
	
	Button next;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if(this.getString(R.string.panes).equals("two")) {
        	if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
        		//this occurs when the screen was rotated to be vertical on the select activity. We
        		//want to navigate back to that screen now.
        		this.setResult(RESULT_CANCELED, this.getIntent());
        		this.finish();
        		return;
        	}
        }

        
        try{
	        setContentView(R.layout.entity_detail);
	        next = (Button)findViewById(R.id.entity_select_button);
	        next.setText(Localization.get("select.detail.confirm"));
	        next.setOnClickListener(new OnClickListener() {
	
				public void onClick(View v) {
			        Intent i = new Intent(EntityDetailActivity.this.getIntent());
			        loadOutgoingIntent(i);
			        setResult(RESULT_OK, i);
	
			        finish();
				}
	        	
	        });
	        
	        if(getIntent().getBooleanExtra(IS_DEAD_END, false)) {
	        	next.setText("Done");
	        }
	        
	        
	        session = CommCareApplication._().getCurrentSession();
	        
	        
	        String passedCommand = getIntent().getStringExtra(CommCareSession.STATE_COMMAND_ID);
	        
			Vector<Entry> entries = session.getEntriesForCommand(passedCommand == null ? session.getCommand() : passedCommand);
			prototype = entries.elementAt(0);
	
	        factory = new NodeEntityFactory(session.getDetail(getIntent().getStringExtra(EntityDetailActivity.DETAIL_ID)), session.getEvaluationContext(new CommCareInstanceInitializer(session)));
			
		    entity = factory.getEntity(CommCareApplication._().deserializeFromIntent(getIntent(), EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class));
	        
	        setTitle(getString(R.string.application_name) + " > " + "Details");
	        
	        refreshView();
        } catch(SessionUnavailableException sue) {
        	//TODO: Login and return to try again
        }
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	adapter = new EntityDetailAdapter(this, session, factory.getDetail(), entity, this);
    	((ListView)this.findViewById(R.id.screen_entity_detail_list)).setAdapter(adapter);
    }
        
    protected void loadOutgoingIntent(Intent i) {
    	i.putExtra(CommCareSession.STATE_DATUM_VAL, this.getIntent().getStringExtra(CommCareSession.STATE_DATUM_VAL));
    }
    
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
