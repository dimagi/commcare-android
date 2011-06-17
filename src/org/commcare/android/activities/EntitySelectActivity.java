/**
 * 
 */
package org.commcare.android.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Text;
import org.javarosa.core.services.storage.Persistable;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public abstract class EntitySelectActivity<T extends Persistable> extends ListActivity implements TextWatcher {
	private AndroidCommCarePlatform platform;
	
	private static final String EXTRA_ENTITY_KEY = "esa_entity_key";
	
	private static final int CONFIRM_SELECT = 0;
	private static final int BARCODE_FETCH = 1;
	
	private static final int MENU_SORT = Menu.FIRST;

	
	EditText searchbox;
	EntityListAdapter<T> adapter;
	Entry prototype;
	LinearLayout header;
	ImageButton barcodeButton;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.entity_select_layout);
        searchbox = (EditText)findViewById(R.id.searchbox);
        header = (LinearLayout)findViewById(R.id.entity_select_header);
        
        barcodeButton = (ImageButton)findViewById(R.id.barcodeButton);
        
        
        searchbox.addTextChangedListener(this);
        
        platform = CommCareApplication._().getCommCarePlatform();
        
		Vector<Entry> entries = platform.getSession().getEntriesForCommand(platform.getSession().getCommand());
		prototype = entries.elementAt(0);
        
        setTitle(getString(R.string.app_name) + " > " + " Select");
        
        barcodeButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				Intent i = new Intent("com.google.zxing.client.android.SCAN");
				try {
					startActivityForResult(i, BARCODE_FETCH);
				} catch(ActivityNotFoundException anfe) {
					Toast noReader = Toast.makeText(EntitySelectActivity.this, "No barcode reader available! You can install one from the android market.", Toast.LENGTH_LONG);
					noReader.show();
				}
			}
        	
        });
        
        if(this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
        	T entity = getEntityFromID(this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));
        	
            Intent i = getDetailIntent(entity);
            startActivityForResult(i, CONFIRM_SELECT);
        } else {
            refreshView();
        }
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	try {
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
	    	adapter = new EntityListAdapter<T>(this, detail, platform, getStorage(), CommCareApplication._().getSession().getLoggedInUser(), defaultKey);
	    	setListAdapter(adapter);
	    	searchbox.requestFocus();
    	} catch(SessionUnavailableException sue) {
    		//TODO: login and return
    	}
    }
    
    protected abstract SqlIndexedStorageUtility<T> getStorage() throws SessionUnavailableException;
    protected abstract Intent getDetailIntent(T t);
    
    /**
     * NOT GUARANTEED TO WORK! May return an entity if one exists
     * 
     * @param uniqueid
     * @return
     */
    protected abstract T getEntityFromID(String uniqueid);


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
    	case BARCODE_FETCH:
    		if(resultCode == Activity.RESULT_OK) {
    			String result = intent.getStringExtra("SCAN_RESULT");
    			this.searchbox.setText(result);
    		}
    		break;
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
    	
    	int currentSort = adapter.getCurrentSort();
    	boolean reversed = adapter.isCurrentSortReversed();

    	int added = 0;
    	for(int i = 0 ; i < templates.length ; ++i) {
    		String result = templates[i].evaluate();
    		if(!"".equals(result)) {
    			String prepend = "";
    			if(currentSort == i) {
    				prepend = reversed ? "(v) " : "(^) ";
    			}
    			namesList.add(prepend + result); 
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
