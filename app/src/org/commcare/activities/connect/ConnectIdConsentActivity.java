package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

/**
 * Shows the page that gets the user's consent when registering a new account
 *
 * @author dviggiano
 */
public class ConnectIdConsentActivity extends CommCareActivity<ConnectIdConsentActivity>
        implements WithUIController {
    private ConnectIdConsentActivityUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_consent_title));

        uiController.setupUI();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIdConsentActivityUiController(this);
    }

    public void finish(boolean accepted) {
        Intent intent = new Intent(getIntent());

        setResult(accepted ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleButtonPress() {
        finish(true);
    }
}
