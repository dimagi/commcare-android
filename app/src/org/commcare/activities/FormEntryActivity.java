package org.commcare.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Rect;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.VideoView;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.FormFileSystemHelpers;
import org.commcare.activities.components.FormNavigationUI;
import org.commcare.activities.components.FormRelevancyUpdating;
import org.commcare.activities.components.ImageCaptureProcessing;
import org.commcare.android.javarosa.PollSensorController;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.AndroidArrayDataSource;
import org.commcare.utils.CompoundIntentList;
import org.commcare.views.media.MediaLayout;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.android.javarosa.PollSensorAction;
import org.commcare.interfaces.AdvanceToNextListener;
import org.commcare.interfaces.FormSaveCallback;
import org.commcare.interfaces.FormSavedListener;
import org.commcare.interfaces.WidgetChangedListener;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.logging.analytics.TimedStatsTracker;
import org.commcare.logic.FormController;
import org.commcare.logic.PropertyManager;
import org.commcare.models.ODKStorage;
import org.commcare.preferences.FormEntryPreferences;
import org.commcare.provider.FormsProviderAPI.FormsColumns;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.tasks.FormLoaderTask;
import org.commcare.tasks.SaveToDiskTask;
import org.commcare.utils.Base64Wrapper;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StringUtils;
import org.commcare.utils.UriToFilePath;
import org.commcare.views.QuestionsView;
import org.commcare.views.ResizingImageView;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.widgets.IntentWidget;
import org.commcare.views.widgets.QuestionWidget;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.form.api.FormEntrySession;
import org.javarosa.form.api.FormEntrySessionReplayer;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xform.util.CalendarUtils;
import org.javarosa.xpath.XPathArityException;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.crypto.spec.SecretKeySpec;

/**
 * Displays questions, animates transitions between
 * questions, and allows the user to enter data.
 */
