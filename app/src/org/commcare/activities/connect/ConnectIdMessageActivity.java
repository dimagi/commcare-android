package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

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
    private boolean deactivationFromAtlMessage = false;
    private String phone = null;
    private String secretKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("");

        title = getString(getIntent().getIntExtra(ConnectConstants.TITLE, 0));
        message = getString(getIntent().getIntExtra(ConnectConstants.MESSAGE, 0));
        button = getString(getIntent().getIntExtra(ConnectConstants.BUTTON, 0));

        if (getIntent().hasExtra(ConnectConstants.BUTTON2)) {
            button2 = getString(getIntent().getIntExtra(ConnectConstants.BUTTON2, 0));
        }
        if (getIntent().hasExtra(ConnectConstants.DEACTIVATION_FROM)) {
            deactivationFromAtlMessage = getIntent().getBooleanExtra(ConnectConstants.DEACTIVATION_FROM, false);
        }
        if (getIntent().hasExtra(ConnectConstants.PHONE)) {
            phone = getIntent().getStringExtra(ConnectConstants.PHONE);
        }
        if (getIntent().hasExtra(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE)) {
            secretKey = getIntent().getStringExtra(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE);
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
        intent.putExtra(ConnectConstants.DEACTIVATE_BUTTON, secondButton ? button2 : button);
        intent.putExtra(ConnectConstants.DEACTIVATION_FROM, deactivationFromAtlMessage);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleButtonPress(boolean secondButton) {
        if (phone != null && !secondButton) {
            initiateDeactivation();
        } else {
            finish(true, secondButton);
        }
    }

    private void initiateDeactivation() {
        boolean isBusy = !ApiConnectId.requestInitiateAccountDeactivation(this, phone, secretKey, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        if (json.getBoolean("success")) {
                            finish(true, false);
                        }
                    }
                } catch (IOException e) {
                    Logger.exception("User deactivation", e);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
            }

            @Override
            public void processNetworkFailure() {
            }

            @Override
            public void processOldApiError() {
            }
        });

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }
}
