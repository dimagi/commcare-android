package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * Adapter for displaying a list of items in a spinner or similar drop-down view. This adapter
 * automatically adds an empty first item to the list that is passed in.
 */
public class SpinnerAdapter extends ArrayAdapter<String> {
    final Context context;
    final String[] items;
    final int textUnit;
    final float textSize;


    public SpinnerAdapter(final Context context, final int textViewResourceId,
                          final String[] objects, int textUnit, float textSize) {
        super(context, textViewResourceId, getChoicesWithEmptyFirstSlot(objects));
        this.items = getChoicesWithEmptyFirstSlot(objects);
        this.context = context;
        this.textUnit = textUnit;
        this.textSize = textSize;
    }

    @Override
    // Defines the text view parameters for the drop down list entries
    public View getDropDownView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.custom_spinner_item, parent, false);
        }

        TextView tv = (TextView)convertView.findViewById(android.R.id.text1);

        tv.setText(items[position]);
        tv.setTextSize(textUnit, textSize);
        tv.setPadding(10, 10, 10, 10);

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

    private static String[] getChoicesWithEmptyFirstSlot(String[] originalChoices) {
        //Creates an empty option to be displayed the first time the widget is shown
        String[] newChoicesList = new String[originalChoices.length+1];
        newChoicesList[0] = "";
        System.arraycopy(originalChoices, 0, newChoicesList, 1, originalChoices.length);
        return newChoicesList;
    }

}