public class FormEntryActivity extends SaveSessionCommCareActivity<FormEntryActivity>
        implements  FormSavedListener, FormSaveCallback,
        WithUIController, AdvanceToNextListener, WidgetChangedListener {
    private static final String TAG = FormEntryActivity.class.getSimpleName();

    // Defines for FormEntryActivity
    protected static final boolean EXIT = true;
    private static final boolean DO_NOT_EXIT = false;
    protected static final boolean EVALUATE_CONSTRAINTS = true;
    protected static final boolean DO_NOT_EVALUATE_CONSTRAINTS = false;

    // Request codes for returning data from specified intent.
    public static final int IMAGE_CAPTURE = 1;
    public static final int BARCODE_CAPTURE = 2;
    public static final int AUDIO_VIDEO_FETCH = 3;
    public static final int LOCATION_CAPTURE = 5;
    private static final int HIERARCHY_ACTIVITY = 6;
    public static final int IMAGE_CHOOSER = 7;
    private static final int FORM_PREFERENCES_KEY = 8;
    public static final int INTENT_CALLOUT = 10;
    private static final int HIERARCHY_ACTIVITY_FIRST_START = 11;
    public static final int SIGNATURE_CAPTURE = 12;
    public static final int INTENT_COMPOUND_CALLOUT = 13;

    // Extra returned from gp activity
    public static final String LOCATION_RESULT = "LOCATION_RESULT";

    // Identifies the gp of the form used to launch form entry
    private static final String KEY_FORMPATH = "formpath";
    public static final String KEY_INSTANCEDESTINATION = "instancedestination";
    public static final String KEY_FORM_CONTENT_URI = "form_content_uri";
    public static final String KEY_INSTANCE_CONTENT_URI = "instance_content_uri";
    public static final String KEY_AES_STORAGE_KEY = "key_aes_storage";
    public static final String KEY_HEADER_STRING = "form_header";
    public static final String KEY_INCOMPLETE_ENABLED = "org.odk.collect.form.management";
    public static final String KEY_RESIZING_ENABLED = "org.odk.collect.resizing.enabled";
    private static final String KEY_HAS_SAVED = "org.odk.collect.form.has.saved";
    public static final String KEY_FORM_ENTRY_SESSION = "form_entry_session";
    public static final String KEY_RECORD_FORM_ENTRY_SESSION = "record_form_entry_session";
    private static final String KEY_WIDGET_WITH_VIDEO_PLAYING = "index-of-widget-with-video-playing-on-pause";
    private static final String KEY_POSITION_OF_VIDEO_PLAYING = "position-of-video-playing-on-pause";

    /**
     * Intent extra flag to track if this form is an archive. Used to trigger
     * return logic when this activity exits to the home screen, such as
     * whether to redirect to archive view or sync the form.
     */
    public static final String IS_ARCHIVED_FORM = "is-archive-form";

    // Identifies whether this is a new form, or reloading a form after a screen
    // rotation (or similar)
    private static final String KEY_FORM_LOAD_HAS_TRIGGERED = "newform";
    private static final String KEY_FORM_LOAD_FAILED = "form-failed";
    private static final String KEY_LOC_ERROR = "location-not-enabled";
    private static final String KEY_LOC_ERROR_PATH = "location-based-xpath-error";

    private static final int MENU_LANGUAGES = Menu.FIRST + 1;
    private static final int MENU_HIERARCHY_VIEW = Menu.FIRST + 2;
    private static final int MENU_SAVE = Menu.FIRST + 3;
    private static final int MENU_PREFERENCES = Menu.FIRST + 4;

    public static final String NAV_STATE_NEXT = "next";
    public static final String NAV_STATE_DONE = "done";
    public static final String NAV_STATE_QUIT = "quit";
    public static final String NAV_STATE_BACK = "back";

    private String mFormPath;
    // Path to a particular form instance
    public static String mInstancePath;
    private String mInstanceDestination;

    private SecretKeySpec symetricKey = null;

    public static FormController mFormController;

    private boolean mIncompleteEnabled = true;
    private boolean hasFormLoadBeenTriggered = false;
    private boolean hasFormLoadFailed = false;
    private String locationRecieverErrorAction = null;
    private String badLocationXpath = null;

    private GestureDetector mGestureDetector;

    private int indexOfWidgetWithVideoPlaying = -1;
    private int positionOfVideoProgress = -1;

    private FormLoaderTask<FormEntryActivity> mFormLoaderTask;
    private SaveToDiskTask mSaveToDiskTask;

    private Uri formProviderContentURI = FormsColumns.CONTENT_URI;
    private Uri instanceProviderContentURI = InstanceColumns.CONTENT_URI;

    private static String mHeaderString;

    // Was the form saved? Used to set activity return code.
    private boolean hasSaved = false;

    private BroadcastReceiver mLocationServiceIssueReceiver;

    // marked true if we are in the process of saving a form because the user
    // database & key session are expiring. Being set causes savingComplete to
    // broadcast a form saving intent.
    private boolean savingFormOnKeySessionExpiration = false;
    private FormEntrySession formEntryRestoreSession;
    private boolean recordEntrySession;
    private FormEntryActivityUIController uiController;

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // must be at the beginning of any activity that can be called from an external intent
        try {
            ODKStorage.createODKDirs();
        } catch (RuntimeException e) {
            Logger.exception(e);
            UserfacingErrorHandling.createErrorDialog(this, e.getMessage(), EXIT);
            return;
        }

        uiController.setupUI();
        mGestureDetector = new GestureDetector(this);

        // Load JavaRosa modules. needed to restore forms.
        new XFormsModule().registerModule();

        // needed to override rms property manager
        org.javarosa.core.services.PropertyManager.setPropertyManager(new PropertyManager(
                getApplicationContext()));

        loadStateFromBundle(savedInstanceState);

        // Need to override CalendarUtil's month localizer
        CalendarUtils.setArrayDataSource(new AndroidArrayDataSource(this));

        // Check to see if this is a screen flip or a new form load.
        Object data = this.getLastCustomNonConfigurationInstance();
        if (data instanceof FormLoaderTask) {
            mFormLoaderTask = (FormLoaderTask) data;
        } else if (data instanceof SaveToDiskTask) {
            mSaveToDiskTask = (SaveToDiskTask) data;
            mSaveToDiskTask.setFormSavedListener(this);
        } else if (hasFormLoadBeenTriggered && !hasFormLoadFailed) {
            // Screen orientation change
            uiController.refreshView();
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        /*
         * EventLog accepts only proper Strings as input, but prior to this version,
         * Android would try to send SpannedStrings to it, thus crashing the app.
         * This makes sure the title is actually a String.
         * This fixes bug 174626.
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2
                && item.getTitleCondensed() != null) {
            item.setTitleCondensed(item.getTitleCondensed().toString());
        }

        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public void formSaveCallback() {
        // note that we have started saving the form
        savingFormOnKeySessionExpiration = true;
        // start saving form, which will call the key session logout completion
        // function when it finishes.
        saveIncompleteFormToDisk();
    }

    private void registerFormEntryReceiver() {
        //BroadcastReceiver for:
        // a) An unresolvable xpath expression encountered in PollSensorAction.onLocationChanged
        // b) Checking if GPS services are not available
        mLocationServiceIssueReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                context.removeStickyBroadcast(intent);
                badLocationXpath = intent.getStringExtra(PollSensorAction.KEY_UNRESOLVED_XPATH);
                locationRecieverErrorAction = intent.getAction();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PollSensorAction.XPATH_ERROR_ACTION);
        filter.addAction(GeoUtils.ACTION_CHECK_GPS_ENABLED);
        registerReceiver(mLocationServiceIssueReceiver, filter);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_FORMPATH, mFormPath);
        outState.putBoolean(KEY_FORM_LOAD_HAS_TRIGGERED, hasFormLoadBeenTriggered);
        outState.putBoolean(KEY_FORM_LOAD_FAILED, hasFormLoadFailed);
        outState.putString(KEY_LOC_ERROR, locationRecieverErrorAction);
        outState.putString(KEY_LOC_ERROR_PATH, badLocationXpath);

        outState.putString(KEY_FORM_CONTENT_URI, formProviderContentURI.toString());
        outState.putString(KEY_INSTANCE_CONTENT_URI, instanceProviderContentURI.toString());
        outState.putString(KEY_INSTANCEDESTINATION, mInstanceDestination);
        outState.putBoolean(KEY_INCOMPLETE_ENABLED, mIncompleteEnabled);
        outState.putBoolean(KEY_HAS_SAVED, hasSaved);
        outState.putString(KEY_RESIZING_ENABLED, ResizingImageView.resizeMethod);
        saveFormEntrySession(outState);
        outState.putBoolean(KEY_RECORD_FORM_ENTRY_SESSION, recordEntrySession);

        if (indexOfWidgetWithVideoPlaying != -1) {
            outState.putInt(KEY_WIDGET_WITH_VIDEO_PLAYING, indexOfWidgetWithVideoPlaying);
            outState.putInt(KEY_POSITION_OF_VIDEO_PLAYING, positionOfVideoProgress);
        }

        if(symetricKey != null) {
            try {
                outState.putString(KEY_AES_STORAGE_KEY, new Base64Wrapper().encodeToString(symetricKey.getEncoded()));
            } catch (ClassNotFoundException e) {
                // we can't really get here anyway, since we couldn't have decoded the string to begin with
                throw new RuntimeException("Base 64 encoding unavailable! Can't pass storage key");
            }
        }
        uiController.saveInstanceState(outState);
    }

    private void saveFormEntrySession(Bundle outState) {
        if (formEntryRestoreSession != null) {
            ByteArrayOutputStream objectSerialization = new ByteArrayOutputStream();
            try {
                formEntryRestoreSession.writeExternal(new DataOutputStream(objectSerialization));
                outState.putByteArray(KEY_FORM_ENTRY_SESSION, objectSerialization.toByteArray());
            } catch (IOException e) {
                outState.putByteArray(KEY_FORM_ENTRY_SESSION, null);
            } finally {
                try {
                    objectSerialization.close();
                } catch (IOException e) {
                    Log.w(TAG, "failed to store form entry session in instance bundle");
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == FORM_PREFERENCES_KEY) {
            uiController.refreshCurrentView(false);
            return;
        }

        if (resultCode == RESULT_CANCELED) {
            if (requestCode == HIERARCHY_ACTIVITY_FIRST_START) {
                // They pressed 'back' on the first hierarchy screen, so we should assume they want
                // to back out of form entry all together
                finishReturnInstance(false);
            } else if (requestCode == INTENT_CALLOUT){
                processIntentResponse(intent, true);
                Toast.makeText(this, Localization.get("intent.callout.cancelled"), Toast.LENGTH_SHORT).show();
            }
            // request was canceled, so do nothing
            return;
        }

        switch (requestCode) {
            case BARCODE_CAPTURE:
                String sb = intent.getStringExtra("SCAN_RESULT");
                uiController.questionsView.setBinaryData(sb, mFormController);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                break;
            case INTENT_CALLOUT:
                if (!processIntentResponse(intent, false)) {
                    Toast.makeText(this, Localization.get("intent.callout.unable.to.process"), Toast.LENGTH_SHORT).show();
                }
                break;
            case IMAGE_CAPTURE:
                ImageCaptureProcessing.processCaptureResponse(this, getInstanceFolder(), true);
                break;
            case SIGNATURE_CAPTURE:
                boolean saved = ImageCaptureProcessing.processCaptureResponse(this, getInstanceFolder(), false);
                if (saved && !uiController.questionsView.isQuestionList()) {
                    // attempt to auto-advance if a signature was captured
                    advance();
                }
                break;
            case IMAGE_CHOOSER:
                ImageCaptureProcessing.processImageChooserResponse(this, getInstanceFolder(), intent);
                break;
            case AUDIO_VIDEO_FETCH:
                processChooserResponse(intent);
                break;
            case LOCATION_CAPTURE:
                String sl = intent.getStringExtra(LOCATION_RESULT);
                uiController.questionsView.setBinaryData(sl, mFormController);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                break;
            case HIERARCHY_ACTIVITY:
            case HIERARCHY_ACTIVITY_FIRST_START:
                if (resultCode == FormHierarchyActivity.RESULT_XPATH_ERROR) {
                    finish();
                } else {
                    // We may have jumped to a new index in hierarchy activity, so refresh
                    uiController.refreshCurrentView(false);
                }
                break;
        }
    }

    private String getInstanceFolder() {
       return mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
    }

    public void saveImageWidgetAnswer(ContentValues values) {
        Uri imageURI =
                getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        Log.i(TAG, "Inserting image returned uri = " + imageURI);

        uiController.questionsView.setBinaryData(imageURI, mFormController);
        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        uiController.refreshView();
    }

    private void processChooserResponse(Intent intent) {
        // For audio/video capture/chooser, we get the URI from the content provider
        // then the widget copies the file and makes a new entry in the content provider.
        Uri media = intent.getData();
        String binaryPath = UriToFilePath.getPathFromUri(CommCareApplication._(), media);
        if (!FormUploadUtil.isSupportedMultimediaFile(binaryPath)) {
            // don't let the user select a file that won't be included in the
            // upload to the server
            uiController.questionsView.clearAnswer();
            Toast.makeText(FormEntryActivity.this,
                    Localization.get("form.attachment.invalid"),
                    Toast.LENGTH_LONG).show();
        } else {
            uiController.questionsView.setBinaryData(media, mFormController);
        }
        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        uiController.refreshView();
    }

    /**
     * Search the the current view's widgets for one that has registered a
     * pending callout with the form controller
     */
    public QuestionWidget getPendingWidget() {
        if (mFormController != null) {
            FormIndex pendingIndex = mFormController.getPendingCalloutFormIndex();
            if (pendingIndex == null) {
                Logger.log(AndroidLogger.SOFT_ASSERT,
                        "getPendingWidget called when pending callout form index was null");
                return null;
            }
            for (QuestionWidget q : uiController.questionsView.getWidgets()) {
                if (q.getFormId().equals(pendingIndex)) {
                    return q;
                }
            }
            Logger.log(AndroidLogger.SOFT_ASSERT,
                    "getPendingWidget couldn't find question widget with a form index that matches the pending callout.");
        }
        return null;
    }

    /**
     * @return Was answer set from intent?
     */
    private boolean processIntentResponse(Intent response, boolean wasIntentCancelled) {
        // keep track of whether we should auto advance
        boolean wasAnswerSet = false;
        boolean isQuick = false;

        IntentWidget pendingIntentWidget = (IntentWidget)getPendingWidget();
        if (pendingIntentWidget != null) {
            // Set our instance destination for binary data if needed
            String destination = mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);

            // get the original intent callout
            IntentCallout ic = pendingIntentWidget.getIntentCallout();

            if (!wasIntentCancelled) {
                isQuick = "quick".equals(ic.getAppearance());
                TreeReference context = null;
                if (mFormController.getPendingCalloutFormIndex() != null) {
                    context = mFormController.getPendingCalloutFormIndex().getReference();
                }
                wasAnswerSet = ic.processResponse(response, context, new File(destination));
            }

            ic.setCancelled(wasIntentCancelled);
        }

        // auto advance if we got a good result and are in quick mode
        if (wasAnswerSet && isQuick) {
            uiController.showNextView();
        } else {
            uiController.refreshView();
        }

        return wasAnswerSet;
    }

    private void updateFormRelevancies() {
        ArrayList<QuestionWidget> oldWidgets = uiController.questionsView.getWidgets();
        // These 2 calls need to be made here, rather than in the for loop below, because at that
        // point the widgets will have already started being updated to the values for the new view
        ArrayList<Vector<SelectChoice>> oldSelectChoices =
                FormRelevancyUpdating.getOldSelectChoicesForEachWidget(oldWidgets);
        ArrayList<String> oldQuestionTexts =
                FormRelevancyUpdating.getOldQuestionTextsForEachWidget(oldWidgets);

        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);

        FormEntryPrompt[] newValidPrompts;
        try {
            newValidPrompts = mFormController.getQuestionPrompts();
        } catch (XPathException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(this, e, EXIT);
            return;
        }
        Set<FormEntryPrompt> promptsLeftInView = new HashSet<>();

        ArrayList<Integer> shouldRemoveFromView = new ArrayList<>();
        // Loop through all of the old widgets to determine which ones should stay in the new view
        for (int i = 0; i < oldWidgets.size(); i++) {

            //Intent widgets need to be fully rebuilt to update their intent callouts
            //depending on model changes.
            if(oldWidgets.get(i) instanceof IntentWidget) {
                shouldRemoveFromView.add(i);
                continue;
            }
            FormEntryPrompt oldPrompt = oldWidgets.get(i).getPrompt();
            String priorQuestionTextForThisWidget = oldQuestionTexts.get(i);
            Vector<SelectChoice> priorSelectChoicesForThisWidget = oldSelectChoices.get(i);

            FormEntryPrompt equivalentNewPrompt =
                    FormRelevancyUpdating.getEquivalentPromptInNewList(newValidPrompts,
                            oldPrompt, priorQuestionTextForThisWidget, priorSelectChoicesForThisWidget);
            if (equivalentNewPrompt != null) {
                promptsLeftInView.add(equivalentNewPrompt);
            } else {
                // If there is no equivalent prompt in the list of new prompts, then this prompt is
                // no longer relevant in the new view, so it should get removed
                shouldRemoveFromView.add(i);
            }
        }
        // Remove "atomically" to not mess up iterations
        uiController.questionsView.removeQuestionsFromIndex(shouldRemoveFromView);

        // Now go through add add any new prompts that we need
        for (int i = 0; i < newValidPrompts.length; ++i) {
        	FormEntryPrompt prompt = newValidPrompts[i];
        	if (!promptsLeftInView.contains(prompt)) {
                // If the old version of this prompt was NOT left in the view, then add it
                uiController.questionsView.addQuestionToIndex(prompt, mFormController.getWidgetFactory(), i);
            }
        }
        updateCompoundIntentButtonVisibility();
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (CommCareApplication._().isConsumerApp()) {
            // Do not show options menu at all if this is a consumer app
            return super.onPrepareOptionsMenu(menu);
        }

        GoogleAnalyticsUtils.reportOptionsMenuEntry(GoogleAnalyticsFields.CATEGORY_FORM_ENTRY);

        menu.removeItem(MENU_LANGUAGES);
        menu.removeItem(MENU_HIERARCHY_VIEW);
        menu.removeItem(MENU_SAVE);
        menu.removeItem(MENU_PREFERENCES);

        if(mIncompleteEnabled) {
            menu.add(0, MENU_SAVE, 0, StringUtils.getStringRobust(this, R.string.save_all_answers)).setIcon(
                    android.R.drawable.ic_menu_save);
        }
        menu.add(0, MENU_HIERARCHY_VIEW, 0, StringUtils.getStringRobust(this, R.string.view_hierarchy)).setIcon(
                R.drawable.ic_menu_goto);

        boolean hasMultipleLanguages =
                (!(mFormController == null || mFormController.getLanguages() == null || mFormController.getLanguages().length == 1));
        menu.add(0, MENU_LANGUAGES, 0, StringUtils.getStringRobust(this, R.string.change_language))
                .setIcon(R.drawable.ic_menu_start_conversation)
                .setEnabled(hasMultipleLanguages);

        menu.add(0, MENU_PREFERENCES, 0, StringUtils.getStringRobust(this, R.string.form_entry_settings)).setIcon(
                android.R.drawable.ic_menu_preferences);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Map<Integer, String> menuIdToAnalyticsEventLabel = createMenuItemToEventMapping();
        GoogleAnalyticsUtils.reportOptionsMenuItemEntry(
                GoogleAnalyticsFields.CATEGORY_FORM_ENTRY,
                menuIdToAnalyticsEventLabel.get(item.getItemId()));
        switch (item.getItemId()) {
            case MENU_LANGUAGES:
                createLanguageDialog();
                return true;
            case MENU_SAVE:
                saveFormToDisk(DO_NOT_EXIT);
                return true;
            case MENU_HIERARCHY_VIEW:
                if (currentPromptIsQuestion()) {
                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                }
                Intent i = new Intent(this, FormHierarchyActivity.class);
                startActivityForResult(i, HIERARCHY_ACTIVITY);
                return true;
            case MENU_PREFERENCES:
                Intent pref = new Intent(this, FormEntryPreferences.class);
                startActivityForResult(pref, FORM_PREFERENCES_KEY);
                return true;
            case android.R.id.home:
                GoogleAnalyticsUtils.reportFormQuitAttempt(GoogleAnalyticsFields.LABEL_NAV_BAR_ARROW);
                triggerUserQuitInput();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private static Map<Integer, String> createMenuItemToEventMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(MENU_LANGUAGES, GoogleAnalyticsFields.LABEL_CHANGE_LANGUAGE);
        menuIdToAnalyticsEvent.put(MENU_SAVE, GoogleAnalyticsFields.LABEL_SAVE_FORM);
        menuIdToAnalyticsEvent.put(MENU_HIERARCHY_VIEW, GoogleAnalyticsFields.LABEL_FORM_HIERARCHY);
        menuIdToAnalyticsEvent.put(MENU_PREFERENCES, GoogleAnalyticsFields.LABEL_CHANGE_SETTINGS);
        return menuIdToAnalyticsEvent;
    }

    /**
     * @return true If the current index of the form controller contains questions
     */
    protected boolean currentPromptIsQuestion() {
        return (mFormController.getEvent() == FormEntryController.EVENT_QUESTION || mFormController
                .getEvent() == FormEntryController.EVENT_GROUP);
    }

    protected boolean saveAnswersForCurrentScreen(boolean evaluateConstraints) {
        return saveAnswersForCurrentScreen(evaluateConstraints, true, false);
    }

    /**
     * Attempt to save the answer(s) in the current screen to into the data model.
     *
     * @param failOnRequired      Whether or not the constraint evaluation
     *                            should return false if the question is only
     *                            required. (this is helpful for incomplete
     *                            saves)
     * @param headless            running in a process that can't display graphics
     * @return false if any error occurs while saving (constraint violated,
     * etc...), true otherwise.
     */
    private boolean saveAnswersForCurrentScreen(boolean evaluateConstraints,
                                                boolean failOnRequired,
                                                boolean headless) {
        // only try to save if the current event is a question or a field-list
        // group
        boolean success = true;
        if (isEventQuestionOrListGroup()) {
            HashMap<FormIndex, IAnswerData> answers =
                    uiController.questionsView.getAnswers();

            // Sort the answers so if there are multiple errors, we can
            // bring focus to the first one
            List<FormIndex> indexKeys = new ArrayList<>();
            indexKeys.addAll(answers.keySet());
            Collections.sort(indexKeys, new Comparator<FormIndex>() {
                @Override
                public int compare(FormIndex arg0, FormIndex arg1) {
                    return arg0.compareTo(arg1);
                }
            });

            for (FormIndex index : indexKeys) {
                // Within a group, you can only save for question events
                if (mFormController.getEvent(index) == FormEntryController.EVENT_QUESTION) {
                    int saveStatus = saveAnswer(answers.get(index),
                            index, evaluateConstraints);
                    if (evaluateConstraints &&
                            ((saveStatus != FormEntryController.ANSWER_OK) &&
                                    (failOnRequired ||
                                            saveStatus != FormEntryController.ANSWER_REQUIRED_BUT_EMPTY))) {
                        if (!headless) {
                            uiController.showConstraintWarning(index, mFormController.getQuestionPrompt(index).getConstraintText(), saveStatus, success);
                        }
                        success = false;
                    }
                } else {
                    Log.w(TAG,
                            "Attempted to save an index referencing something other than a question: "
                                    + index.getReference());
                }
            }
        }
        return success;
    }

    private boolean isEventQuestionOrListGroup() {
        return (mFormController.getEvent() == FormEntryController.EVENT_QUESTION) ||
                (mFormController.getEvent() == FormEntryController.EVENT_GROUP
                        && mFormController.indexIsInFieldList());
    }

    /**
     * Clears the answer on the screen.
     */
    private void clearAnswer(QuestionWidget qw) {
        qw.clearAnswer();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, v.getId(), 0, StringUtils.getStringSpannableRobust(this, R.string.clear_answer));
        menu.setHeaderTitle(StringUtils.getStringSpannableRobust(this, R.string.edit_prompt));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // We don't have the right view here, so we store the View's ID as the
        // item ID and loop through the possible views to find the one the user
        // clicked on.
        for (QuestionWidget qw : uiController.questionsView.getWidgets()) {
            if (item.getItemId() == qw.getId()) {
                createClearDialog(qw);
            }
        }

        return super.onContextItemSelected(item);
    }

    /**
     * If we're loading, then we pass the loading thread to our next instance.
     */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        // if a form is loading, pass the loader task
        if (mFormLoaderTask != null && mFormLoaderTask.getStatus() != AsyncTask.Status.FINISHED)
            return mFormLoaderTask;

        // if a form is writing to disk, pass the save to disk task
        if (mSaveToDiskTask != null && mSaveToDiskTask.getStatus() != AsyncTask.Status.FINISHED)
            return mSaveToDiskTask;

        // mFormEntryController is static so we don't need to pass it.
        if (mFormController != null && currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        return null;
    }


    @SuppressLint("NewApi")
    @Override
    public boolean dispatchTouchEvent(MotionEvent mv) {
        //We need to ignore this even if it's processed by the action
        //bar (if one exists)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar bar = getActionBar();
            if (bar != null) {
                View customView = bar.getCustomView();
                if (customView != null && customView.dispatchTouchEvent(mv)) {
                    return true;
                }
            }
        }

        boolean handled = mGestureDetector.onTouchEvent(mv);
        return handled || super.dispatchTouchEvent(mv);
    }


    /**
     * Identifies whether the questionlist featues an aggregatable intent callout and
     * displays the appropriate button if so.
     */
    protected void updateCompoundIntentButtonVisibility() {
        CompoundIntentList i = uiController.questionsView.getAggregateIntentCallout();
        if(i == null) {
            hideCompoundIntentCalloutButton();
        } else {
            Button compoundDispatchButton =
                    (Button)this.findViewById(R.id.multiple_intent_dispatch_button);
            compoundDispatchButton.setVisibility(View.VISIBLE);
            compoundDispatchButton.setText(i.getTitle() + ": " + i.getNumberOfCallouts());
        }
    }

    private void hideCompoundIntentCalloutButton() {
        this.findViewById(R.id.multiple_intent_dispatch_button).setVisibility(View.GONE);
    }

    protected void fireCompoundIntentDispatch() {
        CompoundIntentList i = uiController.questionsView.getAggregateIntentCallout();
        if(i == null) {
            hideCompoundIntentCalloutButton();
            Log.e(TAG, "Multiple intent dispatch button shouldn't have been shown");
            return;
        }

        // We don't process the result on this yet, but Android won't maintain the backstack
        // state for the current activity unless it thinks we're going to process the callout
        // result.
        this.startActivityForResult(i.getCompoundedIntent(), INTENT_COMPOUND_CALLOUT);
    }



    private void saveFormToDisk(boolean exit) {
        if (formHasLoaded()) {
            boolean isFormComplete = isInstanceComplete();
            saveDataToDisk(exit, isFormComplete, null, false);
        } else if (exit) {
            showSaveErrorAndExit();
        }
    }

    private void saveCompletedFormToDisk(String updatedSaveName) {
        saveDataToDisk(EXIT, true, updatedSaveName, false);
    }

    private void saveIncompleteFormToDisk() {
        saveDataToDisk(EXIT, false, null, true);
    }

    private void showSaveErrorAndExit() {
        Toast.makeText(this, Localization.get("form.entry.save.error"), Toast.LENGTH_SHORT).show();
        hasSaved = false;
        finishReturnInstance();
    }

    /**
     * Saves form data to disk.
     *
     * @param exit            If set, will exit program after save.
     * @param complete        Has the user marked the instances as complete?
     * @param updatedSaveName Set name of the instance's content provider, if
     *                        non-null
     * @param headless        Disables GUI warnings and lets answers that
     *                        violate constraints be saved.
     */
    private void saveDataToDisk(boolean exit, boolean complete, String updatedSaveName, boolean headless) {
        if (!formHasLoaded()) {
            if (exit) {
                showSaveErrorAndExit();
            }
            return;
        }
        // save current answer; if headless, don't evaluate the constraints
        // before doing so.
        boolean wasScreenSaved =
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS, complete, headless);
        if (!wasScreenSaved) {
            return;
        }

        // If a save task is already running, just let it do its thing
        if ((mSaveToDiskTask != null) &&
                (mSaveToDiskTask.getStatus() != AsyncTask.Status.FINISHED)) {
            return;
        }

        mSaveToDiskTask =
                new SaveToDiskTask(getIntent().getData(), exit, complete, updatedSaveName, this, instanceProviderContentURI, symetricKey, headless);
        if (!headless){
            mSaveToDiskTask.connect(this);
        }
        mSaveToDiskTask.setFormSavedListener(this);
        mSaveToDiskTask.executeParallel();
    }

    /**
     * Create a dialog with options to save and exit, save, or quit without saving
     */
    private void createQuitDialog() {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this,
                StringUtils.getStringRobust(this, R.string.quit_form_title));

        View.OnClickListener stayInFormListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_BACK_TO_FORM);
                dismissAlertDialog();
            }
        };
        DialogChoiceItem stayInFormItem = new DialogChoiceItem(
                StringUtils.getStringRobust(this, R.string.do_not_exit),
                R.drawable.ic_blue_forward,
                stayInFormListener);

        View.OnClickListener exitFormListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_EXIT_NO_SAVE);
                discardChangesAndExit();
                dismissAlertDialog();
            }
        };
        DialogChoiceItem quitFormItem = new DialogChoiceItem(
                StringUtils.getStringRobust(this, R.string.do_not_save),
                R.drawable.icon_exit_form,
                exitFormListener);

        DialogChoiceItem[] items;
        if (mIncompleteEnabled) {
            View.OnClickListener saveIncompleteListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_SAVE_AND_EXIT);
                    saveFormToDisk(EXIT);
                    dismissAlertDialog();
                }
            };
            DialogChoiceItem saveIncompleteItem = new DialogChoiceItem(
                    StringUtils.getStringRobust(this, R.string.keep_changes),
                    R.drawable.ic_incomplete_orange,
                    saveIncompleteListener);
            items = new DialogChoiceItem[] {stayInFormItem, quitFormItem, saveIncompleteItem};
        } else {
            items = new DialogChoiceItem[] {stayInFormItem, quitFormItem};
        }
        dialog.setChoiceItems(items);
        showAlertDialog(dialog);
    }

    private void discardChangesAndExit() {
        FormFileSystemHelpers.removeMediaAttachedToUnsavedForm(this, mInstancePath, instanceProviderContentURI);

        finishReturnInstance(false);
    }

    /**
     * Confirm clear answer dialog
     */
    private void createClearDialog(final QuestionWidget qw) {
        String title = StringUtils.getStringRobust(this, R.string.clear_answer_ask);
        String question = qw.getPrompt().getLongText();
        if (question.length() > 50) {
            question = question.substring(0, 50) + "...";
        }
        String msg = StringUtils.getStringSpannableRobust(this, R.string.clearanswer_confirm, question).toString();
        StandardAlertDialog d = new StandardAlertDialog(this, title, msg);
        d.setIcon(android.R.drawable.ic_dialog_info);

        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        clearAnswer(qw);
                        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
                dismissAlertDialog();
            }
        };
        d.setPositiveButton(StringUtils.getStringSpannableRobust(this, R.string.discard_answer), quitListener);
        d.setNegativeButton(StringUtils.getStringSpannableRobust(this, R.string.clear_answer_no), quitListener);
        showAlertDialog(d);
    }

    /**
     * Creates and displays a dialog allowing the user to set the language for the form.
     */
    private void createLanguageDialog() {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this,
                StringUtils.getStringRobust(this, R.string.choose_language));

        final String[] languages = mFormController.getLanguages();
        DialogChoiceItem[] choiceItems = new DialogChoiceItem[languages.length];
        for (int i = 0; i < languages.length; i++) {
            final int index = i;
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Update the language in the content provider when selecting a new
                    // language
                    ContentValues values = new ContentValues();
                    values.put(FormsColumns.LANGUAGE, languages[index]);
                    String selection = FormsColumns.FORM_FILE_PATH + "=?";
                    String selectArgs[] = {
                            mFormPath
                    };
                    int updated =
                            getContentResolver().update(formProviderContentURI, values,
                                    selection, selectArgs);
                    Log.i(TAG, "Updated language to: " + languages[index] + " in "
                            + updated + " rows");

                    mFormController.setLanguage(languages[index]);
                    dismissAlertDialog();
                    if (currentPromptIsQuestion()) {
                        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                    }
                    uiController.refreshView();
                }
            };
            choiceItems[i] = new DialogChoiceItem(languages[i], -1, listener);
        }

        dialog.addButton(StringUtils.getStringSpannableRobust(this, R.string.cancel).toString(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismissAlertDialog();
                    }
                }
        );

        dialog.setChoiceItems(choiceItems);
        showAlertDialog(dialog);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int id) {
        CustomProgressDialog dialog = null;
        switch (id) {
            case FormLoaderTask.FORM_LOADER_TASK_ID:
                dialog = CustomProgressDialog.newInstance(
                        StringUtils.getStringRobust(this, R.string.loading_form),
                        StringUtils.getStringRobust(this, R.string.please_wait),
                        id);
                dialog.addCancelButton();
                break;
            case SaveToDiskTask.SAVING_TASK_ID:
                dialog = CustomProgressDialog.newInstance(
                        StringUtils.getStringRobust(this, R.string.saving_form),
                        StringUtils.getStringRobust(this, R.string.please_wait),
                        id);
                break;
        }
        return dialog;
    }

    @Override
    public void taskCancelled() {
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (uiController.questionsView != null && currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }

        if (mLocationServiceIssueReceiver != null) {
            unregisterReceiver(mLocationServiceIssueReceiver);
        }

        saveInlineVideoState();

        if (isFinishing()) {
            PollSensorController.INSTANCE.stopLocationPolling();
        }
    }

    private void saveInlineVideoState() {
        if (uiController.questionsView != null) {
            for (int i = 0; i < uiController.questionsView.getWidgets().size(); i++) {
                QuestionWidget q = uiController.questionsView.getWidgets().get(i);
                if (q.findViewById(MediaLayout.INLINE_VIDEO_PANE_ID) != null) {
                    VideoView inlineVideo = (VideoView)q.findViewById(MediaLayout.INLINE_VIDEO_PANE_ID);
                    if (inlineVideo.isPlaying()) {
                        indexOfWidgetWithVideoPlaying = i;
                        positionOfVideoProgress = inlineVideo.getCurrentPosition();
                        return;
                    }
                }
            }
        }
    }

    private void restoreInlineVideoState() {
        if (indexOfWidgetWithVideoPlaying != -1) {
            QuestionWidget widgetWithVideoToResume = uiController.questionsView.getWidgets().get(indexOfWidgetWithVideoPlaying);
            VideoView inlineVideo = (VideoView)widgetWithVideoToResume.findViewById(MediaLayout.INLINE_VIDEO_PANE_ID);
            if (inlineVideo != null) {
                inlineVideo.seekTo(positionOfVideoProgress);
                inlineVideo.start();
            } else {
                Logger.log(AndroidLogger.SOFT_ASSERT,
                        "No inline video was found at the question widget index for which a " +
                                "video had been playing before the activity was paused");
            }

            // Reset values now that we have restored
            indexOfWidgetWithVideoPlaying = -1;
            positionOfVideoProgress = -1;
        }
    }

    @Override
    protected void onResumeSessionSafe() {
        if (!hasFormLoadBeenTriggered) {
            loadForm();
        }

        registerFormEntryReceiver();
        restorePriorStates();

        if (mFormController != null) {
            mFormController.setPendingCalloutFormIndex(null);
        }
    }

    private void restorePriorStates() {
        if (uiController.questionsView != null) {
            uiController.questionsView.restoreTimePickerData();
            uiController.restoreFocusToCalloutQuestion();
            restoreInlineVideoState();
        }
    }

    private void loadForm() {
        mFormController = null;
        mInstancePath = null;

        Intent intent = getIntent();
        if (intent != null) {
            loadIntentFormData(intent);

            setTitleToLoading();

            Uri uri = intent.getData();
            final String contentType = getContentResolver().getType(uri);
            Uri formUri;
            if (contentType == null){
                UserfacingErrorHandling.createErrorDialog(this, "form URI resolved to null", EXIT);
                return;
            }

            boolean isInstanceReadOnly = false;
            try {
                switch (contentType) {
                    case InstanceColumns.CONTENT_ITEM_TYPE:
                        Pair<Uri, Boolean> instanceAndStatus = getInstanceUri(uri);
                        formUri = instanceAndStatus.first;
                        isInstanceReadOnly = instanceAndStatus.second;
                        break;
                    case FormsColumns.CONTENT_ITEM_TYPE:
                        formUri = uri;
                        mFormPath = FormFileSystemHelpers.getFormPath(this, uri);
                        break;
                    default:
                        Log.e(TAG, "unrecognized URI");
                        UserfacingErrorHandling.createErrorDialog(this, "unrecognized URI: " + uri, EXIT);
                        return;
                }
            } catch (FormQueryException e) {
                UserfacingErrorHandling.createErrorDialog(this, e.getMessage(), EXIT);
                return;
            }

            if(formUri == null) {
                Log.e(TAG, "unrecognized URI");
                UserfacingErrorHandling.createErrorDialog(this, "couldn't locate FormDB entry for the item at: " + uri, EXIT);
                return;
            }

            mFormLoaderTask = new FormLoaderTask<FormEntryActivity>(symetricKey, isInstanceReadOnly, recordEntrySession, this) {
                @Override
                protected void deliverResult(FormEntryActivity receiver, FECWrapper wrapperResult) {
                    receiver.handleFormLoadCompletion(wrapperResult.getController());
                }

                @Override
                protected void deliverUpdate(FormEntryActivity receiver, String... update) {
                }

                @Override
                protected void deliverError(FormEntryActivity receiver, Exception e) {
                    receiver.setFormLoadFailure();
                    receiver.dismissProgressDialog();

                    if (e != null) {
                        UserfacingErrorHandling.createErrorDialog(receiver, e.getMessage(), EXIT);
                    } else {
                        UserfacingErrorHandling.createErrorDialog(receiver, StringUtils.getStringRobust(receiver, R.string.parse_error), EXIT);
                    }
                }
            };
            mFormLoaderTask.connect(this);
            mFormLoaderTask.executeParallel(formUri);
            hasFormLoadBeenTriggered = true;
        }
    }

    private void handleFormLoadCompletion(FormController fc) {
        if (GeoUtils.ACTION_CHECK_GPS_ENABLED.equals(locationRecieverErrorAction)) {
            handleNoGpsBroadcast();
        } else if (PollSensorAction.XPATH_ERROR_ACTION.equals(locationRecieverErrorAction)) {
            handleXpathErrorBroadcast();
        }

        mFormController = fc;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            // Newer menus may have already built the menu, before all data was ready
            invalidateOptionsMenu();
        }

        registerSessionFormSaveCallback();

        // Set saved answer path
        if (mInstancePath == null) {
            // Create new answer folder.
            String time =
                    new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                            .format(Calendar.getInstance().getTime());
            String file =
                    mFormPath.substring(mFormPath.lastIndexOf('/') + 1, mFormPath.lastIndexOf('.'));
            String path = mInstanceDestination + file + "_" + time;
            if (FileUtil.createFolder(path)) {
                mInstancePath = path + "/" + file + "_" + time + ".xml";
            }
        } else {
            // we've just loaded a saved form, so start in the hierarchy view
            Intent i = new Intent(this, FormHierarchyActivity.class);
            startActivityForResult(i, HIERARCHY_ACTIVITY_FIRST_START);
            return; // so we don't show the intro screen before jumping to the hierarchy
        }

        reportFormEntry();

        try {
            FormEntrySessionReplayer.tryReplayingFormEntry(mFormController.getFormEntryController(),
                    formEntryRestoreSession);
            formEntryRestoreSession = null;
        } catch (FormEntrySessionReplayer.ReplayError e) {
            UserfacingErrorHandling.createErrorDialog(this, e.getMessage(), EXIT);
        }

        uiController.refreshView();
        FormNavigationUI.updateNavigationCues(this, mFormController, uiController.questionsView);
    }

    private void handleNoGpsBroadcast() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Set<String> providers = GeoUtils.evaluateProviders(manager);
        if (providers.isEmpty()) {
            DialogInterface.OnClickListener onChangeListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    if (i == DialogInterface.BUTTON_POSITIVE) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                    dismissAlertDialog();
                }
            };
            GeoUtils.showNoGpsDialog(this, onChangeListener);
        }
    }

    private void handleXpathErrorBroadcast() {
        UserfacingErrorHandling.createErrorDialog(FormEntryActivity.this,
                "There is a bug in one of your form's XPath Expressions \n" + badLocationXpath, EXIT);
    }

    /**
     * Call when the user provides input that they want to quit the form
     */
    protected void triggerUserQuitInput() {
        if(!formHasLoaded()) {
            finish();
        } else if (mFormController.isFormReadOnly()) {
            // If we're just reviewing a read only form, don't worry about saving
            // or what not, just quit
            // It's possible we just want to "finish" here, but
            // I don't really wanna break any c compatibility
            finishReturnInstance(false);
        } else {
            createQuitDialog();
            return;
        }
        GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_NO_DIALOG);
    }

    /**
     * Get the default title for ODK's "Form title" field
     */
    private String getDefaultFormTitle() {
        String saveName = mFormController.getFormTitle();
        if (InstanceColumns.CONTENT_ITEM_TYPE.equals(getContentResolver().getType(getIntent().getData()))) {
            Uri instanceUri = getIntent().getData();

            Cursor instance = null;
            try {
                instance = getContentResolver().query(instanceUri, null, null, null, null);
                if (instance != null && instance.getCount() == 1) {
                    instance.moveToFirst();
                    saveName =
                        instance.getString(instance
                                .getColumnIndex(InstanceColumns.DISPLAY_NAME));
                }
            } finally {
                if (instance != null) {
                    instance.close();
                }
            }
        }
        return saveName;
    }

    /**
     * Call when the user is ready to save and return the current form as complete
     */
    protected void triggerUserFormComplete() {
        if (mFormController.isFormReadOnly()) {
            finishReturnInstance(false);
        } else {
            saveCompletedFormToDisk(getDefaultFormTitle());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                GoogleAnalyticsUtils.reportFormQuitAttempt(GoogleAnalyticsFields.LABEL_DEVICE_BUTTON);
            	triggerUserQuitInput();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isAltPressed() && !uiController.shouldIgnoreSwipeAction()) {
                    uiController.setIsAnimatingSwipe();
                    uiController.showNextView();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed() && !uiController.shouldIgnoreSwipeAction()) {
                    uiController.setIsAnimatingSwipe();
                    uiController.showPreviousView(true);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (mFormLoaderTask != null) {
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            // but only if it's done, otherwise the thread never returns
            if (mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                mFormLoaderTask.cancel(true);
                mFormLoaderTask.destroy();
            }
        }
        if (mSaveToDiskTask != null) {
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            if (mSaveToDiskTask.getStatus() == AsyncTask.Status.FINISHED) {
                mSaveToDiskTask.cancel(false);
            }
        }

        super.onDestroy();
    }

    private void registerSessionFormSaveCallback() {
        if (mFormController != null && !mFormController.isFormReadOnly()) {
            try {
                // CommCareSessionService will call this.formSaveCallback when
                // the key session is closing down and we need to save any
                // intermediate results before they become un-saveable.
                CommCareApplication._().getSession().registerFormSaveCallback(this);
            } catch (SessionUnavailableException e) {
                Log.w(TAG,
                        "Couldn't register form save callback because session doesn't exist");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Display save status notification and exit or continue on in the form.
     * If form entry is being saved because key session is expiring then
     * continue closing the session/logging out.
     */
    @Override
    public void savingComplete(SaveToDiskTask.SaveStatus saveStatus, String errorMessage) {
        // Did we just save a form because the key session
        // (CommCareSessionService) is ending?
        if (savingFormOnKeySessionExpiration) {
            savingFormOnKeySessionExpiration = false;

            // Notify the key session that the form state has been saved (or at
            // least attempted to be saved) so CommCareSessionService can
            // continue closing down key pool and user database.
            CommCareApplication._().expireUserSession();
        } else if (saveStatus != null) {
            String toastMessage = "";
            switch (saveStatus) {
                case SAVED_COMPLETE:
                    toastMessage = Localization.get("form.entry.complete.save.success");
                    hasSaved = true;
                    break;
                case SAVED_INCOMPLETE:
                    toastMessage = Localization.get("form.entry.incomplete.save.success");
                    hasSaved = true;
                    break;
                case SAVED_AND_EXIT:
                    toastMessage = Localization.get("form.entry.complete.save.success");
                    hasSaved = true;
                    finishReturnInstance();
                    break;
                case INVALID_ANSWER:
                    // an answer constraint was violated, so try to save the
                    // current question to trigger the constraint violation message
                    uiController.refreshView();
                    saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS);
                    return;
                case SAVE_ERROR:
                    if (!CommCareApplication._().isConsumerApp()) {
                        UserfacingErrorHandling.createErrorDialog(this, errorMessage,
                                Localization.get("notification.formentry.save_error.title"), EXIT);
                    }
                    return;
            }
            if (!"".equals(toastMessage) && !CommCareApplication._().isConsumerApp()) {
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
            }
            uiController.refreshView();
        }
    }

    /**
     * Attempts to save an answer to the specified index.
     *
     * @param evaluateConstraints Should form constraints be checked when saving answer?
     * @return status as determined in FormEntryController
     */
    private int saveAnswer(IAnswerData answer, FormIndex index, boolean evaluateConstraints) {
        try {
            if (evaluateConstraints) {
                return mFormController.answerQuestion(index, answer);
            } else {
                mFormController.saveAnswer(index, answer);
                return FormEntryController.ANSWER_OK;
            }
        } catch (XPathException e) {
            //this is where runtime exceptions get triggered after the form has loaded
            UserfacingErrorHandling.logErrorAndShowDialog(this, e, EXIT);
            //We're exiting anyway
            return FormEntryController.ANSWER_OK;
        }
    }

    /**
     * Checks the database to determine if the current instance being edited has already been
     * 'marked completed'. A form can be 'unmarked' complete and then resaved.
     *
     * @return true if form has been marked completed, false otherwise.
     */
    private boolean isInstanceComplete() {
        // default to false if we're mid form
        boolean complete = false;

        // Then see if we've already marked this form as complete before
        String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
        String[] selectionArgs = {
            mInstancePath
        };

        Cursor c = null;
        try {
            c = getContentResolver().query(instanceProviderContentURI, null, selection, selectionArgs, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                String status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));
                if (InstanceProviderAPI.STATUS_COMPLETE.compareTo(status) == 0) {
                    complete = true;
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return complete;
    }

    private void finishReturnInstance() {
        finishReturnInstance(true);
    }

    /**
     * Returns the instance that was just filled out to the calling activity,
     * if requested.
     *
     * @param reportSaved was a form saved? Delegates the result code of the
     * activity
     */
    private void finishReturnInstance(boolean reportSaved) {
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            // caller is waiting on a picked form
            String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
            String[] selectionArgs = {
                mInstancePath
            };

            Cursor c = null;
            try {
                c = getContentResolver().query(instanceProviderContentURI, null, selection, selectionArgs, null);
                if (c != null && c.getCount() > 0) {
                    // should only be one...
                    c.moveToFirst();
                    String id = c.getString(c.getColumnIndex(InstanceColumns._ID));
                    Uri instance = Uri.withAppendedPath(instanceProviderContentURI, id);

                    Intent formReturnIntent = new Intent();
                    formReturnIntent.putExtra(IS_ARCHIVED_FORM, mFormController.isFormReadOnly());

                    if (reportSaved || hasSaved) {
                        setResult(RESULT_OK, formReturnIntent.setData(instance));
                    } else {
                        setResult(RESULT_CANCELED, formReturnIntent.setData(instance));
                    }
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        try {
            CommCareApplication._().getSession().unregisterFormSaveCallback();
        } catch (SessionUnavailableException sue) {
            // looks like the session expired, swallow exception because we
            // might be auto-saving a form due to user session expiring
        }

        dismissProgressDialog();
        reportFormExit();
        finish();
    }

    @Override
    protected boolean onBackwardSwipe() {
        GoogleAnalyticsUtils.reportFormNavBackward(GoogleAnalyticsFields.LABEL_SWIPE);
        uiController.showPreviousView(true);
        return true;
    }

    @Override
    protected boolean onForwardSwipe() {
        if (canNavigateForward()) {
            GoogleAnalyticsUtils.reportFormNavForward(
                    GoogleAnalyticsFields.LABEL_SWIPE,
                    GoogleAnalyticsFields.VALUE_FORM_NOT_DONE);
            uiController.next();
            return true;
        } else {
            GoogleAnalyticsUtils.reportFormNavForward(
                    GoogleAnalyticsFields.LABEL_SWIPE,
                    GoogleAnalyticsFields.VALUE_FORM_DONE);
            FormNavigationUI.animateFinishArrow(this);
            return true;
        }
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // The onFling() captures the 'up' event so our view thinks it gets long pressed.
        // We don't wnat that, so cancel it.
        if (uiController.questionsView != null) {
            uiController.questionsView.cancelLongPress();
        }
        return false;
    }

    @Override
    public void advance() {
        if (canNavigateForward()) {
            uiController.next();
        }
    }

    @Override
    public void widgetEntryChanged(QuestionWidget changedWidget) {
        try {
            uiController.recordLastChangedWidgetIndex(changedWidget);
            updateFormRelevancies();
        } catch (XPathTypeMismatchException | XPathArityException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(this, e, EXIT);
            return;
        }

        FormNavigationUI.updateNavigationCues(this, mFormController, uiController.questionsView);
    }

    private boolean canNavigateForward() {
        ImageButton nextButton = (ImageButton)this.findViewById(R.id.nav_btn_next);
        return NAV_STATE_NEXT.equals(nextButton.getTag());
    }

    /**
     * Has form loading (via FormLoaderTask) completed?
     */
    private boolean formHasLoaded() {
        return mFormController != null;
    }

    private void loadStateFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_FORMPATH)) {
                mFormPath = savedInstanceState.getString(KEY_FORMPATH);
            }
            if (savedInstanceState.containsKey(KEY_FORM_LOAD_HAS_TRIGGERED)) {
                hasFormLoadBeenTriggered = savedInstanceState.getBoolean(KEY_FORM_LOAD_HAS_TRIGGERED, false);
            }
            if (savedInstanceState.containsKey(KEY_FORM_LOAD_FAILED)) {
                hasFormLoadFailed = savedInstanceState.getBoolean(KEY_FORM_LOAD_FAILED, false);
            }

            locationRecieverErrorAction = savedInstanceState.getString(KEY_LOC_ERROR);
            badLocationXpath = savedInstanceState.getString(KEY_LOC_ERROR_PATH);

            if (savedInstanceState.containsKey(KEY_FORM_CONTENT_URI)) {
                formProviderContentURI = Uri.parse(savedInstanceState.getString(KEY_FORM_CONTENT_URI));
            }
            if (savedInstanceState.containsKey(KEY_INSTANCE_CONTENT_URI)) {
                instanceProviderContentURI = Uri.parse(savedInstanceState.getString(KEY_INSTANCE_CONTENT_URI));
            }
            if (savedInstanceState.containsKey(KEY_INSTANCEDESTINATION)) {
                mInstanceDestination = savedInstanceState.getString(KEY_INSTANCEDESTINATION);
            }
            if(savedInstanceState.containsKey(KEY_INCOMPLETE_ENABLED)) {
                mIncompleteEnabled = savedInstanceState.getBoolean(KEY_INCOMPLETE_ENABLED);
            }
            if(savedInstanceState.containsKey(KEY_RESIZING_ENABLED)) {
                ResizingImageView.resizeMethod = savedInstanceState.getString(KEY_RESIZING_ENABLED);
            }
            if (savedInstanceState.containsKey(KEY_AES_STORAGE_KEY)) {
                String base64Key = savedInstanceState.getString(KEY_AES_STORAGE_KEY);
                try {
                    byte[] storageKey = new Base64Wrapper().decode(base64Key);
                    symetricKey = new SecretKeySpec(storageKey, "AES");
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Base64 encoding not available on this platform");
                }
            }
            if(savedInstanceState.containsKey(KEY_HEADER_STRING)) {
                mHeaderString = savedInstanceState.getString(KEY_HEADER_STRING);
            }
            if(savedInstanceState.containsKey(KEY_HAS_SAVED)) {
                hasSaved = savedInstanceState.getBoolean(KEY_HAS_SAVED);
            }

            restoreFormEntrySession(savedInstanceState);

            recordEntrySession = savedInstanceState.getBoolean(KEY_RECORD_FORM_ENTRY_SESSION, false);
            if (savedInstanceState.containsKey(KEY_WIDGET_WITH_VIDEO_PLAYING)) {
                indexOfWidgetWithVideoPlaying = savedInstanceState.getInt(KEY_WIDGET_WITH_VIDEO_PLAYING);
                positionOfVideoProgress = savedInstanceState.getInt(KEY_POSITION_OF_VIDEO_PLAYING);
            }
            uiController.restoreSavedState(savedInstanceState);
        }
    }

    private void restoreFormEntrySession(Bundle savedInstanceState) {
        byte[] serializedObject = savedInstanceState.getByteArray(KEY_FORM_ENTRY_SESSION);
        if (serializedObject != null) {
            formEntryRestoreSession = new FormEntrySession();
            DataInputStream objectInputStream = new DataInputStream(new ByteArrayInputStream(serializedObject));
            try {
                formEntryRestoreSession.readExternal(objectInputStream, CommCareApplication._().getPrototypeFactory(this));
            } catch (IOException | DeserializationException e) {
                Log.e(TAG, "failed to deserialize form entry session during saved instance restore");
            } finally {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "failed to close deserialization stream for form entry session during saved instance restore");
                }
            }
        }
    }

    private Pair<Uri, Boolean> getInstanceUri(Uri uri) throws FormQueryException {
        Cursor instanceCursor = null;
        Cursor formCursor = null;
        Boolean isInstanceReadOnly = false;
        Uri formUri = null;
        try {
            instanceCursor = getContentResolver().query(uri, null, null, null, null);
            if (instanceCursor == null) {
                throw new FormQueryException("Bad URI: resolved to null");
            } else if (instanceCursor.getCount() != 1) {
                throw new FormQueryException("Bad URI: " + uri);
            } else {
                instanceCursor.moveToFirst();
                mInstancePath =
                        instanceCursor.getString(instanceCursor
                                .getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));

                final String jrFormId =
                        instanceCursor.getString(instanceCursor
                                .getColumnIndex(InstanceColumns.JR_FORM_ID));


                //If this form is both already completed
                if (InstanceProviderAPI.STATUS_COMPLETE.equals(instanceCursor.getString(instanceCursor.getColumnIndex(InstanceColumns.STATUS)))) {
                    if (!Boolean.parseBoolean(instanceCursor.getString(instanceCursor.getColumnIndex(InstanceColumns.CAN_EDIT_WHEN_COMPLETE)))) {
                        isInstanceReadOnly = true;
                    }
                }
                final String[] selectionArgs = {
                        jrFormId
                };
                final String selection = FormsColumns.JR_FORM_ID + " like ?";

                formCursor = getContentResolver().query(formProviderContentURI, null, selection, selectionArgs, null);
                if (formCursor == null || formCursor.getCount() < 1) {
                    throw new FormQueryException("Parent form does not exist");
                } else if (formCursor.getCount() == 1) {
                    formCursor.moveToFirst();
                    mFormPath =
                            formCursor.getString(formCursor
                                    .getColumnIndex(FormsColumns.FORM_FILE_PATH));
                    formUri = ContentUris.withAppendedId(formProviderContentURI, formCursor.getLong(formCursor.getColumnIndex(FormsColumns._ID)));
                } else if (formCursor.getCount() > 1) {
                    throw new FormQueryException("More than one possible parent form");
                }
            }
        } finally {
            if (instanceCursor != null) {
                instanceCursor.close();
            }
            if (formCursor != null) {
                formCursor.close();
            }
        }
        return new Pair<>(formUri, isInstanceReadOnly);
    }

    private void loadIntentFormData(Intent intent) {
        if(intent.hasExtra(KEY_FORM_CONTENT_URI)) {
            this.formProviderContentURI = Uri.parse(intent.getStringExtra(KEY_FORM_CONTENT_URI));
        }
        if(intent.hasExtra(KEY_INSTANCE_CONTENT_URI)) {
            this.instanceProviderContentURI = Uri.parse(intent.getStringExtra(KEY_INSTANCE_CONTENT_URI));
        }
        if(intent.hasExtra(KEY_INSTANCEDESTINATION)) {
            this.mInstanceDestination = intent.getStringExtra(KEY_INSTANCEDESTINATION);
        } else {
            mInstanceDestination = ODKStorage.INSTANCES_PATH;
        }
        if(intent.hasExtra(KEY_AES_STORAGE_KEY)) {
            String base64Key = intent.getStringExtra(KEY_AES_STORAGE_KEY);
            try {
                byte[] storageKey = new Base64Wrapper().decode(base64Key);
                symetricKey = new SecretKeySpec(storageKey, "AES");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Base64 encoding not available on this platform");
            }
        }
        if(intent.hasExtra(KEY_HEADER_STRING)) {
            FormEntryActivity.mHeaderString = intent.getStringExtra(KEY_HEADER_STRING);
        }

        if(intent.hasExtra(KEY_INCOMPLETE_ENABLED)) {
            this.mIncompleteEnabled = intent.getBooleanExtra(KEY_INCOMPLETE_ENABLED, true);
        }

        if(intent.hasExtra(KEY_RESIZING_ENABLED)) {
            ResizingImageView.resizeMethod = intent.getStringExtra(KEY_RESIZING_ENABLED);
        }
        if (intent.hasExtra(KEY_FORM_ENTRY_SESSION)) {
            formEntryRestoreSession =
                    FormEntrySession.fromString(intent.getStringExtra(KEY_FORM_ENTRY_SESSION));
        }
        recordEntrySession = intent.getBooleanExtra(KEY_RECORD_FORM_ENTRY_SESSION, false);
    }

    private void setTitleToLoading() {
        if(mHeaderString != null) {
            setTitle(mHeaderString);
        } else {
            setTitle(StringUtils.getStringRobust(this, R.string.application_name) + " > " + StringUtils.getStringRobust(this, R.string.loading_form));
        }
    }

    protected String getHeaderString() {
        if(mHeaderString != null) {
            //Localization?
            return mHeaderString;
        } else {
            return StringUtils.getStringRobust(this, R.string.application_name) + " > " + FormEntryActivity.mFormController.getFormTitle();
        }
    }


    public static class FormQueryException extends Exception {
        public FormQueryException(String msg) {
            super(msg);
        }
    }

    private void setFormLoadFailure() {
        hasFormLoadFailed = true;
    }

    @Override
    protected void onMajorLayoutChange(Rect newRootViewDimensions) {
        uiController.recalcShouldHideGroupLabel(newRootViewDimensions);
    }


    private void reportFormEntry() {
        TimedStatsTracker.registerEnterForm(getCurrentFormID());
    }

    private void reportFormExit() {
        TimedStatsTracker.registerExitForm(getCurrentFormID());
    }

    private int getCurrentFormID() {
        return mFormController.getFormID();
    }

    /**
     * For Testing purposes only
     */
    public QuestionsView getODKView() {
        if (BuildConfig.DEBUG) {
            return uiController.questionsView;
        } else {
            throw new RuntimeException("On principal of design, only meant for testing purposes");
        }
    }

    public static String getFormEntrySessionString() {
        if (mFormController == null) {
            return "";
        } else {
            return mFormController.getFormEntrySessionString();
        }
    }

    @Override
    public void initUIController() {
        uiController = new FormEntryActivityUIController(this);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return uiController;
    }
}
