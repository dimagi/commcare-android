package org.odk.collect.android.activities.components;

import org.commcare.views.QuestionsView;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.logic.FormController;

public class FormNavigationController {
    public static class NavigationDetails {
        public int totalQuestions = 0;
        public int completedQuestions = 0;
        public boolean relevantBeforeCurrentScreen = false;
        public boolean isFirstScreen = false;

        public int answeredOnScreen = 0;
        public int requiredOnScreen = 0;

        public int relevantAfterCurrentScreen = 0;
        public FormIndex currentScreenExit = null;

        public boolean isFormDone() {
            return relevantAfterCurrentScreen == 0 &&
                    (requiredOnScreen == answeredOnScreen ||
                            requiredOnScreen < 1);
        }
    }

    public static NavigationDetails calculateNavigationStatus(FormController formEntryController,
                                                              QuestionsView view) {
        NavigationDetails details = new NavigationDetails();

        FormIndex userFormIndex = formEntryController.getFormIndex();
        FormIndex currentFormIndex = FormIndex.createBeginningOfFormIndex();
        formEntryController.expandRepeats(currentFormIndex);
        int event = formEntryController.getEvent(currentFormIndex);

        // keep track of whether there is a question that exists before the
        // current screen
        boolean onCurrentScreen = false;

        while (event != FormEntryController.EVENT_END_OF_FORM) {
            int comparison = currentFormIndex.compareTo(userFormIndex);

            if (comparison == 0) {
                onCurrentScreen = true;
                details.currentScreenExit = formEntryController.getNextFormIndex(currentFormIndex, true);
            }
            if (onCurrentScreen && currentFormIndex.equals(details.currentScreenExit)) {
                onCurrentScreen = false;
            }

            // Figure out if there are any events before this screen (either
            // new repeat or relevant questions are valid)
            if (event == FormEntryController.EVENT_QUESTION
                    || event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                // Figure out whether we're on the last screen
                if (!details.relevantBeforeCurrentScreen && !details.isFirstScreen) {

                    // We got to the current screen without finding a
                    // relevant question,
                    // I guess we're on the first one.
                    if (onCurrentScreen
                            && !details.relevantBeforeCurrentScreen) {
                        details.isFirstScreen = true;
                    } else {
                        // We're using the built in steps (and they take
                        // relevancy into account)
                        // so if there are prompts they have to be relevant
                        details.relevantBeforeCurrentScreen = true;
                    }
                }
            }

            if (event == FormEntryController.EVENT_QUESTION) {
                FormEntryPrompt[] prompts = formEntryController.getQuestionPrompts(currentFormIndex);

                if (!onCurrentScreen && details.currentScreenExit != null) {
                    details.relevantAfterCurrentScreen += prompts.length;
                }

                details.totalQuestions += prompts.length;
                // Current questions are complete only if they're answered.
                // Past questions are always complete.
                // Future questions are never complete.
                if (onCurrentScreen) {
                    for (FormEntryPrompt prompt : prompts) {
                        prompt = view.getOnScreenPrompt(prompt);
                        boolean isAnswered = prompt.getAnswerValue() != null
                                || prompt.getDataType() == Constants.DATATYPE_NULL;

                        if (prompt.isRequired()) {
                            details.requiredOnScreen++;

                            if (isAnswered) {
                                details.answeredOnScreen++;
                            }
                        }

                        if (isAnswered) {
                            details.completedQuestions++;
                        }
                    }
                } else if (comparison < 0) {
                    // For previous questions, consider all "complete"
                    details.completedQuestions += prompts.length;
                    // TODO: This doesn't properly capture state to
                    // determine whether we will end up out of the form if
                    // we hit back!
                    // Need to test _until_ we get a question that is
                    // relevant, then we can skip the relevancy tests
                }
            } else if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                // If we've already passed the current screen, this repeat
                // junction is coming up in the future and we will need to
                // know
                // about it
                if (!onCurrentScreen && details.currentScreenExit != null) {
                    details.totalQuestions++;
                    details.relevantAfterCurrentScreen++;
                } else {
                    // Otherwise we already passed it and it no longer
                    // affects the count
                }
            }
            currentFormIndex = formEntryController.getNextFormIndex(currentFormIndex, FormController.STEP_INTO_GROUP, false);
            event = formEntryController.getEvent(currentFormIndex);
        }

        // Set form back to correct state
        formEntryController.jumpToIndex(userFormIndex);

        return details;
    }
}
