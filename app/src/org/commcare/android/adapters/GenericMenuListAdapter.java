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
 * @author ctsims
 *
 */
public class GenericMenuListAdapter implements ListAdapter {
	
	private CommCarePlatform platform;
	private Context context;
	private Menu menu;
	
	private Menu[] menuData;
	private Entry[] entryData;
	
	public GenericMenuListAdapter(Context context, CommCarePlatform platform) {
		
		System.out.println("Entering root constructor");
		
		this.platform = platform;
		this.context = context;
		
		Vector<Menu> menus = new Vector<Menu>();
		Vector<Entry> entries = new Vector<Entry>();
		
		for(Suite s : platform.getInstalledSuites()) {
			for(Menu m : s.getMenus()) {
				if(m.getRoot() == null || m.getRoot() == "" || m.getRoot().equals("root")) {
					menus.add(m);
					System.out.println("Adding menu in NestedMenuListAdapter: "+m.getId()+", has root " + m.getRoot());
				}
				if(m.getId().equals("root")){
					Hashtable<String, Entry> map = platform.getMenuMap();
					for(String command : m.getCommandIds()) {
						System.out.println("Adding command in MenuListAdapter: "+ command+" root is: "+m.getRoot()+" name is "+m.getId());
						Entry e = map.get(command);
						entries.add(e);
					}
					entryData= new Entry[entries.size()];
					entries.copyInto(entryData);
				}
			}
		}
		menuData= new Menu[menus.size()];
		menus.copyInto(menuData);
	}
	
	public GenericMenuListAdapter(Context context, CommCarePlatform platform, Menu menu) {
		
		System.out.println("Entering menu constructor: " + menu.getName());
		
		this.platform = platform;
		this.menu = menu;
		this.context = context;
		
		Hashtable<String, Entry> map = platform.getMenuMap();
		Vector<Entry> entries = new Vector<Entry>();
		for(String command : menu.getCommandIds()) {
			System.out.println("Adding command in GenericMenuListAdapter Const. 2: "+ command+" root is: "+menu.getRoot()+" name is "+menu.getId());
			Entry e = map.get(command);
			entries.add(e);
		}
		entryData= new Entry[entries.size()];
		entries.copyInto(entryData);
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
		return (mMenuLength()+mEntryLength());
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int i) {
		if(i<mMenuLength()){
			return menuData[i];
		}
		else{
			return entryData[i-mMenuLength()];
		}
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int i) {
		if(i<mMenuLength()){
			return menuData[i].getId().hashCode();
		}
		else{
			return entryData[i-mMenuLength()].getCommandId().hashCode();
		}
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemViewType(int)
	 */
	public int getItemViewType(int i) {
		return 0;
	}
	
	public int mEntryLength(){
		if(entryData==null){
			return 0;
		}
		return entryData.length;
	}
	
	public int mMenuLength(){
		if(menuData==null){
			return 0;
		}
		return menuData.length;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	public View getView(int i, View v, ViewGroup vg) {
		if(i<mMenuLength()){
			Menu m = menuData[i];
			SimpleTextView emv =(SimpleTextView)v;
			if(emv == null) {
				emv = new SimpleTextView(context, platform, m.getName());
			} else{
				emv.setParams(platform, m.getName());
			}
			return emv;
		}
		Entry e = entryData[i-mMenuLength()];
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
