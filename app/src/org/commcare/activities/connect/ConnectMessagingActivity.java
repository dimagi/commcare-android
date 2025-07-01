package org.commcare.activities.connect;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.MessageManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;

import static org.commcare.connect.ConnectConstants.CCC_MESSAGE;

public class ConnectMessagingActivity extends NavigationHostCommCareActivity<ConnectMessagingActivity> {
    public static final String CHANNEL_ID = "channel_id";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.connect_messaging_title);

        NavigationUI.setupActionBarWithNavController(this, navController);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        handleRedirectIfAny();
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.activity_connect_messaging;
    }

    @Override
    protected NavHostFragment getHostFragment() {
        return (NavHostFragment)getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_connect_messaging);
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }


    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }

    private void handleRedirectIfAny() {
        String action = getIntent().getStringExtra("action");
        if (CCC_MESSAGE.equals(action)) {
            PersonalIdManager.getInstance().init(this);
            PersonalIdManager.getInstance().unlockConnect(this, success -> {
                if (success) {
                    String channelId = getIntent().getStringExtra(
                            ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID);
                    if (TextUtils.isEmpty(channelId)) {
                        showFailureMessage(getString(R.string.connect_messaging_pn_wrong_channel));
                    } else {
                        handleChannelForValidity(channelId);
                    }
                }
            });
        }
    }

    private void handleChannelForValidity(String channelId) {
        ConnectMessagingChannelRecord connectMessagingChannelRecord = ConnectMessagingDatabaseHelper.getMessagingChannel(this, channelId);
        if (connectMessagingChannelRecord == null) {
            handleNoChannel(channelId); //This happens if local DB doesn't have the channel yet
        } else {
            handleValidChannel(connectMessagingChannelRecord);  // DB has the channel
        }
    }

    private void handleNoChannel(String channelId) {
        MessageManager.retrieveMessages(this, success -> {  // This is required to update the local DB for channels
            ConnectMessagingChannelRecord connectMessagingChannelRecord = ConnectMessagingDatabaseHelper.getMessagingChannel(this, channelId);
            if (connectMessagingChannelRecord == null) {
                showFailureMessage(getString(R.string.connect_messaging_pn_wrong_channel));
            } else {
                handleValidChannel(connectMessagingChannelRecord);
            }
        });
    }

    private void handleValidChannel(ConnectMessagingChannelRecord channel) {
        if (channel.getConsented()) {
            checkForChannelEncryptionKey(channel);
        } else {
            showChannelListFragmentForConsent(channel.getChannelId());
        }
    }

    private void checkForChannelEncryptionKey(ConnectMessagingChannelRecord channel) {
        if (TextUtils.isEmpty(channel.getKey())) {
            retrieveChannelEncryptionKey(channel);
        } else {
            showConnectMessageFragment(channel.getChannelId());
        }
    }

    private void retrieveChannelEncryptionKey(ConnectMessagingChannelRecord channel) {
        MessageManager.getChannelEncryptionKey(this, channel, success -> {
            if (success) {
                showConnectMessageFragment(channel.getChannelId());
            } else {
                showFailureMessage(getString(R.string.connect_messaging_pn_wrong_channel));
            }
        });
    }

    private void showFailureMessage(String failureMessage) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show();
    }

    private void showChannelListFragmentForConsent(String channelId) {
        Bundle bundle = new Bundle();
        bundle.putString(CHANNEL_ID, channelId);
        navController.navigate(R.id.channelListFragment, bundle, getNavOptions());
    }

    private void showConnectMessageFragment(String channelId) {
        Bundle bundle = new Bundle();
        bundle.putString(CHANNEL_ID, channelId);
        navController.navigate(R.id.connectMessageFragment, bundle, getNavOptions());
    }

    private NavOptions getNavOptions() {
        return new NavOptions.Builder()
                .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                .build();
    }

}
