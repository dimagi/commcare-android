/**
 * 
 */
package org.commcare.android.framework;

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

    @Override
    public int getCount() {
        return wrapped.getCount();
    }

    @Override
    public Object getItem(int position) {
        return wrapped.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return wrapped.getItemId(position);
    }

    @Override
    public int getItemViewType(int position) {
        return wrapped.getItemViewType(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = wrapped.getView(position, convertView, parent);
        if(v instanceof TextView) {
            ((TextView)v).setText(displayVals[position]);
        }
        return v;
    }

    @Override
    public int getViewTypeCount() {
        return wrapped.getViewTypeCount();
    }

    @Override
    public boolean hasStableIds() {
        return wrapped.hasStableIds();
    }

    @Override
    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        wrapped.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        wrapped.unregisterDataSetObserver(observer);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return wrapped.getDropDownView(position, convertView, parent);
    }

}
