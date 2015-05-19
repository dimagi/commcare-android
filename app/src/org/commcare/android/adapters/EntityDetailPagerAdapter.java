package org.commcare.android.adapters;

import org.commcare.android.framework.EntityDetailFragment;
import org.commcare.android.util.SerializationUtil;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

/**
 * Subclass of FragmentStatePagerAdapter for populating a ViewPager (swipe-based paging widget) with entity detail fields.
 * @author jschweers
 */
public class EntityDetailPagerAdapter extends FragmentStatePagerAdapter {

    private EntityDetailAdapter.EntityDetailViewModifier modifier;
    final Detail detail;
    final int detailIndex;
    final boolean hasDetailCalloutListener;
    final TreeReference mEntityReference;

    public EntityDetailPagerAdapter(final FragmentManager fm, final Detail detail, final int detailIndex, final TreeReference reference, final boolean hasDetailCalloutListener) {
        super(fm);
        this.detail = detail;
        this.detailIndex = detailIndex;
        this.hasDetailCalloutListener = hasDetailCalloutListener;
        this.mEntityReference = reference;
    }

    public EntityDetailPagerAdapter(final FragmentManager fm, final Detail detail, final int detailIndex, final TreeReference reference, final boolean hasDetailCalloutListener, final EntityDetailAdapter.EntityDetailViewModifier modifier) {
        this(fm, detail, detailIndex, reference, hasDetailCalloutListener);
        this.modifier = modifier;
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentStatePagerAdapter#getItem(int)
     */
    @Override
    public Fragment getItem(final int i) {
        final EntityDetailFragment fragment = new EntityDetailFragment();
        fragment.setEntityDetailModifier(modifier);
        final Bundle args = new Bundle();
        args.putString(EntityDetailFragment.DETAIL_ID, detail.getId());
        if (detail.isCompound()) {
            args.putInt(EntityDetailFragment.CHILD_DETAIL_INDEX, i);
        }
        args.putInt(EntityDetailFragment.DETAIL_INDEX, detailIndex);
        SerializationUtil.serializeToBundle(args, EntityDetailFragment.CHILD_REFERENCE, mEntityReference);
        fragment.setArguments(args);
        return fragment;
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.view.PagerAdapter#getCount()
     */
    @Override
    public int getCount() {
        return detail.isCompound() ? detail.getDetails().length : 1;
    }

}
