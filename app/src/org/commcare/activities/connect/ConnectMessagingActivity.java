package org.commcare.activities.connect;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.MessageManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.connect.database.NotificationRecordDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogController;

import static org.commcare.connect.ConnectConstants.CCC_MESSAGE;
import static org.commcare.connect.ConnectConstants.NETWORK_ACTIVITY_MESSAGING_CHANNEL_ID;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_ID;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;

public class ConnectMessagingActivity extends NavigationHostCommCareActivity<ConnectMessagingActivity> implements DialogController {
    public static final String CHANNEL_ID = "channel_id";
    private static final String KEY_PROGRESS_DIALOG_FRAGMENT = "progress_dialog_fragment";
    private static final int REQUEST_CODE_PERSONAL_ID_ACTIVITY = 1000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.connect_messaging_title);

        PersonalIdManager personalIdManager = PersonalIdManager.getInstance();
        personalIdManager.init(this);

        if (personalIdManager.isloggedIn()) {
            handleRedirectIfAny();
        } else {
            Toast.makeText(this, R.string.personalid_not_login_from_fcm_error, Toast.LENGTH_LONG).show();
            personalIdManager.launchPersonalId(this, REQUEST_CODE_PERSONAL_ID_ACTIVITY);
            finish();
        }
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        NavigationUI.setupActionBarWithNavController(this, navController);
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
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == NETWORK_ACTIVITY_MESSAGING_CHANNEL_ID) {
            return CustomProgressDialog.newInstance(
                    null,
                    getString(R.string.please_wait),
                    taskId
            );
        } else {
            // Prevent this dialog from showing for every other API call.
            return null;
        }
    }

    @Override
    public void showProgressDialog(int taskId) {
        if (taskId == NETWORK_ACTIVITY_MESSAGING_CHANNEL_ID) {
            CustomProgressDialog dialog = generateProgressDialog(taskId);

            if (dialog != null) {
                dialog.showNow(getSupportFragmentManager(), KEY_PROGRESS_DIALOG_FRAGMENT);
            }
        }
    }

    @Override
    public void dismissProgressDialogForTask(int taskId) {
        if (taskId == NETWORK_ACTIVITY_MESSAGING_CHANNEL_ID) {
            CustomProgressDialog dialog = (CustomProgressDialog) getSupportFragmentManager()
                    .findFragmentByTag(KEY_PROGRESS_DIALOG_FRAGMENT);

            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }

    private void handleRedirectIfAny() {
        String action = getIntent().getStringExtra(REDIRECT_ACTION);
        if (CCC_MESSAGE.equals(action)) {
            PersonalIdManager.getInstance().init(this);
            FirebaseAnalyticsUtil.reportNotificationEvent(
                    AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_CLICK,
                    AnalyticsParamValue.REPORT_NOTIFICATION_CLICK_NOTIFICATION_TRAY,
                    action,
                    getIntent().getStringExtra(NOTIFICATION_ID)
            );
            PersonalIdManager.getInstance().unlockConnect(this, success -> {
                if (success) {
                    String channelId = getIntent().getStringExtra(
                            ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID);
                    String notificationId = getIntent().getStringExtra(NOTIFICATION_ID);
                    if (!TextUtils.isEmpty(notificationId)) {
                        NotificationRecordDatabaseHelper.INSTANCE.updateReadStatus(this, notificationId, true);
                    }
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
        MessageManager.retrieveMessages(this, (success, error) -> {  // This is required to update the local DB for channels
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
        MessageManager.getChannelEncryptionKey(this, channel, (success, error) -> {
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
