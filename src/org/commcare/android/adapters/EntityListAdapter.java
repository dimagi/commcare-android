/**
 * 
 */
package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;

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
	
	Context context;
	
	List<DataSetObserver> observers;
	
	NodeEntityFactory factory;
	List<Entity<TreeReference>> full;
	List<Entity<TreeReference>> current;
	List<TreeReference> references;
	
	int currentSort = -1;
	boolean reverseSort = false;
	
	public EntityListAdapter(Context context, Detail d, EvaluationContext ec, List<TreeReference> references)  throws SessionUnavailableException{
		this(context, d, ec, references, -1);
	}
	
	public EntityListAdapter(Context context, Detail d, EvaluationContext ec, List<TreeReference> references, int sort) throws SessionUnavailableException {
		factory = new NodeEntityFactory(d, ec);
		
		full = new ArrayList<Entity<TreeReference>>();
		current = new ArrayList<Entity<TreeReference>>();
		
		this.references = references;
		
		this.context = context;
		this.observers = new ArrayList<DataSetObserver>();

		all();
		if(sort != -1) {
			sort(sort);
		}
		filterValues("");
	}
	
	private void all() throws SessionUnavailableException{
		for(TreeReference ref : references) {
			Entity<TreeReference> e = factory.getEntity(ref);
			if(e != null) {
				full.add(e);
			}
		}
	}
	
	private void filterValues(String filter) {
		current.clear();
		
		full:
		for(Entity<TreeReference> e : full) {
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
		
		java.util.Collections.sort(full, new Comparator<Entity<TreeReference>>() {

			public int compare(Entity<TreeReference> object1, Entity<TreeReference> object2) {
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
	public TreeReference getItem(int position) {
		return current.get(position).getElement();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int position) {
		return references.indexOf(current.get(position).getElement());
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
		Entity<TreeReference> e = current.get(position);
		EntityView emv =(EntityView)convertView;
		if(emv == null) {
			emv = new EntityView(context, factory.getDetail(), e);
		} else{
			emv.setParams(e);
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
