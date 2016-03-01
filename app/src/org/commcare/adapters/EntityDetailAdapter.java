package org.commcare.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import org.commcare.android.framework.ModifiableEntityDetailAdapter;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.view.EntityDetailView;
import org.commcare.suite.model.Detail;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ctsims
 */
public class EntityDetailAdapter implements ListAdapter, ModifiableEntityDetailAdapter {

    private final Context context;
    private final Detail detail;
    private final Entity entity;
    private final DetailCalloutListener listener;
    private final List<Integer> valid;
    private final int detailIndex;

    private ListItemViewModifier modifier;

    public EntityDetailAdapter(Context context, Detail detail, Entity entity,
                               DetailCalloutListener listener, int detailIndex,
                               ListItemViewModifier modifier) {
        this.context = context;
        this.detail = detail;
        this.entity = entity;
        this.listener = listener;
        valid = new ArrayList<>();
        for (int i = 0; i < entity.getNumFields(); ++i) {
            if (entity.isValidField(i)) {
                valid.add(i);
            }
        }
        this.detailIndex = detailIndex;
        this.modifier = modifier;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }

    @Override
    public int getCount() {
        return valid.size();
    }

    @Override
    public Object getItem(int position) {
        return entity.getField(valid.get(position));
    }

    @Override
    public long getItemId(int position) {
        return valid.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        EntityDetailView dv = (EntityDetailView)convertView;
        if (dv == null) {
            dv = new EntityDetailView(context, detail, entity,
                    valid.get(position), detailIndex);
            dv.setCallListener(listener);
        } else {
            dv.setParams(detail, entity, valid.get(position), detailIndex);
            dv.setCallListener(listener);
        }
        if (modifier != null) {
            modifier.modify(dv, position);
        }
        return dv;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return getCount() > 0;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public void setModifier(ListItemViewModifier modifier) {
        this.modifier = modifier;
    }
}
