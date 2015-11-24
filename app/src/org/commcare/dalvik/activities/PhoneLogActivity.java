package org.commcare.dalvik.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.telecom.Call;
import android.view.View;

import org.commcare.dalvik.R;

/**
 * @author ctsims
 */
public class PhoneLogActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_logs);

        final PhoneLogAdapter phoneLogAdapter =
                new PhoneLogAdapter(getSupportFragmentManager());
        ViewPager mViewPager = (ViewPager)findViewById(R.id.phone_log_pager);
        mViewPager.setAdapter(phoneLogAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                phoneLogAdapter.loadAdapterData(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        this.setTitle("CommCare");
    }

    public static class PhoneLogAdapter extends FragmentPagerAdapter {
        private final CallLogActivity callLogActivity;
        private final MessageLogActivity messageLogActivity;

        public PhoneLogAdapter(FragmentManager fm) {
            super(fm);
            callLogActivity = new CallLogActivity();
            messageLogActivity = new MessageLogActivity();
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return callLogActivity;
                case 1:
                    return messageLogActivity;
                default:
                    return null;
            }
        }

        public void loadAdapterData(int position) {
            switch (position) {
                case 0:
                    callLogActivity.loadData();
                    break;
                case 1:
                    messageLogActivity.loadData();
                    break;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
