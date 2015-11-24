package org.commcare.dalvik.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import org.commcare.dalvik.R;

/**
 * @author ctsims
 */
public class PhoneLogActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_logs);

        PhoneLogAdapter phoneLogAdapter =
                new PhoneLogAdapter(getSupportFragmentManager());
        ViewPager mViewPager = (ViewPager)findViewById(R.id.phone_log_pager);
        mViewPager.setAdapter(phoneLogAdapter);

        this.setTitle("CommCare");
    }

    public static class PhoneLogAdapter extends FragmentPagerAdapter {

        public PhoneLogAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new CallLogActivity();
                case 1:
                    return new MessageLogActivity();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
