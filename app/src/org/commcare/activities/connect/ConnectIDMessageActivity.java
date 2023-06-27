package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIDMessageActivity extends CommCareActivity<ConnectIDMessageActivity>
implements WithUIController {
    private ConnectIDMessageActivityUIController uiController;

    private String title = null;
    private String message = null;
    private String button = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("");

        title = getIntent().getStringExtra(ConnectIDConstants.TITLE);
        message = getIntent().getStringExtra(ConnectIDConstants.MESSAGE);
        button = getIntent().getStringExtra(ConnectIDConstants.BUTTON);

        uiController.setupUI();
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.setTitle(title);
        uiController.setMessage(message);
        uiController.setButtonText(button);
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public CommCareActivityUIController getUIController() { return this.uiController; }

    @Override
    public void initUIController() {
        uiController = new ConnectIDMessageActivityUIController(this);
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleButtonPress() {
        finish(true);
    }
}
