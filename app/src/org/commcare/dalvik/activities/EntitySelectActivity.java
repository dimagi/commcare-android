/**
 * 
 */
package org.commcare.dalvik.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.EntityView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.expr.XPathEqExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathStringLiteral;

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
 * 
 * TODO: Lots of locking and state-based cleanup
 * 
 * @author ctsims
 *
 */
public class EntitySelectActivity extends ListActivity implements TextWatcher {
	private CommCareSession session;
	
	public static final String EXTRA_ENTITY_KEY = "esa_entity_key";
	public static final String EXTRA_IS_MAP = "is_map";
	
	private static final int CONFIRM_SELECT = 0;
	private static final int BARCODE_FETCH = 1;
	private static final int MAP_SELECT = 2;
	
	private static final int MENU_SORT = Menu.FIRST;
	private static final int MENU_MAP = Menu.FIRST + 1;
	
	EditText searchbox;
	EntityListAdapter adapter;
	Entry prototype;
	LinearLayout header;
	ImageButton barcodeButton;
	
	SessionDatum selectDatum;
	
	EvaluationContext entityContext;
	
	boolean mResultIsMap = false;
	
	boolean mMappingEnabled = false;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if(savedInstanceState != null) {
        	mResultIsMap = savedInstanceState.getBoolean(EXTRA_IS_MAP, false);
        }

        setContentView(R.layout.entity_select_layout);
        searchbox = (EditText)findViewById(R.id.searchbox);
        header = (LinearLayout)findViewById(R.id.entity_select_header);
        
        barcodeButton = (ImageButton)findViewById(R.id.barcodeButton);
        
        searchbox.addTextChangedListener(this);
        searchbox.requestFocus();
        
        session = CommCareApplication._().getCurrentSession();
        
		Vector<Entry> entries = session.getEntriesForCommand(session.getCommand());
		prototype = entries.elementAt(0);
        
        setTitle(getString(R.string.application_name) + " > " + " Select");
        
		selectDatum = session.getNeededDatum();
        
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
        
