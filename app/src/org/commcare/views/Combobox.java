package org.commcare.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.AutoCompleteTextView;

import org.commcare.adapters.SpinnerAdapter;

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

    public Combobox(Context context, Vector<String> choices, int fontSize) {
        super(context);
        setTextSize(fontSize);

        this.choices = choices;
        this.choicesAllLowerCase = new Vector<>();
        for (String s : this.choices) {
            choicesAllLowerCase.add(s.toLowerCase());
        }

        setAdapter(new SpinnerAdapter(context, android.R.layout.simple_spinner_item,
                this.choices.toArray(new String[]{}), TypedValue.COMPLEX_UNIT_DIP, fontSize));

        setThreshold(0);
        setListeners();
        setForceIgnoreOutsideTouchWithReflection();

        //setValidator(getAfterTextEnteredValidator());
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

    private boolean isPrefixOfSomeChoiceValue(String text) {
        for (String choice : choicesAllLowerCase) {
            if (choice.startsWith(text.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void setListeners() {
        addTextChangedListener(getWhileTypingValidator());

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDropDown();
            }
        });

        setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
            @Override
            public void onDismiss() {
                performValidation();
            }
        });

        /*setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                performCompletion();
            }
        });*/
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
