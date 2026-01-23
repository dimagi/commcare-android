package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;

import org.commcare.adapters.ComboboxAdapter;
import org.commcare.views.Combobox;
import org.javarosa.core.model.ComboItem;
import org.javarosa.core.model.ComboboxFilterRule;
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
public class ComboboxWidget extends QuestionWidget {

    private Vector<SelectChoice> choices;
    private Vector<ComboItem> choiceComboItems;
    private Combobox comboBox;
    private boolean wasWidgetChangedOnTextChanged = false;

    public ComboboxWidget(Context context, FormEntryPrompt prompt, ComboboxFilterRule filterRule) {
        super(context, prompt);
        initChoices(prompt);
        comboBox = setUpComboboxForWidget(context, choiceComboItems, filterRule, mQuestionFontSize);
        addView(comboBox);

        comboBox.setEnabled(!prompt.isReadOnly());
        comboBox.setFocusable(!prompt.isReadOnly());
        addListeners();
        fillInPreviousAnswer(prompt);
    }

    private static Combobox setUpComboboxForWidget(Context context, Vector<ComboItem> choices,
                                             ComboboxFilterRule filterRule, int fontSize) {
        ComboboxAdapter adapter =
                getAdapterForComboboxWidget(context, choices.toArray(new ComboItem[]{}),
                        filterRule, fontSize);
        Combobox combobox = new Combobox(context, choices, adapter);
        combobox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
        return combobox;
    }

    private static ComboboxAdapter getAdapterForComboboxWidget(Context context, ComboItem[] choices,
                                                               ComboboxFilterRule filterRule,
                                                               int fontSize) {
        choices = getComboItemChoicesWithEmptyFirstSlot(choices);
        ComboboxAdapter adapter = new ComboboxAdapter(context, choices, filterRule);
        adapter.setCustomTextSize(fontSize);
        return adapter;
    }

    public static ComboItem[] getComboItemChoicesWithEmptyFirstSlot(ComboItem[] originalChoices) {
        ComboItem[] newChoicesList = new ComboItem[originalChoices.length+1];
        newChoicesList[0] = new ComboItem("", "", -1);
        System.arraycopy(originalChoices, 0, newChoicesList, 1, originalChoices.length);
        return newChoicesList;
    }

    private void initChoices(FormEntryPrompt prompt) {
        choices = getSelectChoices();
        choiceComboItems = new Vector<>();
        for (int i = 0; i < choices.size(); i++) {
            choiceComboItems.add(new ComboItem(prompt.getSelectChoiceText(choices.get(i)),choices.get(i).getValue(),choices.get(i).getIndex()));
        }
    }

    private void addListeners() {
        comboBox.setOnItemClickListener((parent, view, position, id) -> widgetEntryChanged());

        // Note that Combobox has an OnFocusChangeListener defined in its own class, so when
        // re-setting it here we have to make sure to do all of the same things that the original
        // implementation does, in addition to the new behavior
        comboBox.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                comboBox.showDropDown();
            } else {
                comboBox.autoCorrectCapitalization();
                widgetEntryChanged();
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
                try {
                    wasWidgetChangedOnTextChanged = true;
                    widgetEntryChanged();
                } finally {
                    wasWidgetChangedOnTextChanged = false;
                }
            }
        });
    }

    private void fillInPreviousAnswer(FormEntryPrompt prompt) {
        if (prompt.getAnswerValue() != null) {
            String previousAnswerValue = ((Selection)prompt.getAnswerValue().getValue()).getValue();
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).getValue().equals(previousAnswerValue)) {
                    comboBox.setText(choiceTexts.get(i));
                    break;
                }
            }
        }
    }

    @Override
    public IAnswerData getAnswer() {
        // So that we can see any error message that gets shown as a result of this
        if(!wasWidgetChangedOnTextChanged) {
            comboBox.dismissDropDown();
        }

        comboBox.autoCorrectCapitalization();
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
