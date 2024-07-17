package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectManager;
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
    private String button2 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("");

        title = getString(getIntent().getIntExtra(ConnectConstants.TITLE, 0));
        message = getString(getIntent().getIntExtra(ConnectConstants.MESSAGE, 0));
        button = getString(getIntent().getIntExtra(ConnectConstants.BUTTON, 0));

        if(getIntent().hasExtra(ConnectConstants.BUTTON2)) {
            button2 = getString(getIntent().getIntExtra(ConnectConstants.BUTTON2, 0));
        }

        uiController.setupUI();
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.setTitle(title);
        uiController.setMessage(message);
        uiController.setButtonText(button);
        uiController.setButton2Text(button2);
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

    public void finish(boolean success, boolean secondButton) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.BUTTON2, secondButton);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleButtonPress(boolean secondButton) {
        finish(true, secondButton);
    }
}
