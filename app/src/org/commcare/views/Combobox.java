package org.commcare.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import org.commcare.views.widgets.SpinnerWidget;

import java.lang.reflect.Method;
import java.util.Vector;

/**
 * Created by amstone326 on 1/4/17.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class Combobox extends AutoCompleteTextView {

    private Vector<String> choices;
    private Vector<String> choicesAllLowerCase;

    private CharSequence lastAcceptableStringEntered = "";

    public Combobox(Context context, Vector<String> choices, boolean addEmptyFirstChoice) {
        super(context);

        this.choices = choices;
        this.choicesAllLowerCase = new Vector<>();
        for (String s : this.choices) {
            choicesAllLowerCase.add(s.toLowerCase());
        }

        String[] items = this.choices.toArray(new String[]{});
        if (addEmptyFirstChoice) {
            items = SpinnerWidget.getChoicesWithEmptyFirstSlot(items);
        }
        setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, items));

        setForceIgnoreOutsideTouchWithReflection();
        setThreshold(0);
        setListeners();
        //setValidator(getAfterTextEnteredValidator());
    }

    public String getSelection() {
        String enteredText = getText().toString();
        if (choices.contains(enteredText)) {
            return enteredText;
        }
        return null;
    }

    private boolean setForceIgnoreOutsideTouchWithReflection() {
        try {
            Method method = android.widget.AutoCompleteTextView.class.getMethod("setForceIgnoreOutsideTouch", boolean.class);
            method.invoke(this, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setListeners() {
        addTextChangedListener(getWhileTypingValidator());

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDropDown();
            }
        });
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
                    // Re-set the entered text to be what it was before this change was made
                    setText(lastAcceptableStringEntered);
                    // Move the cursor to the end of the text
                    setSelection(getText().length());
                } else {
                    lastAcceptableStringEntered = s.toString();
                }
            }
        };
    }

    private boolean isPrefixOfSomeChoiceValue(String text) {
        for (String choice : choicesAllLowerCase) {
            if (choice.startsWith(text.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private AutoCompleteTextView.Validator getAfterTextEnteredValidator() {
        return new AutoCompleteTextView.Validator() {

            @Override
            public boolean isValid(CharSequence text) {
                return choices.contains(text.toString());
            }

            @Override
            public CharSequence fixText(CharSequence invalidText) {
                if (choicesAllLowerCase.contains(invalidText.toString().toLowerCase())) {
                    // If the user has entered a valid answer but with different case,
                    // just change the case for them
                    int index = choicesAllLowerCase.indexOf(invalidText.toString().toLowerCase());
                    return choices.get(index);
                } else {
                    // Otherwise delete their answer
                    return "";
                }

            }
        };
    }

}
