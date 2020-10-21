package org.commcare.views;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;

import org.commcare.adapters.ComboboxAdapter;

import java.util.Vector;

import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

/**
 * Custom view that builds upon Android's built-in AutoCompleteTextView and adds:
 * -Validation and auto-correcting of user-entered text to only allow strings that are part of
 * an available answer choice
 * -The ability to set an adapter that implements custom filtering rules (rather than using
 * the default filter of AutoCompleteTextView)
 *
 * @author Aliza Stone
 */
public class Combobox extends AppCompatAutoCompleteTextView {

    private Vector<String> choices;
    private Vector<String> choicesAllLowerCase;
    private CharSequence lastAcceptableStringEntered = "";
    private int lastValidCursorLocation;
    private boolean fixingInvalidEntry;
    private ComboboxAdapter customAdapter;

    public Combobox(Context context, Vector<String> choices, ComboboxAdapter adapter) {
        super(context);
        this.customAdapter = adapter;

        setAdapter(adapter);
        setupChoices(choices);
        setThreshold(1);
        setListeners();
        setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    private void setupChoices(Vector<String> choices) {
        this.choices = choices;
        this.choicesAllLowerCase = new Vector<>();
        for (String s : this.choices) {
            choicesAllLowerCase.add(s.toLowerCase());
        }
    }

    private void setListeners() {
        if (customAdapter.shouldRestrictTyping()) {
            addTextChangedListener(getWhileTypingValidator());
        }

        setOnClickListener(v -> showDropDown());

        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showDropDown();
            } else {
                autoCorrectCapitalization();
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

    /**
     * If the user has entered a valid answer but with different case, change the case for them
     */
    public void autoCorrectCapitalization() {
        String enteredText = getText().toString();
        if (enteredText != null && !choices.contains(enteredText) &&
                choicesAllLowerCase.contains(enteredText.toLowerCase())) {
            int index = choicesAllLowerCase.indexOf(enteredText.toLowerCase());
            setText(choices.get(index));
        }
    }

}
