/**
 * 
 */
package org.commcare.android.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.view.EntityDetailView;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCareSession;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ctsims
 *
 */
public class EntityDetailAdapter implements ListAdapter {

    public interface EntityDetailViewModifier {
        void modifyEntityDetailView(EntityDetailView edv);
    }
    
    Context context;
    CommCareSession session;
    Detail detail;
    Entity entity;
    DetailCalloutListener listener;
    List<Integer> valid;
    int detailIndex;

    public void setModifier(EntityDetailViewModifier modifier) {
        this.modifier = modifier;
    }

    EntityDetailViewModifier modifier;

    public EntityDetailAdapter(Context context, CommCareSession session, Detail detail, Entity entity, 
            DetailCalloutListener listener, int detailIndex) {
        this.context = context;
        this.session = session;
        this.detail = detail;
        this.entity = entity;
        this.listener = listener;
        valid = new ArrayList<Integer>();
        for(int i = 0 ; i < entity.getNumFields() ; ++i ) {
            if(entity.isValidField(i)) {
                valid.add(i);
            }
        }
        this.detailIndex = detailIndex;
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
        EntityDetailView dv =(EntityDetailView)convertView;
        if (dv == null) {
            dv = new EntityDetailView(context, session, detail, entity,
                    valid.get(position), detailIndex);
            dv.setCallListener(listener);
        } else{
            dv.setParams(session, detail, entity, valid.get(position), detailIndex);
            dv.setCallListener(listener);
        }
        if(modifier != null){
            modifier.modifyEntityDetailView(dv);
        }
        dv.setLineColor((position % 2) != 0);
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

}
