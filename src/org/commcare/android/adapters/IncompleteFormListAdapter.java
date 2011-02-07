/**
 * 
 */
package org.commcare.android.adapters;

import java.util.Vector;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.FormRecord;
import org.commcare.android.util.AndroidCommCarePlatform;
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
	
	String filter;
	private FormRecord[] records;
	
	public IncompleteFormListAdapter(Context context, AndroidCommCarePlatform platform) {
		this.platform = platform;
		this.context = context;
		this.filter = null;
		resetRecords();
	}
	
	public void resetRecords() {
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		Vector<Integer> formids;
		if(filter != null) {
			formids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {filter} );
		} else {
			formids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_SAVED} );
		}
		records = new FormRecord[formids.size()];
		for(int i = 0 ; i < records.length ; ++i) {
			records[i] = storage.read(formids.elementAt(i).intValue());
		}
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
		return records.length;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int i) {
		return records[i];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {
		//Skeeeeetccchhyyyy
		return records[i].getID();
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
		FormRecord r = records[i];
		IncompleteFormRecordView ifrv =(IncompleteFormRecordView)v;
		if(ifrv == null) {
			ifrv = new IncompleteFormRecordView(context, platform);
		}
		ifrv.setParams(platform, r);
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
		return records.length > 0;
	}
	
	public void setFormFilter(String filter) {
		this.filter = filter;
		resetRecords();
	}
}
