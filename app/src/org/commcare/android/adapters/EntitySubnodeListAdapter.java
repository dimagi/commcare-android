package org.commcare.android.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

import java.util.Vector;

/**
 * Created by jschweers on 8/24/2015.
 * <p/>
 * Adapter for taking a nodeset, contextualizing it against an entity,
 * and then displaying one item for each node in the resulting set.
 */
public class EntitySubnodeListAdapter implements ListAdapter {

    private Context context;
    private Detail detail;
    private NodeEntityFactory factory;
    private Vector<TreeReference> references;

    public EntitySubnodeListAdapter(Context context, Detail detail, NodeEntityFactory factory, Vector<TreeReference> references) {
        this.context = context;
        this.detail = detail;
        this.factory = factory;
        this.references = references;
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
        return references.size();
    }

    @Override
    public Object getItem(int position) {
        return references.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        EntityView view = (EntityView) convertView;
        Entity entity = factory.getEntity(references.get(position));
        if (view == null) {
            view = new EntityView(context, detail, entity, null, null, position, false);
        } else {
            view.refreshViewsForNewEntity(entity, false, position);
        }
        return view;
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
