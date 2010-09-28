/**
 * 
 */
package org.commcare.android.activities;

import java.util.Vector;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.Case;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareSession;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * @author ctsims
 *
 */
public class EntitySelectActivity extends ListActivity implements TextWatcher {
	private AndroidCommCarePlatform platform;
	
	private static final int CONFIRM_SELECT = 0;
	
	EditText searchbox;
	EntityListAdapter<Case> adapter;
	Entry prototype;
	LinearLayout header;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.entity_select_layout);
        searchbox = (EditText)findViewById(R.id.searchbox);
        header = (LinearLayout)findViewById(R.id.entity_select_header);
        
        
        searchbox.addTextChangedListener(this);
        
        platform = CommCareApplication._().getCommCarePlatform();
        
		Vector<Entry> entries = platform.getSession().getEntriesForCommand(platform.getSession().getCommand());
		prototype = entries.elementAt(0);
        
        setTitle(getString(R.string.app_name) + " > " + " Select");
        
        refreshView();
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	Detail detail = platform.getSession().getDetail(prototype.getShortDetailId());
    	
    	Text[] templates = detail.getHeaders();
    	String[] headers = new String[templates.length];
    	for(int i = 0 ; i < templates.length ; ++i) {
    		headers[i] = templates[i].evaluate();
    	}
    	
    	EntityView v = new EntityView(this, platform, detail, headers);
    	header.removeAllViews();
    	LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    	header.addView(v,params);
    	adapter = new EntityListAdapter<Case>(this, detail, platform, CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class));
    	setListAdapter(adapter);
    	searchbox.requestFocus();
    	
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
    	
    	
        Intent i = new Intent(getApplicationContext(), EntityDetailActivity.class);

        i.putExtra(CommCareSession.STATE_CASE_ID, adapter.getItem(position).getCaseId());
        startActivityForResult(i, CONFIRM_SELECT);
        
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	switch(requestCode){
    	case CONFIRM_SELECT:
    		if(resultCode == RESULT_OK) {
    	        // create intent for return and store path
    	        Intent i = new Intent(this.getIntent());
    	        i.putExtra(CommCareSession.STATE_CASE_ID, intent.getStringExtra(CommCareSession.STATE_CASE_ID));
    	        long duration = intent.getLongExtra(CallOutActivity.CALL_DURATION, 0);
    	        if(duration != 0) {
    	        	i.putExtra(CallOutActivity.CALL_DURATION, duration);
    	        }
    	        setResult(RESULT_OK, i);

    	        finish();
        		return;
    		} else {
    	        Intent i = new Intent(this.getIntent());
    	        setResult(RESULT_CANCELED, i);
        		return;
    		}
    	default:
    		super.onActivityResult(requestCode, resultCode, intent);
    	}
    }


	public void afterTextChanged(Editable s) {
		if(searchbox.getText() == s) {
			adapter.applyFilter(s.toString());
		}
	}


	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub
		
	}


	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// TODO Auto-generated method stub
		
	}
}
