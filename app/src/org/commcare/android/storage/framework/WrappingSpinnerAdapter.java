/**
 * 
 */
package org.commcare.android.storage.framework;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class WrappingSpinnerAdapter implements SpinnerAdapter {
	
	SpinnerAdapter wrapped;
	String[] displayVals;
	
	public WrappingSpinnerAdapter(SpinnerAdapter wrapped, String[] displayVals) {
		this.wrapped = wrapped;
		this.displayVals= displayVals;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return wrapped.getCount();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public Object getItem(int position) {
		return wrapped.getItem(position);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		return wrapped.getItemId(position);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemViewType(int)
	 */
	@Override
	public int getItemViewType(int position) {
		return wrapped.getItemViewType(position);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = wrapped.getView(position, convertView, parent);
		if(v instanceof TextView) {
			((TextView)v).setText(displayVals[position]);
		}
		return v;
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getViewTypeCount()
	 */
	@Override
	public int getViewTypeCount() {
		return wrapped.getViewTypeCount();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#hasStableIds()
	 */
	@Override
	public boolean hasStableIds() {
		return wrapped.hasStableIds();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return wrapped.isEmpty();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
	 */
	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		wrapped.registerDataSetObserver(observer);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
	 */
	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		wrapped.unregisterDataSetObserver(observer);
	}

	/* (non-Javadoc)
	 * @see android.widget.SpinnerAdapter#getDropDownView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return wrapped.getDropDownView(position, convertView, parent);
	}

}
