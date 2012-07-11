/**
 * 
 */
package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.FormRecord;
import org.commcare.android.tasks.FormRecordLoaderTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.IncompleteFormRecordView;
import org.commcare.dalvik.application.CommCareApplication;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.AsyncTask.Status;
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
	
	FormRecordLoaderTask loader;
	
	
	public IncompleteFormListAdapter(Context context, AndroidCommCarePlatform platform, FormRecordLoaderTask loader) throws SessionUnavailableException{
		this.platform = platform;
		this.context = context;
		this.filter = null;
		observers = new ArrayList<DataSetObserver>();
		this.loader = loader;
	}
	
	public void resetRecords() throws SessionUnavailableException {
		if(loader.getStatus() == Status.RUNNING) {
			loader.cancel(false);
			loader = loader.spawn();
		} else if(loader.getStatus() == Status.FINISHED) {
			loader = loader.spawn();
		}
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		
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
		
		loader.init(searchCache);
		loader.execute(records.toArray(new FormRecord[0]));
	}

	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
	    for (DataSetObserver observer: observers) {
	        observer.onChanged();
	    }
	}
	
	@Override
	public void notifyDataSetInvalidated() {
		super.notifyDataSetInvalidated();
		resetRecords();
		for (DataSetObserver observer: observers) {
	        observer.onChanged();
	    }
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
		
		if(searchCache.containsKey(r.getID())) {
			ifrv.setParams(r, searchCache.get(r.getID())[1], r.lastModified().getTime());
		} else {
			//notify the loader that we need access to this record immediately
			loader.registerPriority(r);
			ifrv.setParams(r, "Loading...", r.lastModified().getTime());
		}
		
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
		
		
		//TODO: Don't let this happen until search cache is populated
		
		full:
		for(FormRecord r : records) {
			for(String cacheValue : searchCache.get(r.getID())) {
				for(String piece : pieces) {
					if(cacheValue.toLowerCase().contains(piece)) {
						current.add(r);
						continue full;
					}
				}
			}
		}
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

	public void release() {
		if(loader.getStatus() == Status.RUNNING) {
			loader.cancel(false);
		}
	}
}
