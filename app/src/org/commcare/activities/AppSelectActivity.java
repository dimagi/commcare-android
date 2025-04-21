package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

/**
 * Note: Not currently in use
 * Shows a page with installed apps in a grid view for the user to select from
 *
 * @author dviggiano
 */
public class AppSelectActivity extends CommCareActivity<AppSelectActivity>
        implements WithUIController {
    private AppSelectActivityUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new AppSelectActivityUiController(this);
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
