package org.commcare.activities.components;

import org.commcare.views.widgets.QuestionWidget;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.ArrayList;
import java.util.Vector;

/**
 * @author amstone326
 */
public class FormRelevancyUpdating {
    /**
     * @return A list of the select choices for each widget in the list of old widgets, with the
     * original order preserved
     */
    public static ArrayList<Vector<SelectChoice>> getOldSelectChoicesForEachWidget(ArrayList<QuestionWidget> oldWidgets) {
        ArrayList<Vector<SelectChoice>> selectChoicesList = new ArrayList<>();
        for (QuestionWidget qw : oldWidgets) {
            Vector<SelectChoice> oldSelectChoices = qw.getPrompt().getOldSelectChoices();
            selectChoicesList.add(oldSelectChoices);
        }
        return selectChoicesList;
    }

    /**
     * @return A list of the question texts for each widget in the list of old widgets, with the
     * original order preserved
     */
    public static ArrayList<String> getOldQuestionTextsForEachWidget(ArrayList<QuestionWidget> oldWidgets) {
        ArrayList<String> questionTextList = new ArrayList<>();
        for (QuestionWidget qw : oldWidgets) {
            questionTextList.add(qw.getPrompt().getQuestionText());
        }
        return questionTextList;
    }

    /**
     * @param newValidPrompts  All of the prompts that should be in the new view
     * @param oldPrompt        The prompt from the prior view for which we are seeking a match in the
     *                         list of new prompts
     * @param oldQuestionText  the question text of the old prompt
     * @param oldSelectChoices the select choices of the old prompt
     * @return The form entry prompt from the new list that is equivalent to oldPrompt, or null
     * if none exists
     */
    public static FormEntryPrompt getEquivalentPromptInNewList(FormEntryPrompt[] newValidPrompts,
                                                               FormEntryPrompt oldPrompt,
                                                               String oldQuestionText,
                                                               Vector<SelectChoice> oldSelectChoices) {
        for (FormEntryPrompt newPrompt : newValidPrompts) {
            if (newPrompt.getIndex().equals(oldPrompt.getIndex())
                    && newPrompt.hasSameDisplayContent(oldQuestionText, oldSelectChoices)) {
                // A new prompt is considered equivalent to the old prompt if both their  form
                // indices and display content (question text and select choices) are the same
                return newPrompt;
            }
        }
        return null;
    }
}
