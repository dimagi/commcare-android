package org.commcare.views.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;

import org.commcare.views.Combobox;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Vector;

/**
 * Created by amstone326 on 1/3/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class ComboboxWidget extends QuestionWidget {

    private Vector<SelectChoice> choices;
    private Vector<String> choiceTexts;
    private Combobox comboBox;

    public ComboboxWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);
        initChoices(prompt);

        comboBox = new Combobox(context, choiceTexts, mQuestionFontSize);
        addView(comboBox);
        comboBox.setEnabled(!prompt.isReadOnly());
        comboBox.setFocusable(!prompt.isReadOnly());
        comboBox.requestFocus();
        setListeners();
        fillInPreviousAnswer(prompt);
    }

    private void initChoices(FormEntryPrompt prompt) {
        choices = prompt.getSelectChoices();
        choiceTexts = new Vector<>();
        for (int i = 0; i < choices.size(); i++) {
            choiceTexts.add(prompt.getSelectChoiceText(choices.get(i)));
        }
    }

    private void fillInPreviousAnswer(FormEntryPrompt prompt) {
        if (prompt.getAnswerValue() != null) {
            String previousAnswerValue = ((Selection)prompt.getAnswerValue().getValue()).getValue();
            for (int i = 0; i < choices.size(); i++) {
                String choiceValue = choices.get(i).getValue();
                if (choiceValue.equals(previousAnswerValue)) {
                    comboBox.setText(choiceTexts.get(i));
                    comboBox.performCompletion();
                    break;
                }
            }
        }
    }

    private void setListeners() {
        /*comboBox.setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
            @Override
            public void onDismiss() {
                comboBox.performValidation();
                widgetEntryChanged();
            }
        });*/

        comboBox.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                comboBox.performCompletion();
                widgetEntryChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public IAnswerData getAnswer() {
        String selected = comboBox.getSelection();
        if (selected == null) {
            return null;
        } else {
            int i = choiceTexts.indexOf(selected);
            return new SelectOneData(new Selection(choices.elementAt(i)));
        }
    }

    @Override
    public void clearAnswer() {
        comboBox.setSelection(0);
    }

    @Override
    public void setFocus(Context context) {
        comboBox.requestFocus();
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(comboBox, 0);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }
}
