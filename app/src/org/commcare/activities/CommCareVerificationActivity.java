package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.tasks.VerificationTask;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.SizeBoundVector;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Performs media validation and allows for the installation of missing media
 */
public class CommCareVerificationActivity
        extends CommCareActivity<CommCareVerificationActivity>
        implements OnClickListener {
    private static final String TAG = CommCareVerificationActivity.class.getSimpleName();

    private TextView missingMediaPrompt;
    private static final int MENU_UNZIP = Menu.FIRST;

    private static final String KEY_REQUIRE_REFRESH = "require_referesh";
    public static final String KEY_LAUNCH_FROM_SETTINGS = "from_settings";

    private static final int DIALOG_VERIFY_PROGRESS = 0;
    private static final String MISSING_MEDIA_TEXT_KEY = "missing-media-text-key";
    private static final String NEW_MEDIA_KEY = "new-media-to-validate";

    /**
     * Return code for launching media inflater (selector).
     */
    private static final int GET_MULTIMEDIA = 0;

    /**
     * When new media is installed, set this so that verification is fired
     * onPostResume.
     */
    private boolean newMediaToValidate = false;

    /**
     * Indicates whether this activity was launched from the AppManagerActivity
     */
    private boolean fromManager;

    /**
     * Indicates whether this activity was launched explicitly from the settings menu in
     * CommCareHomeActivity
     */
    private boolean fromSettings;
    private boolean isFirstLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommCareApplication._().isConsumerApp()) {
            setContentView(R.layout.blank_missing_multimedia_layout);
        } else {
            setContentView(R.layout.missing_multimedia_layout);
        }

        Button retryButton = (Button)findViewById(R.id.screen_multimedia_retry);
        retryButton.setOnClickListener(this);
        retryButton.setText(Localization.get("verify.retry"));

        this.fromSettings = this.getIntent().
                getBooleanExtra(KEY_LAUNCH_FROM_SETTINGS, false);
        this.fromManager = this.getIntent().
                getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
        if (fromManager) {
            Button skipButton = (Button)findViewById(R.id.skip_verification_button);
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(this);
        }

        missingMediaPrompt = (TextView)findViewById(R.id.MissingMediaPrompt);

        loadStateFromBundle(savedInstanceState);

        isFirstLaunch = (savedInstanceState == null);
    }

    private void loadStateFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(MISSING_MEDIA_TEXT_KEY)) {
                missingMediaPrompt.setText(savedInstanceState.getString(MISSING_MEDIA_TEXT_KEY));
            } else {
                missingMediaPrompt.setText(Localization.get("verify.checking"));
            }
            if (savedInstanceState.containsKey(NEW_MEDIA_KEY)) {
                newMediaToValidate = savedInstanceState.getBoolean(NEW_MEDIA_KEY);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(MISSING_MEDIA_TEXT_KEY, missingMediaPrompt.getText().toString());
        outState.putBoolean(NEW_MEDIA_KEY, newMediaToValidate);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // It is possible that the CommCare screen was left off in the VerificationActivity, but
        // then something was done on the Manager screen that means we no longer want to be here --
        // VerificationActivity should be displayed to a user only if we were explicitly sent from
        // the manager, or if the state of installed apps calls for it
        boolean shouldBeHere = fromManager || fromSettings || CommCareApplication._().shouldSeeMMVerification();
        if (!shouldBeHere) {
            // send back to dispatch activity
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (isFirstLaunch) {
            isFirstLaunch = false;
            verifyResourceInstall();
        } else if (newMediaToValidate) {
            newMediaToValidate = false;
            verifyResourceInstall();
        }
    }

    private void verifyResourceInstall() {
        // there shouldn't be another verification task running, but just in case
        cancelCurrentTask();

        VerificationTask<CommCareVerificationActivity> task =
                new VerificationTask<CommCareVerificationActivity>(DIALOG_VERIFY_PROGRESS) {
                    @Override
                    protected void deliverResult(CommCareVerificationActivity receiver,
                                                 SizeBoundVector<MissingMediaException> problems) {
                        if (problems == null) {
                            receiver.handleVerificationSuccess();
                        } else {
                            if (problems.size() == 0) {
                                receiver.handleVerificationSuccess();
                            } else if (problems.size() > 0) {
                                receiver.handleVerificationProblems(problems);
                            }
                        }
                    }

                    @Override
                    protected void deliverUpdate(CommCareVerificationActivity receiver,
                                                 int[]... update) {
                        final int done = update[0][0];
                        final int pending = update[0][1];

                        receiver.updateProgress(
                                Localization.get("verification.progress", new String[]{"" + done, "" + pending}),
                                DIALOG_VERIFY_PROGRESS);
                        updateProgressBar(done, pending, DIALOG_VERIFY_PROGRESS);
                    }

                    @Override
                    protected void deliverError(CommCareVerificationActivity receiver,
                                                Exception e) {
                        receiver.missingMediaPrompt.setText(Localization.get("verify.check.failed"));
                    }
                };
        task.connect(this);
        task.execute((String[])null);
    }

    @Override
    public void taskCancelled() {
    }

    private void handleVerificationProblems(SizeBoundVector<MissingMediaException> problems) {
        String message = Localization.get("verification.fail.message");

        Hashtable<String, Vector<String>> problemList = new Hashtable<>();
        for (Enumeration en = problems.elements(); en.hasMoreElements(); ) {
            MissingMediaException ure = (MissingMediaException)en.nextElement();
            String res = ure.getResource().getResourceId();

            Vector<String> list;
            if (problemList.containsKey(res)) {
                list = problemList.get(res);
            } else {
                list = new Vector<>();
            }
            list.addElement(ure.getMessage());

            problemList.put(res, list);

        }

        for (Enumeration en = problemList.keys(); en.hasMoreElements(); ) {
            String resource = (String)en.nextElement();

            message += "\n-----------";
            for (String s : problemList.get(resource)) {
                message += "\n" + prettyString(s);
            }
        }
        if (problems.getAdditional() > 0) {
            message += "\n\n..." + problems.getAdditional() + " more";
        }

        missingMediaPrompt.setText(message);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == GET_MULTIMEDIA && resultCode == Activity.RESULT_OK) {
            // we found some media, so try validating it
            newMediaToValidate = true;
            return;
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void handleVerificationSuccess() {
        CommCareApplication._().getCurrentApp().setMMResourcesValidated();
        if (Intent.ACTION_VIEW.equals(CommCareVerificationActivity.this.getIntent().getAction())) {
            //Call out to CommCare Home
            Intent i = new Intent(getApplicationContext(), DispatchActivity.class);
            i.putExtra(KEY_REQUIRE_REFRESH, true);
            startActivity(i);
        } else {
            //Good to go
            Intent i = new Intent(getIntent());
            i.putExtra(KEY_REQUIRE_REFRESH, true);
            setResult(RESULT_OK, i);
        }
        if (!CommCareApplication._().isConsumerApp()) {
            Toast.makeText(getApplicationContext(), Localization.get("verification.success.message"), Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_UNZIP, 0, "Install Multimedia").setIcon(android.R.drawable.ic_menu_gallery);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UNZIP:
                Intent i = new Intent(this, MultimediaInflaterActivity.class);
                i.putExtra(MultimediaInflaterActivity.EXTRA_FILE_DESTINATION,
                        CommCareApplication._().getCurrentApp().storageRoot());
                this.startActivityForResult(i, GET_MULTIMEDIA);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (CommCareApplication._().isConsumerApp()) {
            return CustomProgressDialog.newInstance("Starting Up", "Initializing your application...", taskId);
        }
        
        if (taskId == DIALOG_VERIFY_PROGRESS) {
            CustomProgressDialog dialog =
                    CustomProgressDialog.newInstance(
                            Localization.get("verification.title"),
                            Localization.get("verification.checking"),
                            taskId);
            dialog.addProgressBar();
            if (fromSettings || fromManager) {
                dialog.addCancelButton();
            }
            return dialog;
        }
        Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                + "any valid possibilities in CommCareVerificationActivity");
        return null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.skip_verification_button:
                Intent i = new Intent(getIntent());
                setResult(RESULT_CANCELED, i);
                finish();
                break;
            case R.id.screen_multimedia_retry:
                verifyResourceInstall();
                break;
        }
    }

    private String prettyString(String rawString) {
        int marker = rawString.indexOf(Environment.getExternalStorageDirectory().getPath());
        if (marker < 0) {
            return rawString;
        } else {
            return rawString.substring(marker);
        }
    }
}
