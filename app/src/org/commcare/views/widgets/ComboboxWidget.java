package org.commcare.views.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;

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

        comboBox = new Combobox(context, choiceTexts, true);
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
            String previousAnswer = ((Selection)prompt.getAnswerValue().getValue()).getValue();
            for (int i = 0; i < choiceTexts.size(); i++) {
                String choiceValue = choiceTexts.get(i);
                if (choiceValue.equals(previousAnswer)) {
                    comboBox.setSelection(i+1);
                }
            }
        }
    }

    private void setListeners() {
        comboBox.setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
            @Override
            public void onDismiss() {
                comboBox.performValidation();
                widgetEntryChanged();
            }
        });

        comboBox.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                widgetEntryChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public IAnswerData getAnswer() {
        int i = comboBox.getListSelection();
        if (i < 1) {
            return null;
        } else {
            SelectChoice sc = choices.elementAt(i-1);
            return new SelectOneData(new Selection(sc));
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
