package org.commcare.android.adapters;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import org.commcare.android.framework.EntityDetailFragment;
import org.commcare.android.util.SerializationUtil;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

/**
 * Subclass of FragmentStatePagerAdapter for populating a ViewPager (swipe-based paging widget) with entity detail fields.
 *
 * @author jschweers
 */
public class EntityDetailPagerAdapter extends FragmentStatePagerAdapter {

    private EntityDetailAdapter.EntityDetailViewModifier modifier;
    Detail detail;
    int detailIndex;
    boolean hasDetailCalloutListener;
    TreeReference mEntityReference;

    public EntityDetailPagerAdapter(FragmentManager fm, Detail detail, int detailIndex, TreeReference reference, boolean hasDetailCalloutListener) {    
        super(fm);
        this.detail = detail;
        this.detailIndex = detailIndex;
        this.hasDetailCalloutListener = hasDetailCalloutListener;
        this.mEntityReference = reference;
    }

    public EntityDetailPagerAdapter(FragmentManager fm, Detail detail, int detailIndex, TreeReference reference, boolean hasDetailCalloutListener, EntityDetailAdapter.EntityDetailViewModifier modifier) {
        this(fm, detail, detailIndex, reference, hasDetailCalloutListener);
        this.modifier = modifier;
    }

    @Override
    public Fragment getItem(int i) {
        EntityDetailFragment fragment = new EntityDetailFragment();
        fragment.setEntityDetailModifier(modifier);
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
        return (detail.isCompound() ? detail.getDetails()[position] : detail).getTitle().getText().evaluate();
    }

    @Override
    public int getCount() {
        return detail.isCompound() ? detail.getDetails().length : 1;
    }
}
