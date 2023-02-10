package org.commcare.activities;

import static org.commcare.activities.EntitySelectActivity.BARCODE_FETCH;
import static org.commcare.suite.model.QueryPrompt.INPUT_TYPE_CHECKBOX;
import static org.commcare.suite.model.QueryPrompt.INPUT_TYPE_DATERANGE;
import static org.commcare.suite.model.QueryPrompt.INPUT_TYPE_SELECT1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.modern.util.Pair;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.SessionRegistrationHelper;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects 'query datum' in the current session. Prompts user for query
 * params, makes query to server and stores xml 'fixture' response into current
 * session. Allows for 'case search and claim' workflow when used inside a
 * 'remote-request' entry in conjuction with entity select datum and sync
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class QueryRequestActivity
        extends SaveSessionCommCareActivity<QueryRequestActivity>
        implements HttpResponseProcessor, WithUIController {
    private static final String TAG = QueryRequestActivity.class.getSimpleName();

    private static final String ANSWERED_USER_PROMPTS_KEY = "answered_user_prompts";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";
    private static final String ERROR_MESSAGE_KEY = "error-message-key";

    private boolean inErrorState;
    private String errorMessage;
    private RemoteQuerySessionManager remoteQuerySessionManager;

    private QueryRequestUiController mRequestUiController;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        if (!isFinishing()) {
            mRequestUiController.setupUI();
        }
    }

    private ArrayList<String> getSupportedPrompts() {
        ArrayList<String> supportedPrompts = new ArrayList<>();
        supportedPrompts.add(INPUT_TYPE_SELECT1);
        supportedPrompts.add(INPUT_TYPE_DATERANGE);
        supportedPrompts.add(INPUT_TYPE_CHECKBOX);
        return supportedPrompts;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == BARCODE_FETCH) {
            if (resultCode == RESULT_OK) {
                String result = intent.getStringExtra("SCAN_RESULT");
                if (result != null) {
                    result = result.trim();
                    mRequestUiController.setPendingPromptResult(result);
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    public void makeQueryRequest() {
        clearErrorState();
        ModernHttpTask httpTask = new ModernHttpTask(this,
                remoteQuerySessionManager.getBaseUrl().toString(),
                remoteQuerySessionManager.getRawQueryParams(false),
                new HashMap(),
                new AuthInfo.CurrentAuth());
        httpTask.connect((CommCareTaskConnector)this);
        httpTask.executeParallel();
    }

    private void clearErrorState() {
        errorMessage = "";
        inErrorState = false;
        mRequestUiController.hideError();
    }

    private void enterErrorState(String message) {
        errorMessage = message;
        enterErrorState();
    }

    private void enterErrorState() {
        inErrorState = true;
        Log.e(TAG, errorMessage);
        mRequestUiController.showError(errorMessage);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        loadStateFromSavedInstance(savedInstanceState);
    }

    private void loadStateFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            errorMessage = savedInstanceState.getString(ERROR_MESSAGE_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);

            if (inErrorState) {
                enterErrorState();
            }

            Map<String, String> answeredPrompts =
                    (Map<String, String>)savedInstanceState.getSerializable(ANSWERED_USER_PROMPTS_KEY);
            mRequestUiController.reloadStateUsingAnswers(answeredPrompts);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putSerializable(ANSWERED_USER_PROMPTS_KEY,
                remoteQuerySessionManager.getUserAnswers());
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        // todo pass url and requestData to this callback
        Pair<ExternalDataInstance, String> instanceOrError = remoteQuerySessionManager.buildExternalDataInstance(
                responseData,
                remoteQuerySessionManager.getBaseUrl().toString(),
                remoteQuerySessionManager.getRawQueryParams(false));
        if (instanceOrError.first == null) {
            enterErrorState(Localization.get("query.response.format.error",
                    instanceOrError.second));
        } else if (isResponseEmpty(instanceOrError.first)) {
            Toast.makeText(this, Localization.get("query.response.empty"), Toast.LENGTH_SHORT).show();
        } else {
            CommCareApplication.instance().getCurrentSession().setQueryDatum(instanceOrError.first);
            setResult(RESULT_OK);
            finish();
        }
    }

    private boolean isResponseEmpty(ExternalDataInstance instance) {
        return !instance.getRoot().hasChildren();
    }

    @Override
    public void processClientError(int responseCode) {
        enterErrorState(Localization.get("post.client.error", responseCode + ""));
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
            enterErrorState(Localization.get("auth.request.not.using.https", remoteQuerySessionManager.getBaseUrl().toString()));
        } else if (exception instanceof IOException) {
            enterErrorState(Localization.get("post.io.error", exception.getMessage()));
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case ModernHttpTask.SIMPLE_HTTP_TASK_ID:
                title = Localization.get("query.dialog.title");
                message = Localization.get("query.dialog.body");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return mRequestUiController;
    }

    @Override
    public void initUIController() {
        initRemoteQuerySessionManager();
        if (remoteQuerySessionManager != null) {
            mRequestUiController = new QueryRequestUiController(this, remoteQuerySessionManager);
        }
    }

    private void initRemoteQuerySessionManager() {
        try {
            AndroidSessionWrapper sessionWrapper = CommCareApplication.instance().getCurrentSessionWrapper();
            try {
                remoteQuerySessionManager = RemoteQuerySessionManager.buildQuerySessionManager(
                        sessionWrapper.getSession(), sessionWrapper.getEvaluationContext(), getSupportedPrompts());
            } catch (XPathException xpe) {
                new UserfacingErrorHandling<>().createErrorDialog(this, xpe.getMessage(), true);
                return;
            }

            if (remoteQuerySessionManager == null) {
                Log.e(TAG, "Tried to launch remote query activity at wrong time in session.");
                setResult(RESULT_CANCELED);
                finish();
            }
        } catch (SessionUnavailableException e) {
            SessionRegistrationHelper.redirectToLogin(this);
            finish();
        }
    }
}
