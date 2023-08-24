package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

/**
 * Shows a page that simply displays a message to the user
 *
 * @author dviggiano
 */
public class ConnectIdMessageActivity extends CommCareActivity<ConnectIdMessageActivity>
        implements WithUIController {
    private ConnectIdMessageActivityUiController uiController;

    private String title = null;
    private String message = null;
    private String button = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("");

        title = getString(getIntent().getIntExtra(ConnectIdConstants.TITLE, 0));
        message = getString(getIntent().getIntExtra(ConnectIdConstants.MESSAGE, 0));
        button = getString(getIntent().getIntExtra(ConnectIdConstants.BUTTON, 0));

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
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIdMessageActivityUiController(this);
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
