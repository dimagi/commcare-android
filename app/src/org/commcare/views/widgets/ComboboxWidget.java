package org.commcare.views.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;

import org.commcare.views.Combobox;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Vector;

/**
 * ComboboxWidget is identical to a SpinnerWidget (a select-one widget formatted as a dropdown
 * question), but with the added ability to filter the options in the dropdown list by typing into
 * an EditText box.
 *
 * @author Aliza Stone
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class ComboboxWidget extends QuestionWidget {

    private Vector<SelectChoice> choices;
    private Vector<String> choiceTexts;
    private Combobox comboBox;

    public ComboboxWidget(Context context, FormEntryPrompt prompt, boolean permissive) {
        super(context, prompt);
        initChoices(prompt);
        comboBox = new Combobox(context, choiceTexts, true, permissive, mQuestionFontSize);
        addView(comboBox);

        comboBox.setEnabled(!prompt.isReadOnly());
        comboBox.setFocusable(!prompt.isReadOnly());
        addListeners();
        fillInPreviousAnswer(prompt);
    }

    private void initChoices(FormEntryPrompt prompt) {
        choices = prompt.getSelectChoices();
        choiceTexts = new Vector<>();
        for (int i = 0; i < choices.size(); i++) {
            choiceTexts.add(prompt.getSelectChoiceText(choices.get(i)));
        }
    }

    private void addListeners() {
        comboBox.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                widgetEntryChanged();
            }
        });

        comboBox.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    comboBox.showDropDown();
                } else {
                    widgetEntryChanged();
                }
            }
        });

        comboBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearWarningMessage();
            }
        });
    }

    private void fillInPreviousAnswer(FormEntryPrompt prompt) {
        if (prompt.getAnswerValue() != null) {
            String previousAnswerValue = ((Selection)prompt.getAnswerValue().getValue()).getValue();
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).getValue().equals(previousAnswerValue)) {
                    comboBox.setText(choiceTexts.get(i));
                    // setting this text will cause the dropdown to open up, which we don't want
                    comboBox.dismissDropDown();
                    break;
                }
            }
        }
    }

    @Override
    public IAnswerData getAnswer() {
        // So that we can see any error message that gets shown as a result of this
        comboBox.dismissDropDown();

        String enteredText = comboBox.getText().toString();
        if (choiceTexts.contains(enteredText)) {
            int i = choiceTexts.indexOf(enteredText);
            return new SelectOneData(new Selection(choices.elementAt(i)));
        } else if ("".equals(enteredText)) {
            return null;
        } else {
            return new InvalidData("The text entered is not a valid answer choice",
                    new SelectOneData(new Selection(enteredText)));
        }
    }

    @Override
    public void clearAnswer() {
        comboBox.setText("");
    }

    @Override
    public void setFocus(Context context) {
        // Intentionally does nothing, in order to force user to click on the combobox themselves
        // and thus bring up the drop-down menu (rather than just starting to type without
        // realizing there is a dropdown menu available)
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
    }

}
