/**
 * 
 */
package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.NoSuchElementException;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Case;
import org.commcare.android.models.FormRecord;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.IncompleteFormRecordView;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * @author ctsims
 *
 */
public class IncompleteFormListAdapter extends BaseAdapter {
	
	private AndroidCommCarePlatform platform;
	private Context context;
	List<DataSetObserver> observers;
	
	String filter;
	private List<FormRecord> records;
	private List<FormRecord> current;
	
	private Hashtable<Integer, String[]> searchCache;
	
	private String currentQuery;
		
	public IncompleteFormListAdapter(Context context, AndroidCommCarePlatform platform) throws SessionUnavailableException{
		this.platform = platform;
		this.context = context;
		this.filter = null;
		observers = new ArrayList<DataSetObserver>();
	}
	
	public void resetRecords() throws SessionUnavailableException {		
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		
		System.out.println("one");
		if(filter == null) { filter = FormRecord.STATUS_SAVED; }
		records = storage.getRecordsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {filter} );
		System.out.println("two");
		
		Collections.sort(records,new Comparator<FormRecord>() {

			public int compare(FormRecord one, FormRecord two) {
				
				long ot = one.lastModified().getTime();
				long tt = two.lastModified().getTime();
				 
//				if(ot == 0) return -1;
//				if(tt == 0) return 1;
				
				return ot > tt ? -1 : ot == tt ? 0 : 1;
				
			}
			
		});
		
		searchCache = new Hashtable<Integer, String[]>();
		current = new ArrayList<FormRecord>();
		
		this.filterValues(currentQuery);
	}

	@Override
	public void notifyDataSetChanged() {
		resetRecords();
		super.notifyDataSetChanged();
	}

	/* (non-Javadoc)
	 * @see android.widget.ListAdapter#areAllItemsEnabled()
	 */
	public boolean areAllItemsEnabled() {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.ListAdapter#isEnabled(int)
	 */
	public boolean isEnabled(int arg0) {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	public int getCount() {
		return current.size();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int i) {
		return current.get(i);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {
		//Skeeeeetccchhyyyy maybe?
		return current.get(i).getID();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemViewType(int)
	 */
	public int getItemViewType(int i) {
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	public View getView(int i, View v, ViewGroup vg) {
		FormRecord r = current.get(i);
		IncompleteFormRecordView ifrv =(IncompleteFormRecordView)v;
		if(ifrv == null) {
			ifrv = new IncompleteFormRecordView(context, platform);
		}
		ifrv.setParams(platform, r, r.lastModified().getTime());
		return ifrv;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getViewTypeCount()
	 */
	public int getViewTypeCount() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#hasStableIds()
	 */
	public boolean hasStableIds() {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#isEmpty()
	 */
	public boolean isEmpty() {
		return getCount() > 0;
	}
	
	public void setFormFilter(String filter) throws SessionUnavailableException {
		this.filter = filter;
	}
	
	public String getFilter() {
		return this.filter;
	}
	
	private void filterValues(String query) {
		this.currentQuery = query;
		current.clear();
		
		if(query == null || query.equals("")) {
			current.addAll(records);
			return;
		}
		
		String[] pieces = query.toLowerCase().split(" ");
		
		full:
		for(FormRecord r : records) {
			if(!searchCache.containsKey(r.getID())) {
				populateSearchCache(r);
			}
			for(String cacheValue : searchCache.get(r.getID())) {
				for(String piece : pieces) {
					if(cacheValue.contains(piece)) {
						current.add(r);
						continue full;
					}
				}
			}
		}
	}
	
	protected void populateSearchCache(FormRecord record) {
		
		ArrayList<String> cache = new ArrayList<String>();
		
		//Only the month
		cache.add(android.text.format.DateUtils.formatDateTime(context, record.lastModified().getTime(), android.text.format.DateUtils.FORMAT_NO_MONTH_DAY | android.text.format.DateUtils.FORMAT_NO_YEAR).toLowerCase()); 
		
		if(record.getEntityId() != null && !record.getEntityId().equals(AndroidCommCarePlatform.ENTITY_NONE)) {
			SqlIndexedStorageUtility<Case> storage =  CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class);
			try {
				Case c = storage.getRecordForValue(Case.META_CASE_ID, record.getCaseId());
				
				if(c.getProperty("initials") != null) {
					cache.add(((String)c.getProperty("initials")).toLowerCase());
					if(c.getProperty("pactid") != null) {
						cache.add(((String)c.getProperty("pactid")).toLowerCase());
					}
				} else {
					cache.add(c.getName().toLowerCase());
				}
			} catch(NoSuchElementException nsee) {
				//Not sure what to do about that one.
			}
		}
		
		this.searchCache.put(record.getID(), cache.toArray(new String[0]));

	}

	
	public void applyTextFilter(String query) {
		filterValues(query);
		for(DataSetObserver o : observers) {
			o.onChanged();
		}
	}


	/* (non-Javadoc)
	 * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
	 */
	public void registerDataSetObserver(DataSetObserver observer) {
		this.observers.add(observer);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
	 */
	public void unregisterDataSetObserver(DataSetObserver observer) {
		this.observers.remove(observer);
	}
}
