package org.commcare.activities.connect;

import android.os.Bundle;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

public class ConnectMessagingActivity extends CommCareActivity<ConnectMessagingActivity> {
    public static final String CCC_MESSAGE = "ccc_message";

    public NavController controller;
    NavController.OnDestinationChangedListener destinationListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_messaging);
        setTitle(R.string.connect_messaging_title);

        destinationListener = FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener();

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect_messaging);
        controller = navHostFragment.getNavController();
        controller.addOnDestinationChangedListener(destinationListener);
        NavigationUI.setupActionBarWithNavController(this, controller);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        String action = getIntent().getStringExtra("action");
        if(action != null) {
            handleRedirect(action);
        }
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    protected void onDestroy() {
        if (destinationListener != null) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_connect_messaging);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                navController.removeOnDestinationChangedListener(destinationListener);
            }
            destinationListener = null;
        }

        super.onDestroy();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }

    private void handleRedirect(String action) {
        if(action.equals(CCC_MESSAGE)) {
            ConnectManager.init(this);
            PersonalIdManager.getInstance().unlockConnect(this, success -> {
                if (success) {
                    String channelId = getIntent().getStringExtra(
                            ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID);
                    ConnectMessagingChannelRecord channel = ConnectMessagingDatabaseHelper.getMessagingChannel(this, channelId);

                    int fragmentId = channel!=null && channel.getConsented() ? R.id.connectMessageFragment : R.id.channelListFragment;

                    Bundle bundle = new Bundle();
                    bundle.putString("channel_id", channelId);

                    NavOptions options = new NavOptions.Builder()
                            .setPopUpTo(controller.getGraph().getStartDestinationId(), true)
                            .build();
                    controller.navigate(fragmentId, bundle, options);
                }
            });
        }
    }

}
