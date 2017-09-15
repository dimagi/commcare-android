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
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityDetailAdapter;
import org.commcare.adapters.ListItemViewModifier;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ModifiableEntityDetailAdapter;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.suite.model.Detail;
import org.commcare.cases.entity.EntityUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.DetailCalloutListener;
import org.commcare.utils.SerializationUtil;
import org.commcare.views.UserfacingErrorHandling;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

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
        this.asw = CommCareApplication.instance().getCurrentSessionWrapper();
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
        Detail detailForDisplay = getDetailToUseForDisplay();
        TreeReference referenceToDisplay = getReferenceToDisplay();
        EvaluationContext contextForFactory = getFactoryContextForRef(referenceToDisplay);
        NodeEntityFactory factory = new NodeEntityFactory(detailForDisplay, contextForFactory);

        View rootView = inflater.inflate(R.layout.entity_detail_list, container, false);
        try {
            final Activity thisActivity = getActivity();
            final Entity entity = factory.getEntity(referenceToDisplay);
            final DetailCalloutListener detailCalloutListener =
                    thisActivity instanceof DetailCalloutListener ? ((DetailCalloutListener)thisActivity) : null;
            adapter = new EntityDetailAdapter(
                    thisActivity, detailForDisplay, entity,
                    detailCalloutListener, getArguments().getInt(DETAIL_INDEX),
                    modifier
            );
            ((ListView)rootView.findViewById(R.id.screen_entity_detail_list)).setAdapter((ListAdapter)adapter);
        } catch (XPathException xe) {
            UserfacingErrorHandling.logErrorAndShowDialog((CommCareActivity)getActivity(), xe, true);
        }

        return rootView;
    }

    protected EvaluationContext getFactoryContextForRef(TreeReference referenceToDisplay) {
        EvaluationContext context = EntityUtil.getEntityFactoryContext(referenceToDisplay,
                getArguments().getInt(CHILD_DETAIL_INDEX, -1) != -1,
                getParentDetail(),
                CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext());
        context.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler());
        return context;
    }

    /**
     * @return The Detail whose information will be displayed. If the root detail passed to this
     * fragment is NOT a compound detail, then this method will just return that detail. If it is
     * compound, then this will return one of its children (the one corresponding to the given
     * CHILD_DETAIL_INDEX)
     */
    protected Detail getDetailToUseForDisplay() {
        Bundle args = getArguments();
        final Detail detail = asw.getSession().getDetail(args.getString(DETAIL_ID));
        final int childIndex = args.getInt(CHILD_DETAIL_INDEX, -1);
        final boolean rootDetailIsCompound = childIndex != -1;
        if (rootDetailIsCompound) {
            return getChildDetailForDisplay(detail, childIndex);
        }
        return detail;
    }

    protected Detail getChildDetailForDisplay(Detail parentDetail, int childIndex) {
        EvaluationContext contextForChildDetailDisplayConditions =
                EntityUtil.prepareCompoundEvaluationContext(getReferenceToDisplay(), parentDetail,
                        CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext());
        return parentDetail.getDisplayableChildDetails(contextForChildDetailDisplayConditions)[childIndex];
    }

    /**
     * @return Reference to the detail returned by getDetailToUseForDisplay
     */
    protected TreeReference getReferenceToDisplay() {
        return SerializationUtil.deserializeFromBundle(
                getArguments(), CHILD_REFERENCE, TreeReference.class);
    }

    /**
     * @return Reference to this fragment's parent detail, which may be the same as this
     * fragment's detail.
     */
    private Detail getParentDetail() {
        return asw.getSession().getDetail(getArguments().getString(DETAIL_ID));
    }
}