        if(selectDatum.getLongDetail() != null && this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
        	TreeReference entity = getEntityFromID(this.getIntent().getStringExtra(EXTRA_ENTITY_KEY));
        	
        	if(entity != null) {
        		Intent i = getDetailIntent(entity);
        		startActivityForResult(i, CONFIRM_SELECT);
        	} else {
        		refreshView();
        	}
        } else {
            refreshView();
        }
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	try {
    		
	    	Detail detail = session.getDetail(selectDatum.getShortDetail());
	    	
	    	//TODO: Get ec into these text's
	    	String[] headers = new String[detail.getFields().length];
	    	int[] order = detail.getSortOrder();
	    	
	    	
	    	for(int i = 0 ; i < headers.length ; ++i) {
	    		headers[i] = detail.getFields()[i].getHeader().evaluate();
	    		if(order.length == 0 && !"".equals(headers[i])) {
	    			order = new int[] {i}; 
	    		}
	    		
	    		if("address".equals(detail.getFields()[i].getTemplateForm())) {
	    			this.mMappingEnabled = true;
	    		}
	    	}
	    	
	    	EntityView v = new EntityView(this, detail, headers);
	    	header.removeAllViews();
	    	LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
	    	v.setBackgroundResource(R.drawable.blue_tabbed_box);
	    	header.addView(v,params);
	    	
	    	
	    	Vector<TreeReference> references = getEC().expandReference(selectDatum.getNodeset());
	    	
	    	adapter = new EntityListAdapter(this, detail, getEC(), references, order);
	    	setListAdapter(adapter);
	    	
    	} catch(SessionUnavailableException sue) {
    		//TODO: login and return
    	}
    }
    
    private EvaluationContext getEC() {
    	if(entityContext == null) {
    		entityContext = session.getEvaluationContext(getInstanceInit());
    	}
    	return entityContext;
    }
    
    private CommCareInstanceInitializer getInstanceInit() {
    	return new CommCareInstanceInitializer(session);
    }
    
    protected Intent getDetailIntent(TreeReference contextRef) {
    	//Parse out the return value first, and stick it in the appropriate intent so it'll get passed along when
    	//we return
    	
    	TreeReference valueRef = XPathReference.getPathExpr(selectDatum.getValue()).getReference(true);
    	AbstractTreeElement element = getEC().resolveReference(valueRef.contextualize(contextRef));
    	String value = "";
    	if(element != null && element.getValue() != null) {
    		value = element.getValue().uncast().getString();
    	}
    	
    	//See if we even have a long datum
    	if(selectDatum.getLongDetail() != null) {
    		//We do, 
    	}
    	
    	Intent i = new Intent(getApplicationContext(), EntityDetailActivity.class);
    	i.putExtra(CommCareSession.STATE_DATUM_VAL, value);
    	i.putExtra(EntityDetailActivity.DETAIL_ID, selectDatum.getLongDetail());
    	CommCareApplication._().serializeToIntent(i, EntityDetailActivity.CONTEXT_REFERENCE, contextRef);
    	
    	return i;
    }
    /**
     * NOT GUARANTEED TO WORK! May return an entity if one exists
     * 
     * @param uniqueid
     * @return
     */
    protected TreeReference getEntityFromID(String uniqueid) {
    	//The uniqueid here is the value selected, so we can in theory track down the value we're looking for.
    	
    	//Get root nodeset 
    	TreeReference nodesetRef = selectDatum.getNodeset().clone();
    	Vector<XPathExpression> predicates = nodesetRef.getPredicate(nodesetRef.size() -1);
    	predicates.add(new XPathEqExpr(true, XPathReference.getPathExpr(selectDatum.getValue()), new XPathStringLiteral(uniqueid)));
    	nodesetRef.addPredicate(nodesetRef.size() - 1, predicates);
    	
    	Vector<TreeReference> elements = getEC().expandReference(nodesetRef);
    	if(elements.size() == 1) {
    		return elements.firstElement();
    	} else if(elements.size() > 1) {
    		//Lots of nodes. Can't really choose one yet.
    		return null;
    	} else {
    		return null;
    	}
    }


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
	    		if(mResultIsMap) {
	    			mResultIsMap = false;
	            	Intent i = new Intent(this, EntityMapActivity.class);
	            	this.startActivityForResult(i, MAP_SELECT);
	            	return;
	    		}
    	        Intent i = new Intent(this.getIntent());
    	        setResult(RESULT_CANCELED, i);
    	        //If the original calling intent bounced us up immediately, we need to refresh
    	        if(this.getIntent().hasExtra(EXTRA_ENTITY_KEY)) {
    	        	refreshView();
    	        }
        		return;
    		}
    	case MAP_SELECT:
    		if(resultCode == RESULT_OK) {
	    		TreeReference r = CommCareApplication._().deserializeFromIntent(intent, EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);
	    		
	        	Intent i = this.getDetailIntent(r);
	        	
	    		//To go back to map mode if confirm is false
	        	mResultIsMap = true;
	        	
	            startActivityForResult(i, CONFIRM_SELECT);
	            return;
    		} else {
    			refreshView();
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
        if(mMappingEnabled) {
	        menu.add(0, MENU_MAP, 1, "View on Map").setIcon(
	                android.R.drawable.ic_menu_mapmode);
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SORT:
                createSortMenu();
                return true;
            case MENU_MAP:
            	Intent i = new Intent(this, EntityMapActivity.class);
            	this.startActivityForResult(i, MAP_SELECT);
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void createSortMenu() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	
        builder.setTitle("Sort by...");
		SessionDatum datum = session.getNeededDatum();
    	DetailField[] fields = session.getDetail(datum.getShortDetail()).getFields();
        
    	List<String> namesList = new ArrayList<String>();
    	        
    	final int[] keyarray = new int[fields.length];
    	
    	int[] sorts = adapter.getCurrentSort();
    	
    	int currentSort = sorts.length == 1 ? sorts[0] : -1;
    	boolean reversed = adapter.isCurrentSortReversed();

    	int added = 0;
    	for(int i = 0 ; i < fields.length ; ++i) {
    		String result = fields[i].getHeader().evaluate();
    		if(!"".equals(result)) {
    			String prepend = "";
    			if(currentSort == -1) {
    				for(int j = 0 ; j < sorts.length ; ++ j) {
    					if(sorts[j] == i) {
    						prepend = (j+1) + " " + (fields[i].getSortDirection() == DetailField.DIRECTION_DESCENDING ? "(v) " : "(^) ");
    					}
    				}
    			} else if(currentSort == i) {
    				prepend = reversed ^ fields[i].getSortDirection() == DetailField.DIRECTION_DESCENDING ? "(v) " : "(^) ";
    			}
    			namesList.add(prepend + result); 
    			keyarray[added] = i;
    			added++;
    		}
    	}
    	
    	final String[] names = namesList.toArray(new String[0]);
    	    	
        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
            	adapter.sortEntities(new int[] { keyarray[item]});
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
