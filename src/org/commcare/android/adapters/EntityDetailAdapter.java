/**
 * 
 */
package org.commcare.android.adapters;

import java.util.ArrayList;
import java.util.List;

import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.view.EntityDetailView;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCareSession;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

/**
 * @author ctsims
 *
 */
public class EntityDetailAdapter implements ListAdapter {
	
	Context context;
	CommCareSession session;
	Detail detail;
	Entity entity;
	DetailCalloutListener listener;
	List<Integer> valid;
	
	public EntityDetailAdapter(Context context, CommCareSession session, Detail detail, Entity entity, DetailCalloutListener listener) {		
		this.context = context;
		this.session = session;
		this.detail = detail;
		this.entity = entity;
		this.listener = listener;
		valid = new ArrayList<Integer>(); 
		for(int i = 0 ; i < entity.getFields().length ; ++i ) {
			if(!entity.getFields()[i].equals("")) {
				valid.add(i);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see android.widget.ListAdapter#areAllItemsEnabled()
	 */
	public boolean areAllItemsEnabled() {
		return false;
	}

	/* (non-Javadoc)
	 * @see android.widget.ListAdapter#isEnabled(int)
	 */
	public boolean isEnabled(int position) {
		return false;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	public int getCount() {
		return valid.size();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int position) {
		return entity.getFields()[valid.get(position)];
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int position) {
		return valid.get(position);
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
		EntityDetailView dv =(EntityDetailView)convertView;
		if(dv == null) {
			dv = new EntityDetailView(context, session, detail, entity, valid.get(position));
			dv.setCallListener(listener);
		} else{
			dv.setParams(session, detail, entity, valid.get(position));
			dv.setCallListener(listener);
		}
		return dv;
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
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
	 */
	public void unregisterDataSetObserver(DataSetObserver observer) {
	}

}
