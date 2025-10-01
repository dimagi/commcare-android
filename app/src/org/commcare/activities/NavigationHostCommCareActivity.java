package org.commcare.activities;

import android.os.Bundle;

import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

import javax.annotation.Nullable;

public abstract class NavigationHostCommCareActivity<R> extends CommCareActivity<R>{

    private NavController.OnDestinationChangedListener destinationListener;

    protected NavController navController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());
        destinationListener = FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener();
        navController = getHostFragment().getNavController();
    }

    @Override
    protected void onResume() {
        super.onResume();
        navController.addOnDestinationChangedListener(destinationListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        navController.removeOnDestinationChangedListener(destinationListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destinationListener = null;
    }

    protected abstract int getLayoutResource();

    protected abstract NavHostFragment getHostFragment();
}
