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

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentStatePagerAdapter#getItem(int)
     */
    @Override
    public Fragment getItem(int i) {
        Fragment fragment = new EntityDetailFragment();
        Bundle args = new Bundle();
        args.putString(EntityDetailFragment.DETAIL_ID, detail.getId());
        if (detail.isCompound()) {
            args.putInt(EntityDetailFragment.CHILD_DETAIL_INDEX, i);
        }
        args.putInt(EntityDetailFragment.DETAIL_INDEX, detailIndex);
        args.putBoolean(EntityDetailFragment.HAS_DETAIL_CALLOUT_LISTENER, hasDetailCalloutListener);
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
