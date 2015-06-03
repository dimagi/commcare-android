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
import org.odk.collect.android.views.media.AudioController;

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

    public interface EntityDetailViewModifier {
        void modifyEntityDetailView(EntityDetailView edv);
    }
    
    Context context;
    CommCareSession session;
    Detail detail;
    Entity entity;
    DetailCalloutListener listener;
    List<Integer> valid;
    AudioController controller;
    int detailIndex;

    public void setModifier(EntityDetailViewModifier modifier) {
        this.modifier = modifier;
    }

    EntityDetailViewModifier modifier;

    public EntityDetailAdapter(Context context, CommCareSession session, Detail detail, Entity entity, 
            DetailCalloutListener listener, AudioController controller, int detailIndex) {    
        this.context = context;
        this.session = session;
        this.detail = detail;
        this.entity = entity;
        this.listener = listener;
        this.controller = controller;
        valid = new ArrayList<Integer>(); 
        for(int i = 0 ; i < entity.getNumFields() ; ++i ) {
            if(entity.isValidField(i)) {
                valid.add(i);
            }
        }
        this.detailIndex = detailIndex;
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
        return entity.getField(valid.get(position));
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
        if (dv == null) {
            dv = new EntityDetailView(context, session, detail, entity, valid.get(position), controller, 
                    detailIndex);
            dv.setCallListener(listener);
        } else{
            dv.setParams(session, detail, entity, valid.get(position), detailIndex);
            dv.setCallListener(listener);
        }
        if(modifier != null){
            modifier.modifyEntityDetailView(dv);
        }
        dv.setLineColor(position % 2 != 0);
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
