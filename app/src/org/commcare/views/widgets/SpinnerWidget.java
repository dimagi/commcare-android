package org.commcare.views.widgets;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Vector;

/**
 * SpinnerWidget handles select-one fields. Instead of a list of buttons it uses a spinner, wherein
 * the user clicks a button and the choices pop up in a dialogue box. The goal is to be more
 * compact. If images, audio, or video are specified in the select answers they are ignored.
 *
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class SpinnerWidget extends QuestionWidget {
    private final Vector<SelectChoice> mItems;
    private final Spinner spinner;
    private final String[] choices;


    public SpinnerWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        mItems = prompt.getSelectChoices();
        spinner = new Spinner(context);
        choices = new String[mItems.size()];

        for (int i = 0; i < mItems.size(); i++) {
            choices[i] = prompt.getSelectChoiceText(mItems.get(i));
        }

        // The spinner requires a custom adapter. It is defined below
        SpinnerAdapter adapter =
                new SpinnerAdapter(getContext(), android.R.layout.simple_spinner_item, choices,
                        TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize);

        spinner.setAdapter(adapter);
        spinner.setPrompt(prompt.getQuestionText());
        spinner.setEnabled(!prompt.isReadOnly());
        spinner.setFocusable(!prompt.isReadOnly());

        // Fill in previous answer
        String s = null;
        if (prompt.getAnswerValue() != null) {
            s = ((Selection)prompt.getAnswerValue().getValue()).getValue();
        }

        if (s != null) {
            for (int i = 0; i < mItems.size(); ++i) {
                String sMatch = mItems.get(i).getValue();
                if (sMatch.equals(s)) {
                    spinner.setSelection(i);
                }
            }
        }

        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (hasListener()) {
                    widgetChangedListener.widgetEntryChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //do nothing here
            }

        });

        addView(spinner);

    }


    @Override
    public IAnswerData getAnswer() {
        int i = spinner.getSelectedItemPosition();
        if (i < 1) {
            return null;
        } else {
            SelectChoice sc = mItems.elementAt(i-1); // 0th spot is empty
            return new SelectOneData(new Selection(sc));
        }
    }


    @Override
    public void clearAnswer() {
        // It seems that spinners cannot return a null answer. This resets the answer
        // to its original value, but it is not null.
        spinner.setSelection(0);
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);

    }

    // Defines how to display the select answers
    private class SpinnerAdapter extends ArrayAdapter<String> {
        final Context context;
        String[] items;
        final int textUnit;
        final float textSize;


        public SpinnerAdapter(final Context context, final int textViewResourceId,
                              final String[] objects, int textUnit, float textSize) {
            super(context, textViewResourceId, objects);
            this.context = context;
            this.textUnit = textUnit;
            this.textSize = textSize;

            //Creates an empty option to be displayed the first time the widget is shown
            items = new String[objects.length+1];
            items[0] = "";
            for(int i = 1; i < items.length; i ++){
                items[i] = objects[i-1];
            }
        }


        @Override
        // Defines the text view parameters for the drop down list entries
        public View getDropDownView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(R.layout.custom_spinner_item, parent, false);
            }

            TextView tv = (TextView)convertView.findViewById(android.R.id.text1);

            if(position == 0){
                tv.setHeight(0); //Hide empty option from dropdown view
            }else{
                tv.setHeight(3*(int)textSize+12);
                tv.setText(items[position]);
                tv.setTextSize(textUnit, textSize);
                tv.setPadding(10, 10, 10, 10);
            }

            return convertView;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
            }

            TextView tv = (TextView)convertView.findViewById(android.R.id.text1);
            tv.setText(items[position]);
            tv.setTextSize(textUnit, textSize);
            return convertView;
        }

    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        spinner.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        spinner.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        spinner.cancelLongPress();
    }

}
