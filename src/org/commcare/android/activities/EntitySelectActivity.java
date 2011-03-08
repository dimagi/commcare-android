/**
 * 
 */
package org.commcare.android.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.application.AndroidShortcuts;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareSession;
import org.javarosa.core.services.storage.Persistable;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * @author ctsims
 *
 */
public abstract class EntitySelectActivity<T extends Persistable> extends ListActivity implements TextWatcher {
	private AndroidCommCarePlatform platform;
	
	private static final int CONFIRM_SELECT = 0;
	
	private static final int MENU_SORT = Menu.FIRST;

	
	EditText searchbox;
	EntityListAdapter<T> adapter;
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
    	int defaultKey = -1;
    	for(int i = 0 ; i < templates.length ; ++i) {
    		headers[i] = templates[i].evaluate();
    		if(defaultKey == -1 && !"".equals(headers[i])) {
    			defaultKey = i;
    		}
    	}
    	
    	EntityView v = new EntityView(this, platform, detail, headers);
    	header.removeAllViews();
    	LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    	header.addView(v,params);
    	adapter = new EntityListAdapter<T>(this, detail, platform, getStorage(), defaultKey);
    	setListAdapter(adapter);
    	searchbox.requestFocus();
    	
    }
    
    protected abstract SqlIndexedStorageUtility<T> getStorage();
    protected abstract Intent getDetailIntent(T t);


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        Intent i = getDetailIntent(adapter.getItem(position));
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
    	        
    	        i.putExtras(intent.getExtras());
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
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_SORT, 0, "Sort By...").setIcon(
                android.R.drawable.ic_menu_sort_alphabetically);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SORT:
                createSortMenu();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void createSortMenu() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	
        builder.setTitle("Sort by...");
    	Text[] templates = platform.getSession().getDetail(prototype.getShortDetailId()).getHeaders();
        
    	List<String> namesList = new ArrayList<String>();
    	        
    	final int[] keyarray = new int[templates.length];

    	int added = 0;
    	for(int i = 0 ; i < templates.length ; ++i) {
    		String result = templates[i].evaluate();
    		if(!"".equals(result)) {
    			namesList.add(result); 
    			keyarray[added] = i;
    			added++;
    		}
    	}
    	
    	final String[] names = namesList.toArray(new String[0]);

        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
            	adapter.sortEntities(keyarray[item]);
            	adapter.applyFilter(searchbox.getText().toString());
            }
        });
        
        builder.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				//
			}
        });

        
        AlertDialog alert = builder.create();
        alert.show();

    }
}
