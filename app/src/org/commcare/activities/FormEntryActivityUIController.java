package org.commcare.activities;

import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.activities.components.FormLayoutHelpers;
import org.commcare.activities.components.FormNavigationController;
import org.commcare.activities.components.FormNavigationUI;
import org.commcare.activities.components.FormRelevancyUpdating;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.preferences.FormEntryPreferences;
import org.commcare.preferences.LocalePreferences;
import org.commcare.utils.BlockingActionsManager;
import org.commcare.utils.CompoundIntentList;
import org.commcare.utils.StringUtils;
import org.commcare.views.QuestionsView;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.HorizontalPaneledChoiceDialog;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.media.AudioController;
import org.commcare.views.widgets.ImageWidget;
import org.commcare.views.widgets.IntentWidget;
import org.commcare.views.widgets.QuestionWidget;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.XPathUnhandledException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

public class FormEntryActivityUIController implements CommCareActivityUIController,
        Animation.AnimationListener {
    private static final String TAG = FormEntryActivityUIController.class.getSimpleName();
    private final FormEntryActivity activity;
    private ViewGroup mViewPane;

    private boolean shouldHideGroupLabel = false;

    private Animation mInAnimation;
    private Animation mOutAnimation;

    // used to limit forward/backward swipes to one per question
    private boolean isAnimatingSwipe;
    private boolean isDialogShowing;
    protected QuestionsView questionsView;
    private boolean hasGroupLabel = false;
    private int indexOfLastChangedWidget = -1;
    private BlockingActionsManager blockingActionsManager;

    private boolean formRelevanciesUpdateInProgress = false;

    private static final String KEY_LAST_CHANGED_WIDGET = "index-of-last-changed-widget";

    enum AnimationType {
        LEFT, RIGHT, FADE
    }

    public FormEntryActivityUIController(FormEntryActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.screen_form_entry);
        blockingActionsManager = new BlockingActionsManager(this.activity);

        ImageButton nextButton = activity.findViewById(R.id.nav_btn_next);
        ImageButton prevButton = activity.findViewById(R.id.nav_btn_prev);

        Button multiIntentDispatchButton = activity.findViewById(R.id.multiple_intent_dispatch_button);

        View finishButton = activity.findViewById(R.id.nav_btn_finish);

        TextView finishText = finishButton.findViewById(R.id.nav_btn_finish_text);
        finishText.setText(Localization.get("form.entry.finish.button").toUpperCase());

        nextButton.setOnClickListener(v -> {
            FirebaseAnalyticsUtil.reportFormNav(
                    AnalyticsParamValue.DIRECTION_FORWARD,
                    AnalyticsParamValue.NAV_BUTTON_PRESS);
            showNextView();
        });

        prevButton.setOnClickListener(v -> {
            if (!FormEntryConstants.NAV_STATE_QUIT.equals(v.getTag())) {
                FirebaseAnalyticsUtil.reportFormNav(
                        AnalyticsParamValue.DIRECTION_BACKWARD,
                        AnalyticsParamValue.NAV_BUTTON_PRESS);
                showPreviousView(true);
            } else {
                FirebaseAnalyticsUtil.reportFormQuitAttempt(AnalyticsParamValue.NAV_BUTTON_PRESS);
                activity.triggerUserQuitInput();
            }
        });

        finishButton.setOnClickListener(v -> {
            FirebaseAnalyticsUtil.reportFormNav(
                    AnalyticsParamValue.DIRECTION_FORWARD,
                    AnalyticsParamValue.NAV_BUTTON_PRESS);
            activity.triggerUserFormComplete();
        });

        multiIntentDispatchButton.setOnClickListener(v -> activity.fireCompoundIntentDispatch());


        mViewPane = activity.findViewById(R.id.form_entry_pane);

        activity.requestMajorLayoutUpdates();

        if (questionsView != null) {
            questionsView.teardownView();
        }

        // re-set defaults in case the app got in a bad state.
        isAnimatingSwipe = false;
        isDialogShowing = false;
        questionsView = null;
        mInAnimation = null;
        mOutAnimation = null;
    }

    @Override
    public void refreshView() {
        refreshCurrentView();
    }

    /**
     * Refreshes the current view. the controller and the displayed view can get out of sync due to
     * dialogs and restarts caused by screen orientation changes, so they're resynchronized here.
     */
    private void refreshCurrentView() {
        refreshCurrentView(true);
    }

    /**
     * Refreshes the current view. the controller and the displayed view can get out of sync due to
     * dialogs and restarts caused by screen orientation changes, so they're resynchronized here.
     */
    protected void refreshCurrentView(boolean animateLastView) {
        if (FormEntryActivity.mFormController == null) {
            throw new RuntimeException("Form state is lost! Cannot refresh current view. This shouldn't happen, please submit a bug report.");
        }
        int event = FormEntryActivity.mFormController.getEvent();

        // When we refresh, repeat dialog state isn't maintained, so step back to the previous
        // question.
        // Also, if we're within a group labeled 'field list', step back to the beginning of that
        // group.
        // That is, skip backwards over repeat prompts, groups that are not field-lists,
        // repeat events, and indexes in field-lists that is not the containing group.
        while (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT
                || (event == FormEntryController.EVENT_GROUP && !FormEntryActivity.mFormController.indexIsInFieldList())
                || event == FormEntryController.EVENT_REPEAT
                || (FormEntryActivity.mFormController.indexIsInFieldList() && !(event == FormEntryController.EVENT_GROUP))) {
            event = FormEntryActivity.mFormController.stepToPreviousEvent();
        }

        //If we're at the beginning of form event, but don't show the screen for that, we need
        //to get the next valid screen
        if (event == FormEntryController.EVENT_BEGINNING_OF_FORM) {
            showNextView(true);
        } else if (event == FormEntryController.EVENT_END_OF_FORM) {
            showPreviousView(false);
        } else {
            QuestionsView current = createView();
            showView(current, AnimationType.FADE, animateLastView);
        }
    }

    /**
     * Displays the View specified by the parameter 'next', animating both the current view and next
     * appropriately given the AnimationType. Also updates the progress bar.
     */
    private void showView(QuestionsView next, AnimationType from) {
        showView(next, from, true);
    }

    private void showView(QuestionsView next, AnimationType from, boolean animateLastView) {
        switch (from) {
            case RIGHT:
                mInAnimation = AnimationUtils.loadAnimation(activity, R.anim.push_left_in);
                mOutAnimation = AnimationUtils.loadAnimation(activity, R.anim.push_left_out);
                break;
            case LEFT:
                mInAnimation = AnimationUtils.loadAnimation(activity, R.anim.push_right_in);
                mOutAnimation = AnimationUtils.loadAnimation(activity, R.anim.push_right_out);
                break;
            case FADE:
                mInAnimation = AnimationUtils.loadAnimation(activity, R.anim.fade_in);
                mOutAnimation = AnimationUtils.loadAnimation(activity, R.anim.fade_out);
                break;
        }

        if (questionsView != null) {
            if (animateLastView) {
                questionsView.startAnimation(mOutAnimation);
            }
            mViewPane.removeView(questionsView);
            questionsView.teardownView();
        }

        mInAnimation.setAnimationListener(this);

        RelativeLayout.LayoutParams lp =
                new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

        questionsView = next;
        mViewPane.addView(questionsView, lp);

        questionsView.startAnimation(mInAnimation);
        questionsView.setFocus(activity, indexOfLastChangedWidget);

        setupGroupLabel();
        checkForOrientationRequirements();
    }

    private void checkForOrientationRequirements() {
        if (currentScreenShouldForcePortrait()) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private boolean currentScreenShouldForcePortrait() {
        for (QuestionWidget w : this.questionsView.getWidgets()) {
            if (w.forcesPortrait()) {
                return true;
            }
        }
        return false;
    }

    private void setupGroupLabel() {
        hasGroupLabel = false;
        FormLayoutHelpers.updateGroupViewVisibility(activity, false, shouldHideGroupLabel);
        SpannableStringBuilder groupLabelText = questionsView.getGroupLabel();

        if (groupLabelText != null && !groupLabelText.toString().trim().equals("")) {
            TextView groupLabel = activity.findViewById(R.id.form_entry_group_label);
            groupLabel.setTextSize(FormEntryPreferences.getQuestionFontSize());
            groupLabel.setText(groupLabelText);
            hasGroupLabel = true;
            FormLayoutHelpers.updateGroupViewVisibility(activity, true, shouldHideGroupLabel);
        }
        updateCompoundIntentButtonVisibility();
    }

    /**
     * Determines what should be displayed between a question, or the start screen and displays the
     * appropriate view. Also saves answers to the data model without checking constraints.
     */
    protected void showPreviousView(boolean showSwipeAnimation) {
        if (shouldIgnoreNavigationAction()) {
            return;
        }

        // The answer is saved on a back swipe, but question constraints are ignored.
        if (activity.currentPromptIsQuestion()) {
            activity.saveAnswersForCurrentScreen(false);
        }

        // Any info stored about the last changed widget is useless when we move to a new view
        resetLastChangedWidget();

        FormIndex startIndex = FormEntryActivity.mFormController.getFormIndex();
        FormIndex lastValidIndex = startIndex;

        if (FormEntryActivity.mFormController.getEvent() != FormEntryController.EVENT_BEGINNING_OF_FORM) {
            int event = FormEntryActivity.mFormController.stepToPreviousEvent();

            //Step backwards until we either find a question, the beginning of the form,
            //or a field list with valid questions inside
            while (event != FormEntryController.EVENT_BEGINNING_OF_FORM
                    && event != FormEntryController.EVENT_QUESTION
                    && !(event == FormEntryController.EVENT_GROUP
                    && FormEntryActivity.mFormController.indexIsInFieldList() && FormEntryActivity.mFormController
                    .getQuestionPrompts().length != 0)) {
                event = FormEntryActivity.mFormController.stepToPreviousEvent();
                lastValidIndex = FormEntryActivity.mFormController.getFormIndex();
            }

            if (event == FormEntryController.EVENT_BEGINNING_OF_FORM) {
                // we can't go all the way back to the beginning, so we've
                // gotta hit the last index that was valid
                FormEntryActivity.mFormController.jumpToIndex(lastValidIndex);

                //Did we jump at all? (not sure how we could have, but there might be a mismatch)
                if (lastValidIndex.equals(startIndex)) {
                    //If not, don't even bother changing the view. 
                    //NOTE: This needs to be the same as the
                    //exit condition below, in case either changes
                    activity.triggerUserQuitInput();
                } else if (lastValidIndex.isBeginningOfFormIndex()) {
                    //We might have walked all the way back still, which isn't great,
                    //so keep moving forward again until we find it
                    //there must be a repeat between where we started and the beginning of hte form, walk back up to it
                    showNextView(true);
                }
            } else {
                AudioController.INSTANCE.releaseCurrentMediaEntity();
                QuestionsView next = createView();
                if (showSwipeAnimation) {
                    showView(next, LocalePreferences.isLocaleRTL() ? AnimationType.RIGHT : AnimationType.LEFT);
                } else {
                    showView(next, AnimationType.FADE, false);
                }
            }
        } else {
            activity.triggerUserQuitInput();
        }
    }

    private QuestionsView createView() {
        activity.setTitle(activity.getHeaderString());
        QuestionsView odkv;
        // should only be a group here if the event_group is a field-list
        try {
            odkv =
                    new QuestionsView(activity, FormEntryActivity.mFormController.getQuestionPrompts(),
                            FormEntryActivity.mFormController.getGroupsForCurrentIndex(),
                            FormEntryActivity.mFormController.getWidgetFactory(), activity,
                            blockingActionsManager);
            Log.i(TAG, "created view for group");
        } catch (RuntimeException e) {
            Logger.exception("Error instantiating QuestionsView", e);
            UserfacingErrorHandling.createErrorDialog(activity, e.getMessage(), FormEntryConstants.EXIT);
            // this is badness to avoid a crash.
            // really a next view should increment the formcontroller, create the view
            // if the view is null, then keep the current view and pop an error.
            return new QuestionsView(activity, blockingActionsManager);
        }

        // Makes a "clear answer" menu pop up on long-click of
        // select-one/select-multiple questions
        for (QuestionWidget qw : odkv.getWidgets()) {
            if (!qw.getPrompt().isReadOnly() &&
                    !FormEntryActivity.mFormController.isFormReadOnly() &&
                    (qw.getPrompt().getControlType() == Constants.CONTROL_SELECT_ONE ||
                            qw.getPrompt().getControlType() == Constants.CONTROL_SELECT_MULTI)) {
                activity.registerForContextMenu(qw);
            }
        }

        FormNavigationUI.updateNavigationCues(activity, FormEntryActivity.mFormController, odkv);

        return odkv;
    }

    /**
     * Determines what should be displayed on the screen. Possible options are: a question, an ask
     * repeat dialog, or the submit screen. Also saves answers to the data model after checking
     * constraints.
     */
    protected void showNextView() {
        showNextView(false);
    }

    private void showNextView(boolean resuming) {
        AudioController.INSTANCE.releaseCurrentMediaEntity();
        if (shouldIgnoreNavigationAction()) {
            isAnimatingSwipe = false;
            return;
        }

        if (activity.currentPromptIsQuestion()) {
            if (!activity.saveAnswersForCurrentScreen(!activity.mFormController.isFormReadOnly())) {
                // A constraint was violated so a dialog should be showing.
                return;
            }
        }

        // Any info stored about the last changed widget is useless when we move to a new view
        resetLastChangedWidget();

        if (FormEntryActivity.mFormController.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            int event;

            try {
                group_skip:
                do {
                    event = FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_OVER_GROUP);
                    switch (event) {
                        case FormEntryController.EVENT_QUESTION:
                            QuestionsView next = createView();
                            if (!resuming) {
                                showView(next, LocalePreferences.isLocaleRTL() ? AnimationType.LEFT : AnimationType.RIGHT);
                            } else {
                                showView(next, AnimationType.FADE, false);
                            }
                            break group_skip;
                        case FormEntryController.EVENT_END_OF_FORM:
                            // auto-advance questions might advance past the last form quesion
                            // In special case when questionsView is null (there is no question),
                            // to avoid exit dialog without saving option, shown from refreshCurrentView(),
                            // save and exit method called.
                            if (questionsView != null) {
                                refreshCurrentView();
                            } else {
                                activity.triggerUserFormComplete();
                            }
                            break group_skip;
                        case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                            createRepeatDialog();
                            break group_skip;
                        case FormEntryController.EVENT_GROUP:
                            //We only hit this event if we're at the _opening_ of a field
                            //list, so it seems totally fine to do it this way, technically
                            //though this should test whether the index is the field list
                            //host.
                            if (FormEntryActivity.mFormController.indexIsInFieldList()
                                    && FormEntryActivity.mFormController.getQuestionPrompts().length != 0) {
                                QuestionsView nextGroupView = createView();
                                if (!resuming) {
                                    showView(nextGroupView, LocalePreferences.isLocaleRTL() ? AnimationType.LEFT : AnimationType.RIGHT);
                                } else {
                                    showView(nextGroupView, AnimationType.FADE, false);
                                }
                                break group_skip;
                            }
                            // otherwise it's not a field-list group, so just skip it
                            break;
                        case FormEntryController.EVENT_REPEAT:
                            Log.i(TAG, "repeat: " + FormEntryActivity.mFormController.getFormIndex().getReference());
                            // skip repeats
                            break;
                        case FormEntryController.EVENT_REPEAT_JUNCTURE:
                            Log.i(TAG, "repeat juncture: "
                                    + FormEntryActivity.mFormController.getFormIndex().getReference());
                            // skip repeat junctures until we implement them
                            break;
                        default:
                            Log.w(TAG,
                                    "JavaRosa added a new EVENT type and didn't tell us... shame on them.");
                            break;
                    }
                } while (event != FormEntryController.EVENT_END_OF_FORM);
            } catch (XPathException e) {
                UserfacingErrorHandling.logErrorAndShowDialog(activity, e, FormEntryConstants.EXIT);
            }
        }
    }

    /**
     * Creates and displays a dialog asking the user if they'd like to create a repeat of the
     * current group.
     */
    private void createRepeatDialog() {
        isDialogShowing = true;

        // Determine the effect that back and next buttons should have
        FormNavigationController.NavigationDetails details;
        try {
            details = FormNavigationController.calculateNavigationStatus(FormEntryActivity.mFormController, questionsView);
        } catch (XPathTypeMismatchException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(activity, e, FormEntryConstants.EXIT);
            return;
        }
        final boolean backExitsForm = !details.relevantBeforeCurrentScreen;
        final boolean nextExitsForm = details.relevantAfterCurrentScreen == 0;

        // Assign title and text strings based on the current state
        String backText = Localization.get("repeat.dialog.go.back");
        String addAnotherText = Localization.get("repeat.dialog.add");
        String title, skipText;
        if (!nextExitsForm) {
            skipText = Localization.get("repeat.dialog.leave");
        } else {
            skipText = Localization.get("repeat.dialog.exit");
        }
        if (FormEntryActivity.mFormController.getLastRepeatCount() > 0) {
            title = Localization.get("repeat.dialog.add.another", FormEntryActivity.mFormController.getLastGroupText());
        } else {
            title = Localization.get("repeat.dialog.add.new", FormEntryActivity.mFormController.getLastGroupText());
        }

        // Create the choice dialog
        ContextThemeWrapper wrapper = new ContextThemeWrapper(activity, R.style.DialogBaseTheme);
        final PaneledChoiceDialog dialog = new HorizontalPaneledChoiceDialog(wrapper, title);

        // Panel 1: Back option
        View.OnClickListener backListener = v -> {
            if (backExitsForm) {
                activity.triggerUserQuitInput();
            } else {
                dialog.dismiss();
                refreshCurrentView(false);
            }
        };
        int backIconId;
        if (backExitsForm) {
            backIconId = R.drawable.icon_exit;
        } else {
            if (LocalePreferences.isLocaleRTL())
                backIconId = R.drawable.icon_next;
            else
                backIconId = R.drawable.icon_back;
        }
        DialogChoiceItem backItem = new DialogChoiceItem(backText, backIconId, backListener);

        // Panel 2: Add another option
        View.OnClickListener addAnotherListener = v -> {
            dialog.dismiss();
            try {
                FormEntryActivity.mFormController.newRepeat();
            } catch (XPathUnhandledException | XPathTypeMismatchException e) {
                Logger.exception("Error creating new repeat", e);
                UserfacingErrorHandling.logErrorAndShowDialog(activity, e, FormEntryConstants.EXIT);
                return;
            }
            showNextView();
        };
        DialogChoiceItem addAnotherItem = new DialogChoiceItem(addAnotherText, R.drawable.icon_new, addAnotherListener);

        // Panel 3: Skip option
        View.OnClickListener skipListener = v -> {
            dialog.dismiss();
            if (!nextExitsForm) {
                showNextView();
            } else {
                activity.triggerUserFormComplete();
            }
        };
        int skipIconId;
        if (nextExitsForm) {
            skipIconId = R.drawable.icon_done;
        } else {
            if (LocalePreferences.isLocaleRTL())
                skipIconId = R.drawable.icon_back;
            else
                skipIconId = R.drawable.icon_next;
        }
        DialogChoiceItem skipItem = new DialogChoiceItem(skipText, skipIconId, skipListener);

        dialog.setChoiceItems(new DialogChoiceItem[]{backItem, addAnotherItem, skipItem});
        dialog.makeNotCancelable();
        dialog.setOnDismissListener(
                d -> isDialogShowing = false
        );
        // Purposefully don't persist this dialog across rotation! Rotation
        // refreshes the view, which steps the form index back from the repeat
        // event. This can be fixed, but the dialog click listeners closures
        // capture refences to the old activity, so we need to redo our
        // infrastructure to forward new activities.
        dialog.showNonPersistentDialog();
    }

    protected void next() {
        if (!shouldIgnoreSwipeAction()) {
            isAnimatingSwipe = true;
            showNextView();
        }
    }

    protected boolean shouldIgnoreNavigationAction() {
        return blockingActionsManager.isBlocked();
    }

    protected boolean shouldIgnoreSwipeAction() {
        return isAnimatingSwipe || isDialogShowing;
    }

    /**
     * Creates and displays a dialog displaying the violated constraint.
     */
    protected void showConstraintWarning(FormIndex index, String constraintText,
                                         int saveStatus, boolean requestFocus) {

        switch (saveStatus) {
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
                if (constraintText == null) {
                    constraintText = StringUtils.getStringRobust(activity, R.string.invalid_answer_error);
                }
                break;
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                constraintText = StringUtils.getStringRobust(activity, R.string.required_answer_error);
                break;
        }

        boolean displayed = false;
        //We need to see if question in violation is on the screen, so we can show this cleanly.
        for (QuestionWidget q : questionsView.getWidgets()) {
            if (index.equals(q.getFormId())) {

                if (q.getAnswer() instanceof InvalidData) {
                    constraintText = ((InvalidData)q.getAnswer()).getErrorMessage();
                }

                q.notifyInvalid(constraintText, requestFocus);
                displayed = true;
                break;
            }
        }

        if (!displayed) {
            Toast popupMessage = Toast.makeText(activity, constraintText, Toast.LENGTH_SHORT);
            // center message to avoid overlapping with keyboard
            popupMessage.setGravity(Gravity.CENTER, 0, 0);
            popupMessage.show();
        }
        isAnimatingSwipe = false;
    }

    @Override
    public void onAnimationEnd(Animation arg0) {
        isAnimatingSwipe = false;
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

    protected void setIsAnimatingSwipe() {
        isAnimatingSwipe = true;
    }

    private boolean hasGroupLabel() {
        return hasGroupLabel;
    }

    protected void recalcShouldHideGroupLabel(Rect newRootViewDimensions) {
        shouldHideGroupLabel =
                FormLayoutHelpers.determineNumberOfValidGroupLines(activity, newRootViewDimensions,
                        hasGroupLabel(), shouldHideGroupLabel);
    }

    protected void restoreFocusToCalloutQuestion() {
        int restoredFocusTo =
                questionsView.restoreFocusToQuestionThatCalledOut(activity, activity.getPendingWidget());
        if (restoredFocusTo != -1) {
            indexOfLastChangedWidget = restoredFocusTo;
        }
    }

    protected void saveInstanceState(Bundle outState) {
        outState.putInt(KEY_LAST_CHANGED_WIDGET, indexOfLastChangedWidget);
    }

    protected void restoreSavedState(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(KEY_LAST_CHANGED_WIDGET)) {
            indexOfLastChangedWidget = savedInstanceState.getInt(KEY_LAST_CHANGED_WIDGET);
        }
    }

    private void resetLastChangedWidget() {
        indexOfLastChangedWidget = -1;
    }

    protected void recordLastChangedWidgetIndex(QuestionWidget changedWidget) {
        indexOfLastChangedWidget = questionsView.getWidgets().indexOf(changedWidget);
    }

    /**
     * Identifies whether the questionlist featues an aggregatable intent callout and
     * displays the appropriate button if so.
     */
    private void updateCompoundIntentButtonVisibility() {
        CompoundIntentList i = questionsView.getAggregateIntentCallout();
        if (i == null) {
            hideCompoundIntentCalloutButton();
        } else {
            Button compoundDispatchButton =
                    activity.findViewById(R.id.multiple_intent_dispatch_button);
            compoundDispatchButton.setVisibility(View.VISIBLE);
            compoundDispatchButton.setText(i.getTitle() + ": " + i.getNumberOfCallouts());
        }
    }

    protected void hideCompoundIntentCalloutButton() {
        activity.findViewById(R.id.multiple_intent_dispatch_button).setVisibility(View.GONE);
    }

    protected void updateFormRelevancies() {
        if (formRelevanciesUpdateInProgress) {
            // Don't allow this method to call itself downstream accidentally
            return;
        }
        formRelevanciesUpdateInProgress = true;

        ArrayList<QuestionWidget> oldWidgets = questionsView.getWidgets();
        // These 2 calls need to be made here, rather than in the for loop below, because at that
        // point the widgets will have already started being updated to the values for the new view
        ArrayList<Vector<SelectChoice>> oldSelectChoices =
                FormRelevancyUpdating.getOldSelectChoicesForEachWidget(oldWidgets);
        ArrayList<String> oldQuestionTexts =
                FormRelevancyUpdating.getOldQuestionTextsForEachWidget(oldWidgets);

        activity.saveAnswersForCurrentScreen(false);

        FormEntryPrompt[] newValidPrompts;
        try {
            newValidPrompts = FormEntryActivity.mFormController.getQuestionPrompts();
        } catch (XPathException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(activity, e, FormEntryConstants.EXIT);
            return;
        }
        Set<FormEntryPrompt> promptsLeftInView = new HashSet<>();

        ArrayList<Integer> shouldRemoveFromView = new ArrayList<>();
        // Loop through all of the old widgets to determine which ones should stay in the new view
        for (int i = 0; i < oldWidgets.size(); i++) {

            //Intent widgets need to be fully rebuilt to update their intent callouts
            //depending on model changes.
            if (oldWidgets.get(i) instanceof IntentWidget) {
                shouldRemoveFromView.add(i);
                continue;
            }

            if (oldWidgets.get(i) instanceof ImageWidget) {
                // If there was change(in particular image-remove) in an image widget,
                // only then update the form to disk.
                activity.onExternalAttachmentUpdated();
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
        questionsView.removeQuestionsFromIndex(shouldRemoveFromView);

        // Now go through add add any new prompts that we need
        for (int i = 0; i < newValidPrompts.length; ++i) {
            FormEntryPrompt prompt = newValidPrompts[i];
            if (!promptsLeftInView.contains(prompt)) {
                // If the old version of this prompt was NOT left in the view, then add it
                questionsView.addQuestionToIndex(prompt,
                        FormEntryActivity.mFormController.getWidgetFactory(),
                        i,
                        FormEntryActivity.mFormController.indexIsInCompact(prompt.getIndex()));
            }
        }
        updateCompoundIntentButtonVisibility();

        formRelevanciesUpdateInProgress = false;
    }
}
