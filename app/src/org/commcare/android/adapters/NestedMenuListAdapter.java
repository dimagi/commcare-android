/**
 * 
 */
package org.commcare.android.adapters;

import java.util.Vector;

import org.commcare.android.view.SimpleTextView;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
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
public class NestedMenuListAdapter implements ListAdapter {
	
	private CommCarePlatform platform;
	private Context context;
	
	private Menu[] data;
	
	public NestedMenuListAdapter(Context context, CommCarePlatform platform) {
		this.platform = platform;
		this.context = context;
		
		Vector<Menu> menus = new Vector<Menu>();
		
		for(Suite s : platform.getInstalledSuites()) {
			for(Menu m : s.getMenus()) {
				if(m.getRoot() == null || m.getRoot() == "" || m.getRoot().equals("root")) {
					menus.add(m);
					System.out.println("Adding menu in NestedMenuListAdapter: "+m.getId()+", has root " + m.getRoot());
				}
			}
		}
		data= new Menu[menus.size()];
		menus.copyInto(data);
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
	public Menu getItem(int i) {
		return data[i];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {
		return data[i].getId().hashCode();
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
		Menu m = data[i];
		SimpleTextView emv =(SimpleTextView)v;
		if(emv == null) {
			emv = new SimpleTextView(context, platform, m.getName());
		} else{
			emv.setParams(platform, m.getName());
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
