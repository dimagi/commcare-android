package org.commcare.adapters;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import org.commcare.CommCareApplication;
import org.commcare.fragments.EntityDetailFragment;
import org.commcare.fragments.EntitySubnodeDetailFragment;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Detail;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;

/**
 * Subclass of FragmentStatePagerAdapter for populating a ViewPager (swipe-based paging widget) with entity detail fields.
 *
 * @author jschweers
 */
public class EntityDetailPagerAdapter extends FragmentStatePagerAdapter {

    private ListItemViewModifier modifier;
    private final Detail detail;
    private final int detailIndex;
    private final TreeReference mEntityReference;
    private final Detail[] displayableChildDetails;

    public EntityDetailPagerAdapter(FragmentManager fm, Detail detail, int detailIndex,
                                    TreeReference reference, ListItemViewModifier modifier) {
        super(fm);
        this.detail = detail;
        this.detailIndex = detailIndex;
        this.mEntityReference = reference;
        this.modifier = modifier;
        this.displayableChildDetails = detail.getDisplayableChildDetails(
                getEvalContextForEntity(mEntityReference));
    }

    @Override
    public Fragment getItem(int i) {
        EntityDetailFragment fragment;
        if (detail.getNodeset() != null || (detail.isCompound() && displayableChildDetails[i].getNodeset() != null)) {
            fragment = new EntitySubnodeDetailFragment();
        } else {
            fragment = new EntityDetailFragment();
        }
        fragment.setModifier(modifier);
        Bundle args = new Bundle();
        args.putString(EntityDetailFragment.DETAIL_ID, detail.getId());
        if (detail.isCompound()) {
            args.putInt(EntityDetailFragment.CHILD_DETAIL_INDEX, i);
        }
        args.putInt(EntityDetailFragment.DETAIL_INDEX, detailIndex);
        SerializationUtil.serializeToBundle(args, EntityDetailFragment.CHILD_REFERENCE, mEntityReference);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        Detail detailShowing = detail.isCompound() ? displayableChildDetails[position] : detail;
        return detailShowing.getTitle().getText().evaluate();
    }

    @Override
    public int getCount() {
        return detail.isCompound() ? displayableChildDetails.length : 1;
    }

    public static EvaluationContext getEvalContextForEntity(TreeReference ref) {
        CommCareSession currentSession =
                CommCareApplication.instance().getCurrentSessionWrapper().getSession();
        EvaluationContext baseEvalContext = currentSession.getEvaluationContext(
                new AndroidInstanceInitializer(currentSession));
        return new EvaluationContext(baseEvalContext, ref);
    }
}
