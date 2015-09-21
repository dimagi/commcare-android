package org.commcare.android.framework;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

/**
 * Fragment to display Detail content. Not meant for handling nested Detail objects.
 * @author jschweers
 *
 */
public class EntityDetailFragment extends Fragment {
    public static final String CHILD_DETAIL_INDEX = "edf_child_detail_index";
    public static final String DETAIL_ID = "edf_detail_id";
    public static final String DETAIL_INDEX = "edf_detail_index";
    public static final String CHILD_REFERENCE = "edf_detail_reference";

    private AndroidSessionWrapper asw;
    private EntityDetailAdapter adapter;
    private EntityDetailAdapter.EntityDetailViewModifier modifier;

    public EntityDetailFragment() {
        super();
        this.asw = CommCareApplication._().getCurrentSessionWrapper();
    }

    public void setEntityDetailModifier(EntityDetailAdapter.EntityDetailViewModifier edvm){
        this.modifier = edvm;
        if(adapter != null) {
            adapter.setModifier(edvm);
        }
    }

    public static final String MODIFIER_KEY = "modifier";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(modifier instanceof Parcelable) {
            outState.putParcelable(MODIFIER_KEY, (Parcelable)modifier);
        } else {
            throw new IllegalArgumentException(modifier + " must implement Parcelable!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(savedInstanceState != null){
            this.modifier = savedInstanceState.getParcelable(MODIFIER_KEY);
        }

        // Note that some of this setup could be moved into onAttach if it would help performance
        Bundle args = getArguments();

        final Detail detail = asw.getSession().getDetail(args.getString(DETAIL_ID));
        Detail childDetail = detail;
        final int thisIndex = args.getInt(CHILD_DETAIL_INDEX, -1);
        final boolean detailCompound = thisIndex != -1;
        if (detailCompound) {
            childDetail = detail.getDetails()[thisIndex];
        }

        NodeEntityFactory factory = new NodeEntityFactory(childDetail, asw.getEvaluationContext());
        final Entity entity = factory.getEntity(SerializationUtil.deserializeFromBundle(
                        args, CHILD_REFERENCE, TreeReference.class)
        );

        View rootView = inflater.inflate(R.layout.entity_detail_list, container, false);
        final Activity thisActivity = getActivity();
        final DetailCalloutListener detailCalloutListener =
                thisActivity instanceof DetailCalloutListener ? ((DetailCalloutListener)thisActivity) : null;

        final ListView listView = ((ListView) rootView.findViewById(R.id.screen_entity_detail_list));
        adapter = new EntityDetailAdapter(
            thisActivity, asw.getSession(), childDetail, entity, 
            detailCalloutListener, args.getInt(DETAIL_INDEX)
        );
        adapter.setModifier(modifier);
        listView.setAdapter(adapter);
        return rootView;
    }

}
