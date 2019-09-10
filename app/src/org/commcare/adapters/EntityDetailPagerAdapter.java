package org.commcare.adapters;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import org.commcare.CommCareApplication;
import org.commcare.cases.entity.EntityUtil;
import org.commcare.fragments.EntityDetailFragment;
import org.commcare.fragments.EntitySubnodeDetailFragment;
import org.commcare.suite.model.Detail;
import org.commcare.utils.SerializationUtil;
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
                EntityUtil.prepareCompoundEvaluationContext(mEntityReference, detail,
                        CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext()));
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

}
