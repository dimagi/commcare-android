package org.commcare.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.commcare.CommCareApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityDetailAdapter;
import org.commcare.adapters.ListItemViewModifier;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ModifiableEntityDetailAdapter;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.modern.models.Entity;
import org.commcare.modern.models.NodeEntityFactory;
import org.commcare.suite.model.Detail;
import org.commcare.utils.DetailCalloutListener;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;

/**
 * Fragment to display Detail content. Not meant for handling nested Detail objects.
 *
 * @author jschweers
 */
public class EntityDetailFragment extends Fragment {
    public static final String CHILD_DETAIL_INDEX = "edf_child_detail_index";
    public static final String DETAIL_ID = "edf_detail_id";
    public static final String DETAIL_INDEX = "edf_detail_index";
    public static final String CHILD_REFERENCE = "edf_detail_reference";

    protected ListItemViewModifier modifier;

    final AndroidSessionWrapper asw;
    ModifiableEntityDetailAdapter adapter;

    public EntityDetailFragment() {
        this.asw = CommCareApplication._().getCurrentSessionWrapper();
    }

    public void setModifier(ListItemViewModifier modifier) {
        this.modifier = modifier;
        if (adapter != null) {
            adapter.setModifier(modifier);
        }
    }

    public static final String MODIFIER_KEY = "modifier";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (modifier instanceof Parcelable) {
            outState.putParcelable(MODIFIER_KEY, (Parcelable)modifier);
        } else {
            throw new IllegalArgumentException(modifier + " must implement Parcelable!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            this.modifier = savedInstanceState.getParcelable(MODIFIER_KEY);
        }

        // Note that some of this setup could be moved into onAttach if it would help performance
        Detail childDetail = getChildDetail();
        TreeReference childReference = getChildReference();
        NodeEntityFactory factory = new NodeEntityFactory(childDetail, this.getFactoryContext(childReference));

        View rootView = inflater.inflate(R.layout.entity_detail_list, container, false);
        final Activity thisActivity = getActivity();
        final Entity entity = factory.getEntity(childReference);
        final DetailCalloutListener detailCalloutListener =
                thisActivity instanceof DetailCalloutListener ? ((DetailCalloutListener)thisActivity) : null;
        adapter = new EntityDetailAdapter(
                thisActivity, childDetail, entity,
                detailCalloutListener, getArguments().getInt(DETAIL_INDEX),
                modifier
        );
        ((ListView)rootView.findViewById(R.id.screen_entity_detail_list)).setAdapter((ListAdapter)adapter);

        return rootView;
    }

    /**
     * @return The Detail whose information will be displayed. Will never be a parent detail.
     */
    protected Detail getChildDetail() {
        Bundle args = getArguments();
        final Detail detail = asw.getSession().getDetail(args.getString(DETAIL_ID));
        final int childIndex = args.getInt(CHILD_DETAIL_INDEX, -1);
        final boolean detailCompound = childIndex != -1;
        if (detailCompound) {
            return detail.getDetails()[childIndex];
        }
        return detail;
    }

    /**
     * @return Reference to the detail returned by getChildDetail
     */
    protected TreeReference getChildReference() {
        return SerializationUtil.deserializeFromBundle(getArguments(), CHILD_REFERENCE, TreeReference.class);
    }

    protected EvaluationContext getFactoryContext(TreeReference childReference) {
        EvaluationContext factoryContext;
        if (getArguments().getInt(CHILD_DETAIL_INDEX, -1) != -1) {
            factoryContext = prepareEvaluationContext(childReference);
        } else {
            factoryContext = asw.getEvaluationContext();
        }
        factoryContext.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler());
        return factoryContext;
    }

    /**
     * @return Reference to this fragment's parent detail, which may be the same as this fragment's detail.
     */
    private Detail getParentDetail() {
        return asw.getSession().getDetail(getArguments().getString(DETAIL_ID));
    }

    /**
     * Creates an evaluation context which is preloaded with all of the variables and context from
     * the parent detail definition.
     *
     * @param childReference The qualified reference for the nodeset in the parent detail
     * @return An evaluation context ready to be used as the base of the subnode detail, including
     * any variable definitions included by the parent.
     */
    private EvaluationContext prepareEvaluationContext(TreeReference childReference) {
        EvaluationContext sessionContext = asw.getEvaluationContext();
        EvaluationContext parentDetailContext = new EvaluationContext(sessionContext, childReference);
        getParentDetail().populateEvaluationContextVariables(parentDetailContext);
        return parentDetailContext;
    }
}
