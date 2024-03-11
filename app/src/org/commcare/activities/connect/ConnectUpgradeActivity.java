package org.commcare.activities.connect;

import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.views.dialogs.CustomProgressDialog;

import androidx.lifecycle.ViewModelProvider;

public class ConnectUpgradeActivity extends CommCareActivity<ConnectUpgradeActivity>
        implements WithUIController {
    private ConnectUpgradeActivityUiController uiController;
    private ConnectUpgradeViewModel viewModel;

    private final Runnable viewModelCallback = () -> {
        uiController.setMessage(viewModel.getMessage(this));
        uiController.setButtonText(viewModel.getButtonText(this));
        uiController.setButtonEnabled(viewModel.getButtonEnabled());
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.connect_upgrade_title);

        uiController.setupUI();
        viewModel = new ViewModelProvider(this).get(ConnectUpgradeViewModel.class);
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        viewModel.setCallback(viewModelCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();

        viewModel.setCallback(null);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectUpgradeActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void handleButtonPress() {
        viewModel.handleButtonPress(this);
    }
}
