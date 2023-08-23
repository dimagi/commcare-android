package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

/**
 * @author dviggiano
 * Shows the page that gets the user's consent when registering a new account
 */
public class ConnectIDConsentActivity extends CommCareActivity<ConnectIDConsentActivity>
        implements WithUIController {
    private ConnectIDConsentActivityUIController uiController;

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
        uiController = new ConnectIDConsentActivityUIController(this);
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
