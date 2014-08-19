package org.commcare.android.adapters;

import java.util.List;

import org.commcare.android.framework.EntityDetailFragment;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.suite.model.Detail;
import org.odk.collect.android.views.media.AudioController;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class EntityDetailPagerAdapter extends FragmentStatePagerAdapter {
	
	Detail detail;
	int detailIndex;

	public EntityDetailPagerAdapter(FragmentManager fm, Detail detail, int detailIndex) {	
		super(fm);
		this.detail = detail;
		this.detailIndex = detailIndex;
	}

	@Override
	public Fragment getItem(int i) {
		System.out.println("EntityDetailPagerAdapter.getItem is getting item #" + i);
		Fragment fragment = new EntityDetailFragment();
		Bundle args = new Bundle();
		args.putString(EntityDetailFragment.DETAIL_ID, detail.getId());
		if (detail.isCompound()) {
			args.putInt(EntityDetailFragment.CHILD_DETAIL_INDEX, i);
		}
		args.putInt(EntityDetailFragment.DETAIL_INDEX, detailIndex);
		//System.out.println("[jls] sending DETAIL_ID=" + detail.getId() + ", CHILD_DETAIL_INDEX=" + (detail.isCompound() ? i : -1) + ", DETAIL_INDEX=" + detailIndex);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public int getCount() {
		return detail.isCompound() ? detail.getDetails().length : 1;
	}

}
