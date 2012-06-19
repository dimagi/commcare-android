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
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidSessionWrapper;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.IncompleteFormRecordView;
import org.commcare.cases.model.Case;

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

	
	SqlIndexedStorageUtility<SessionStateDescriptor> descriptorStorage;
	
	public IncompleteFormListAdapter(Context context, AndroidCommCarePlatform platform) throws SessionUnavailableException{
		this.platform = platform;
		this.context = context;
		this.filter = null;
		observers = new ArrayList<DataSetObserver>();
	}
	
	public void resetRecords() throws SessionUnavailableException {		
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		descriptorStorage = CommCareApplication._().getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class); 
		
		if(filter == null) { filter = FormRecord.STATUS_SAVED; }
		records = storage.getRecordsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {filter} );
		
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

	private Hashtable<String,String> descriptorCache = new Hashtable<String,String>();
	
	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	public View getView(int i, View v, ViewGroup vg) {
		FormRecord r = current.get(i);
		IncompleteFormRecordView ifrv =(IncompleteFormRecordView)v;
		if(ifrv == null) {
			ifrv = new IncompleteFormRecordView(context, platform);
		}
		
		String dataTitle = getRecordTitle(r);
		
		ifrv.setParams(r, dataTitle, r.lastModified().getTime());
		return ifrv;
	}

	private String getRecordTitle(FormRecord r) {
		SessionStateDescriptor ssd = null;
		try {
		 ssd = descriptorStorage.getRecordForValue(SessionStateDescriptor.META_FORM_RECORD_ID, r.getID());
		} catch(NoSuchElementException nsee) {
			//s'all good
		}
		String dataTitle = null;
		if(ssd != null) {
			String descriptor = ssd.getSessionDescriptor();
			if(!descriptorCache.containsKey(descriptor)) {
				AndroidSessionWrapper asw = new AndroidSessionWrapper(platform);
				asw.loadFromStateDescription(ssd);
				dataTitle = asw.getTitle();
				dataTitle = dataTitle == null ? "" : dataTitle;
				descriptorCache.put(descriptor, dataTitle);
			} else {
				dataTitle = descriptorCache.get(descriptor);
			}
		}
		return dataTitle;
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
		
		cache.add(getRecordTitle(record).toLowerCase());
		
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
