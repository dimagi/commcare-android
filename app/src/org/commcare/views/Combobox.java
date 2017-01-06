package org.commcare.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.views.widgets.SpinnerWidget;

import java.lang.reflect.Method;
import java.util.Vector;

/**
 * Created by amstone326 on 1/4/17.
 */

public class Combobox extends AutoCompleteTextView {

    private static int TEXT_UNIT = TypedValue.COMPLEX_UNIT_DIP;

    private Vector<String> choices;
    private Vector<String> choicesAllLowerCase;
    private CharSequence lastAcceptableStringEntered = "";
    private int lastValidCursorLocation;
    private boolean fixingInvalidEntry;

    public Combobox(Context context, Vector<String> choices, boolean addEmptyFirstChoice, int fontSize) {
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
        setAdapter(new ComboboxAdapter(context, R.layout.custom_spinner_item, items, fontSize));

        setTextSize(TEXT_UNIT, fontSize);
        //setForceIgnoreOutsideTouchWithReflection();
        setThreshold(0);
        setListeners();
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

    private void setListeners() {
        addTextChangedListener(getWhileTypingValidator());

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDropDown();
            }
        });

        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    showDropDown();
                }
            }
        });
    }

    private TextWatcher getWhileTypingValidator() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!fixingInvalidEntry) {
                    lastValidCursorLocation = getSelectionStart();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fixingInvalidEntry = false;
                if (!isPrefixOfSomeChoiceValue(s.toString())) {
                    fixingInvalidEntry = true;
                    // Re-set the entered text to be what it was before this change was made
                    setText(lastAcceptableStringEntered);
                    // Put the cursor back where it was
                    setSelection(lastValidCursorLocation);
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

    private class ComboboxAdapter extends ArrayAdapter<String> {
        final float textSize;

        public ComboboxAdapter(final Context context, final int textViewResourceId,
                              final String[] objects, float textSize) {
            super(context, textViewResourceId, objects);
            this.textSize = textSize;
        }

        @Override
        // Defines the text view parameters for the drop down list entries
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView tv = (TextView)view.findViewById(android.R.id.text1);
            tv.setTextSize(TEXT_UNIT, textSize);
            tv.setPadding(10, 10, 10, 10);
            return view;
        }
    }

}
