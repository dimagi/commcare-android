package org.commcare.views.widgets;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Vector;

/**
 * Created by amstone326 on 1/3/17.
 */

public class ComboboxWidget extends QuestionWidget {

    private Vector<SelectChoice> choices;
    private Vector<String> choiceValues;
    private AutoCompleteTextView comboBox;

    public ComboboxWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        comboBox = new AutoCompleteTextView(context);
        setupChoices(prompt);
        comboBox.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line,
                SpinnerWidget.getChoicesWithEmptyFirstSlot(choiceValues.toArray(new String[]{}))));

        comboBox.setThreshold(0);
        comboBox.setEnabled(!prompt.isReadOnly());
        comboBox.setFocusable(!prompt.isReadOnly());
        comboBox.requestFocus();
        comboBox.addTextChangedListener(getWhileTypingValidator());
        comboBox.setValidator(getAfterTextEnteredValidator());

        fillInPreviousAnswer(prompt);
        setListeners();

        addView(comboBox);
    }

    private void setupChoices(FormEntryPrompt prompt) {
        choices = prompt.getSelectChoices();
        choiceValues = new Vector<>();
        for (int i = 0; i < choices.size(); i++) {
            choiceValues.add(prompt.getSelectChoiceText(choices.get(i)));
        }
    }

    private void fillInPreviousAnswer(FormEntryPrompt prompt) {
        if (prompt.getAnswerValue() != null) {
            String previousAnswer = ((Selection)prompt.getAnswerValue().getValue()).getValue();
            for (int i = 0; i < choiceValues.size(); i++) {
                String choiceValue = choiceValues.get(i);
                if (choiceValue.equals(previousAnswer)) {
                    comboBox.setSelection(i+1);
                }
            }
        }
    }

    private void setListeners() {
        comboBox.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                widgetEntryChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //do nothing here
            }
        });

        comboBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                comboBox.showDropDown();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            comboBox.setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
                @Override
                public void onDismiss() {
                    comboBox.performValidation();
                }
            });
        }
    }

    private AutoCompleteTextView.Validator getAfterTextEnteredValidator() {
        return new AutoCompleteTextView.Validator() {
            @Override
            public boolean isValid(CharSequence text) {
                return choiceValues.contains(text.toString());
            }

            @Override
            public CharSequence fixText(CharSequence invalidText) {
                return "";
            }
        };
    }

    private TextWatcher getWhileTypingValidator() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isPrefixOfSomeChoiceValue(s.toString())) {
                    //s.delete(s.length()-1, s.length());
                    comboBox.setText(s.subSequence(0, s.length()-1));
                    comboBox.setSelection(comboBox.getText().length());
                }
            }
        };
    }

    private boolean isPrefixOfSomeChoiceValue(String text) {
        for (String choice : choiceValues) {
            if (choice.toLowerCase().startsWith(text.toLowerCase())) {
                return true;
            }
        }
        return false;
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
