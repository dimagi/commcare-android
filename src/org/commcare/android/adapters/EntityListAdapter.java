/**
 * 
 */
package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.models.Entity;
import org.commcare.android.models.EntityFactory;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCarePlatform;
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
public class EntityListAdapter<T extends Persistable> implements ListAdapter {
	
	SqlIndexedStorageUtility<T> utility;
	
	Context context;
	CommCarePlatform platform;
	
	List<DataSetObserver> observers;
	
	EntityFactory<T> factory;
	List<Entity<T>> full;
	List<Entity<T>> current;
	
	int currentSort = -1;
	boolean reverseSort = false;
	
	public EntityListAdapter(Context context, Detail d, AndroidCommCarePlatform platform, SqlIndexedStorageUtility<T> utility)  throws SessionUnavailableException{
		this(context, d, platform, utility, -1);
	}
	
	public EntityListAdapter(Context context, Detail d, AndroidCommCarePlatform platform, SqlIndexedStorageUtility<T> utility, int sort) throws SessionUnavailableException {
		this.utility = utility;
		
		factory = new EntityFactory<T>(d, platform.getLoggedInUser());
		
		full = new ArrayList<Entity<T>>();
		current = new ArrayList<Entity<T>>();
		
		this.context = context;
		this.platform = platform;
		this.observers = new ArrayList<DataSetObserver>();

		all();
		if(sort != -1) {
			sort(sort);
		}
		filterValues("");
	}
	
	private void all() throws SessionUnavailableException{
		for(SqlStorageIterator<T> i = utility.iterate() ; i.hasMore() ;){
			T t = i.nextRecord();
			Entity<T> e = factory.getEntity(t);
			if(e != null) {
				full.add(e);
			}
		}
	}
	
	private void filterValues(String filter) {
		current.clear();
		
		full:
		for(Entity<T> e : full) {
			for(String field : e.getFields()) {
				if(field.toLowerCase().contains(filter.toLowerCase())) {
					current.add(e);
					continue full;
				}
			}
		}
	}
	
	private void sort(int field) {
		if(currentSort == field) {
			reverseSort = !reverseSort;
		} else {
			reverseSort = false;
		}
		
		currentSort = field;
		
		java.util.Collections.sort(full, new Comparator<Entity<T>>() {

			public int compare(Entity<T> object1, Entity<T> object2) {
				return (reverseSort ? -1 : 1) * object1.getFields()[currentSort].compareTo(object2.getFields()[currentSort]);
			}
			
		});
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
		return current.size();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public T getItem(int position) {
		return current.get(position).getElement();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int position) {
		return current.get(position).getElement().getID();
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
		Entity<T> e = current.get(position);
		EntityView emv =(EntityView)convertView;
		if(emv == null) {
			emv = new EntityView(context, platform, factory.getDetail(), e);
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
	
	public void applyFilter(String s) {
		filterValues(s);
		for(DataSetObserver o : observers) {
			o.onChanged();
		}
	}
	
	public void sortEntities(int key) {
		sort(key);
	}
	
	public int getCurrentSort() {
		return currentSort;
	}
	
	public boolean isCurrentSortReversed() {
		return reverseSort;
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
