package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.tasks.ConnectionDiagnosticTask;
import org.commcare.tasks.LogSubmissionTask;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.MarkupUtil;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity that will diagnose various connection problems that a user may be facing.
 *
 * @author srengesh
 */
@ManagedUi(R.layout.connection_diagnostic)
public class ConnectionDiagnosticActivity extends CommCareActivity<ConnectionDiagnosticActivity> {
    private static final String TAG = ConnectionDiagnosticActivity.class.getSimpleName();

    public static final String logUnsetPostURLMessage = "CCHQ ping test: post URL not set.";

    @UiElement(value = R.id.run_connection_test, locale = "connection.test.run")
    Button btnRunTest;

    @UiElement(value = R.id.output_message, locale = "connection.test.messages")
    TextView txtInteractiveMessages;

    @UiElement(value = R.id.settings_button, locale = "connection.test.access.settings")
    Button settingsButton;

    @UiElement(value = R.id.report_button, locale = "connection.test.report.button.message")
    Button reportButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        btnRunTest.setOnClickListener(v -> {
            ConnectionDiagnosticTask<ConnectionDiagnosticActivity> mConnectionDiagnosticTask =
                    new ConnectionDiagnosticTask<ConnectionDiagnosticActivity>(getApplicationContext()) {
                        @Override
                        //<R> receiver, <C> result.
                        //<C> is the return from DoTaskBackground, of type ArrayList<Boolean>
                        protected void deliverResult(ConnectionDiagnosticActivity receiver, ConnectivityStatus.NetworkState failedTest) {
                            //user-caused connection issues
                            switch (failedTest) {
                                case CONNECTED:
                                    receiver.txtInteractiveMessages.setText(Localization.get("connection.task.success"));
                                    receiver.txtInteractiveMessages.setVisibility(View.VISIBLE);
                                    receiver.settingsButton.setVisibility(View.INVISIBLE);
                                    receiver.reportButton.setVisibility(View.INVISIBLE);
                                    break;
                                case COMMCARE_DOWN:
                                    //unable to ping commcare -- report this to cchq
                                    receiver.txtInteractiveMessages.setText(Localization.get("connection.task.commcare.html.fail"));
                                    receiver.txtInteractiveMessages.setVisibility(View.VISIBLE);
                                    receiver.reportButton.setVisibility(View.VISIBLE);
                                    break;
                                case DISCONNECTED:
                                case CAPTIVE_PORTAL:
                                    //get the appropriate display message based on what the problem is
                                    String displayMessage = failedTest == ConnectivityStatus.NetworkState.DISCONNECTED ?
                                            Localization.get("connection.task.internet.fail")
                                            : Localization.get("connection.captive_portal.action");

                                    receiver.txtInteractiveMessages.setText(displayMessage);
                                    receiver.txtInteractiveMessages.setVisibility(View.VISIBLE);
                                    receiver.settingsButton.setVisibility(View.VISIBLE);
                                    break;
                            }
                        }

                        @Override
                        protected void deliverUpdate(ConnectionDiagnosticActivity receiver, String... update) {
                            receiver.txtInteractiveMessages.setText((Localization.get("connection.test.update.message")));
                        }

                        @Override
                        protected void deliverError(ConnectionDiagnosticActivity receiver, Exception e) {
                            receiver.txtInteractiveMessages.setText(Localization.get("connection.test.error.message"));
                            receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                        }
                    };

            mConnectionDiagnosticTask.connect(ConnectionDiagnosticActivity.this);
            mConnectionDiagnosticTask.executeParallel();
        });

        //Set a button that allows you to change your airplane mode settings
        this.settingsButton.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));

        this.reportButton.setOnClickListener(v -> {
            String url = LogSubmissionTask.getSubmissionUrl(CommCareApplication.instance().getCurrentApp().getAppPreferences());

            if (url != null) {
                CommCareUtil.executeLogSubmission(url, false);
                ConnectionDiagnosticActivity.this.finish();
                Toast.makeText(
                        CommCareApplication.instance(),
                        Localization.get("connection.task.report.commcare.popup"),
                        Toast.LENGTH_LONG).show();
            } else {
                Logger.log(ConnectionDiagnosticTask.CONNECTION_DIAGNOSTIC_REPORT, logUnsetPostURLMessage);
                ConnectionDiagnosticActivity.this.txtInteractiveMessages.setText(MarkupUtil.localizeStyleSpannable(ConnectionDiagnosticActivity.this, "connection.task.unset.posturl"));
                ConnectionDiagnosticActivity.this.txtInteractiveMessages.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == ConnectionDiagnosticTask.CONNECTION_ID) {
            String title = Localization.get("connection.test.run.title");
            String message = Localization.get("connection.test.now.running");
            CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
            dialog.setCancelable();
            return dialog;
        } else {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in ConnectionDiagnosticActivity");
            return null;
        }
    }
}
