package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;

import okhttp3.RequestBody;

/**
 * Perform post request to external server and trigger a sync upon success.
 * Can be used to perform server transactions, to, for instance, claim a case
 * and pull it down to the mobile device
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@ManagedUi(R.layout.http_request_layout)
public class PostRequestActivity
        extends SyncCapableCommCareActivity<PostRequestActivity>
        implements ConnectorWithHttpResponseProcessor<PostRequestActivity> {
    private static final String TAG = PostRequestActivity.class.getSimpleName();

    private static final String TASK_LAUNCHED_KEY = "task-launched-key";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";
    private static final String ERROR_MESSAGE_KEY = "error-message-key";

    public static final String URL_KEY = "url-key";
    public static final String PARAMS_KEY = "params-key";

    @UiElement(value = R.id.request_button, locale = "post.request.button")
    private Button retryButton;

    @UiElement(value = R.id.error_message)
    private TextView errorMessageBox;

    private URL url;
    private HashMap<String, String> params;
    private String errorMessage;
    private boolean hasTaskLaunched;
    private boolean inErrorState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadStateFromSavedInstance(savedInstanceState);
        loadStateFromIntent(getIntent());

        setupUI();

        if (!inErrorState) {
            makePostRequest();
        }
    }

    private void loadStateFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            hasTaskLaunched = savedInstanceState.getBoolean(TASK_LAUNCHED_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);
            errorMessage = savedInstanceState.getString(ERROR_MESSAGE_KEY);
        }
    }

    private void loadStateFromIntent(Intent intent) {
        if (intent.hasExtra(URL_KEY) && intent.hasExtra(PARAMS_KEY)) {
            url = (URL)intent.getSerializableExtra(URL_KEY);
            Object o = intent.getSerializableExtra(PARAMS_KEY);
            params = (HashMap<String, String>)o;
        } else {
            enterErrorState(Localization.get("post.generic.error"));
        }
    }

    private void setupUI() {
        if (inErrorState) {
            enterErrorState();
        } else {
            retryButton.setVisibility(View.GONE);
        }

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryPost();
            }
        });
    }

    private void performSync() {
        formAndDataSyncer.syncDataForLoggedInUser(this, false, false);
    }

    private void makePostRequest() {
        try {
            if (!hasTaskLaunched && !inErrorState) {
                RequestBody requestBody = ModernHttpRequester.getPostBody(params);
                ModernHttpTask postTask = new ModernHttpTask(this, url.toString(), new HashMap(), getContentHeadersForXFormPost(requestBody), requestBody, HTTPMethod.POST, null);
                postTask.connect((CommCareTaskConnector)this);
                postTask.executeParallel();
                hasTaskLaunched = true;
            }
        } catch (IOException e) {
            handleIOException(e);
        }
    }

    private void enterErrorState() {
        inErrorState = true;
        errorMessageBox.setVisibility(View.VISIBLE);
        errorMessageBox.setText(errorMessage);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void enterErrorState(String message) {
        errorMessage = message;
        enterErrorState();
    }

    private void retryPost() {
        inErrorState = false;
        errorMessage = "";
        errorMessageBox.setVisibility(View.GONE);
        hasTaskLaunched = false;
        makePostRequest();
    }

    private HashMap<String, String> getContentHeadersForXFormPost(RequestBody postBody) throws IOException {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Content-Length", String.valueOf(postBody.contentLength()));
        return headers;
    }


    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(TASK_LAUNCHED_KEY, hasTaskLaunched);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
    }

    @Override
    protected void updateUiAfterDataPullOrSend(String message, boolean success) {
        if (success) {
            setResult(RESULT_OK);
            finish();
        } else {
            enterErrorState(message);
        }
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        performSync();
    }

    @Override
    public void processClientError(int responseCode) {
        String clientErrorMessage;
        switch (responseCode) {
            case 409:
                clientErrorMessage = Localization.get("post.conflict.error");
                break;
            case 410:
                clientErrorMessage = Localization.get("post.gone.error");
                break;
            default:
                clientErrorMessage = Localization.get("post.client.error", responseCode + "");
                break;
        }
        enterErrorState(clientErrorMessage);
    }

    @Override
    public void processServerError(int responseCode) {
        enterErrorState(Localization.get("post.server.error", responseCode + ""));
    }

    @Override
    public void processOther(int responseCode) {
        enterErrorState(Localization.get("post.unknown.response", responseCode + ""));
    }

    @Override
    public void handleIOException(IOException exception) {
        if (exception instanceof AuthenticationInterceptor.PlainTextPasswordException) {
            enterErrorState(Localization.get("auth.request.not.using.https", url.toString()));
        } else if (exception instanceof IOException) {
            enterErrorState(Localization.get("post.io.error", exception.getMessage()));
        }
    }

    @Override
    public void onBackPressed() {
        if (inErrorState) {
            finish();
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case DataPullTask.DATA_PULL_TASK_ID:
                title = Localization.get("sync.communicating.title");
                message = Localization.get("sync.progress.purge");
                break;
            case ModernHttpTask.SIMPLE_HTTP_TASK_ID:
                title = Localization.get("post.dialog.title");
                message = Localization.get("post.dialog.body");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in activity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        return false;
    }

    @Override
    public boolean usesSubmissionProgressBar() {
        return false;
    }

}
