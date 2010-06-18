/**
 * 
 */
package org.commcare.android.adapters;

import java.util.Vector;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.view.EntityView;
import org.commcare.android.view.EntryMenuView;
import org.commcare.suite.model.Entry;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

/**
 * @author ctsims
 *
 */
public class EntityListAdapter implements ListAdapter {
	
	SqlIndexedStorageUtility utility;
	
	Persistable[] p;
	
	Context context;
	CommCarePlatform platform;
	
	public EntityListAdapter(Context context, CommCarePlatform platform, SqlIndexedStorageUtility utility) {
		this.utility = utility;
		Vector<Persistable> data = new Vector<Persistable>(); 
		for(IStorageIterator i = utility.iterate() ; i.hasMore() ;){
			data.add((Persistable)i.nextRecord());
		}
		p = new Persistable[data.size()];
		data.copyInto(p);
		
		this.context = context;
		this.platform = platform;
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
	public boolean isEnabled(int position) {
		return true;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	public int getCount() {
		return p.length;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int position) {
		return p[position];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int position) {
		return p[position].getID();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemViewType(int)
	 */
	public int getItemViewType(int position) {
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	public View getView(int position, View convertView, ViewGroup parent) {
		Persistable e = p[position];
		EntityView emv =(EntityView)convertView;
		if(emv == null) {
			emv = new EntityView(context, platform, e);
		} else{
			emv.setParams(platform, e);
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
		return getCount() > 0;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
	 */
	public void registerDataSetObserver(DataSetObserver observer) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
	 */
	public void unregisterDataSetObserver(DataSetObserver observer) {
		// TODO Auto-generated method stub

	}

}
