package org.commcare.views.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Calendar;
import java.util.Locale;

/**
 * Gregorian date widget that has a scrollable list for month selection
 */

    public class ListGregorianWidget extends GregorianDateWidget {


    private Spinner monthSpinner;

    public ListGregorianWidget(Context context, FormEntryPrompt prompt, boolean closeButton, String calendarType){
            super(context, prompt, closeButton, calendarType);
        }

        @Override
        protected void setupMonthComponents(){
            monthList.add("");
            monthSpinner.setAdapter(new ArrayAdapter<>(getContext(), R.layout.calendar_date, monthList));
            monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    monthArrayPointer = position%12;
                    calendar.set(Calendar.MONTH, monthArrayPointer);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }

        @Override
        protected void inflateView(Context context){
            LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            gregorianView = (LinearLayout) vi.inflate(R.layout.list_gregorian_widget, null);
            addView(gregorianView);
            monthSpinner = (Spinner) gregorianView.findViewById(R.id.month_spinner);
        }

        @Override
        protected void updateDateDisplay(long millisFromJavaEpoch) {
            UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
            monthArrayPointer = dateUniv.month - 1;
            dayText.setText(String.format(DAYFORMAT, dateUniv.day));
            monthSpinner.setSelection(monthArrayPointer);
            yearText.setText(String.format(YEARFORMAT, dateUniv.year));
            calendar.setTimeInMillis(millisFromJavaEpoch);
            dayOfWeek.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));
        }

        @Override
        protected void autoFillEmptyTextFields() {
            if(dayText.getText().toString().isEmpty()){
                dayText.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
            }

            if(monthSpinner.getSelectedItem().equals("")){
                monthSpinner.setSelection(monthArrayPointer);
            }

            if(yearText.getText().toString().isEmpty()){
                yearText.setText(String.valueOf(calendar.get(Calendar.YEAR)));
            }
        }

        @Override
        protected void validateTextOnButtonPress() {

            String dayTextValue = dayText.getText().toString();
            int num = Integer.parseInt(dayTextValue);
            int monthMax = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

            if (num <= monthMax && num >= 1) {
                calendar.set(Calendar.DAY_OF_MONTH, num);
            }else{
                dayText.setText(String.valueOf(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }

            monthArrayPointer = monthSpinner.getSelectedItemPosition();
            calendar.set(Calendar.MONTH, monthArrayPointer);

            String yearTextValue = yearText.getText().toString();
            calendar.set(Calendar.YEAR, Integer.parseInt(yearTextValue));
        }

        @Override
        public IAnswerData getAnswer(){

            setFocus(getContext());

            String month = (String) monthSpinner.getSelectedItem();
            String day = dayText.getText().toString();
            String year = yearText.getText().toString();

            //All fields are empty - Like submitting a blank date
            if(month.isEmpty() && day.isEmpty() && year.isEmpty()){
                return null;
            }

            //Some but not all fields are empty - Error
            if(month.isEmpty() || day.isEmpty() || year.isEmpty()){
                return new InvalidData(Localization.get("empty.fields"), new DateData(calendar.getTime()));
            }

            //Invalid year (too low)
            if(Integer.parseInt(year) < MINYEAR){
                return new InvalidData(Localization.get("low.year"), new DateData(calendar.getTime()));
            }
            //Invalid month
            if(!monthList.contains(month)){
                return new InvalidData(Localization.get("invalid.month"), new DateData(calendar.getTime()));
            }

            //Invalid day (too high)
            if(Integer.parseInt(day) > calendar.getActualMaximum(Calendar.DAY_OF_MONTH)){
                return new InvalidData(Localization.get("high.date") + " " + String.valueOf(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)), new DateData(calendar.getTime()));
            }

            //Invalid day (too high)
            if(Integer.parseInt(day)< 1){
                return new InvalidData(Localization.get("low.date"), new DateData(calendar.getTime()));
            }

            //Invalid year (too high)
            if(Integer.parseInt(year) > maxYear){
                return new InvalidData(Localization.get("high.year") + " " + String.valueOf(maxYear), new DateData(calendar.getTime()));
            }
            return super.getAnswer();
        }

        @Override
        protected void clearAll(){
            dayText.setText("");
            monthSpinner.setSelection(12);
            yearText.setText("");
            setFocus(getContext());
        }

        @Override
        public void setFocus(Context context) {
            dayText.setCursorVisible(false);
            yearText.setCursorVisible(false);
        }

    }


