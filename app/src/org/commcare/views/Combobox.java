package org.commcare.views;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.AutoCompleteTextView;

import org.commcare.adapters.ComboboxAdapter;
import org.commcare.adapters.PermissiveComboboxAdapter;
import org.commcare.dalvik.R;
import org.commcare.views.widgets.SpinnerWidget;

import java.util.Vector;

/**
 * Created by amstone326 on 1/4/17.
 */

public class Combobox extends AutoCompleteTextView {

    private Vector<String> choices;
    private CharSequence lastAcceptableStringEntered = "";
    private int lastValidCursorLocation;
    private boolean fixingInvalidEntry;
    private ComboboxAdapter customAdapter;

    public Combobox(Context context, Vector<String> choices, boolean addEmptyFirstChoice,
                    boolean permissive, int fontSize) {
        super(context);
        this.choices = choices;
        setupAdapter(context, fontSize, addEmptyFirstChoice, permissive);
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
        setThreshold(1);
        setListeners();
    }

    private void setupAdapter(Context context, int fontSize, boolean addEmptyFirstChoice,
                              boolean permissive) {
        String[] itemsForAdapter = this.choices.toArray(new String[]{});
        if (addEmptyFirstChoice) {
            itemsForAdapter = SpinnerWidget.getChoicesWithEmptyFirstSlot(itemsForAdapter);
        }

        if (permissive) {
            customAdapter = new PermissiveComboboxAdapter(context, R.layout.custom_spinner_item,
                    itemsForAdapter, fontSize);
        } else {
            customAdapter = new StandardComboboxAdapter(context, R.layout.custom_spinner_item,
                    itemsForAdapter, fontSize);
        }

        setAdapter(customAdapter);
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
                if (!customAdapter.isValidUserEntry(s.toString())) {
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

    private class StandardComboboxAdapter extends ComboboxAdapter {

        StandardComboboxAdapter(final Context context, final int textViewResourceId,
                                final String[] objects, float textSize) {
            super(context, textViewResourceId, objects, textSize);
        }

        @Override
        public boolean isValidUserEntry (String enteredText){
            return isPrefixOfSomeChoiceValue(enteredText);
        }
    }

    private boolean isPrefixOfSomeChoiceValue(String text) {
        for (String choice : choices) {
            if (choice.toLowerCase().startsWith(text.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

}
