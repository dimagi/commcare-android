/**
 * 
 */
package org.commcare.android.adapters;

import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.view.SimpleTextView;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

/**
 * Adapter class to handle both Menu and Entry items
 * @author wspride
 *
 */
public class GenericMenuListAdapter implements ListAdapter {
	
	private CommCarePlatform platform;
	private Context context;
	private Object[] objectData;
	
	public GenericMenuListAdapter(Context context, CommCarePlatform platform, String menuID){
		
		System.out.println("Creating adapter with menuID: " + menuID);
		
		this.platform = platform;
		this.context = context;
		
		Vector<Object> items = new Vector<Object>();
		
		Hashtable<String, Entry> map = platform.getMenuMap();
		
		for(Suite s : platform.getInstalledSuites()) {
			for(Menu m : s.getMenus()) {
	    		if(m.getId().equals(menuID)) {
	    			for(String command : m.getCommandIds()) {
	    				Entry e = map.get(command);
	    				items.add(e);
	    			}
					continue;
	    		}
				if(menuID.equals(m.getRoot())){
					items.add(m);
				}
			}
		}
		
		objectData = new Object[items.size()];
		items.copyInto(objectData);
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
		return (objectData.length);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int i) {
		return objectData[i];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {
		
		Object tempItem = objectData[i];
		
		if(tempItem instanceof Menu){
			return ((Menu)tempItem).getId().hashCode();
		}
		else{
			return ((Entry)tempItem).getCommandId().hashCode();
		}
	}


	/*
	 * (non-Javadoc)
	 * @see android.widget.Adapter#getItemViewType(int)
	 */
	public int getItemViewType(int i) {
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	public View getView(int i, View v, ViewGroup vg) {
		if(objectData[i] instanceof Menu){
			Menu m = (Menu)objectData[i];
			SimpleTextView emv =(SimpleTextView)v;
			if(emv == null) {
				emv = new SimpleTextView(context, platform, m.getName());
			} else{
				emv.setParams(platform, m.getName());
			}
			return emv;
		}
		Entry e = (Entry)objectData[i];
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
