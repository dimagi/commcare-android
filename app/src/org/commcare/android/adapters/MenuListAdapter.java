/**
 * 
 */
package org.commcare.android.adapters;

import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.view.SimpleTextView;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

/**
 * @author ctsims
 *
 */
public class MenuListAdapter implements ListAdapter {
	
	private CommCarePlatform platform;
	private Menu menu;
	private Context context;
	
	private Entry[] data;
	
	public MenuListAdapter(Context context, CommCarePlatform platform, Menu menu) {
		this.platform = platform;
		this.menu = menu;
		this.context = context;
		
		Hashtable<String, Entry> map = platform.getMenuMap();
		Vector<Entry> entries = new Vector<Entry>();
		for(String command : menu.getCommandIds()) {
			Entry e = map.get(command);
			entries.add(e);
		}
		data= new Entry[entries.size()];
		entries.copyInto(data);
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
		return data.length;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int i) {
		return data[i];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {
		//Skeeeeetccchhyyyy
		return data[i].getCommandId().hashCode();
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
		Entry e = data[i];
		SimpleTextView emv =(SimpleTextView)v;
		if(emv == null) {
			emv = new SimpleTextView(context, platform, e.getText());
		} else{
			emv.setParams(platform, e.getText());
		}
		return emv;
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
		return false;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
	 */
	public void registerDataSetObserver(DataSetObserver arg0) {

	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
	 */
	public void unregisterDataSetObserver(DataSetObserver arg0) {

	}

}
