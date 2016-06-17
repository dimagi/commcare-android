package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Saumya on 5/27/2016.
 * A widget that accepts Gregorian dates using logic and GUI that are similar to the Nepali and Ethiopian widgets
 */
public class GregorianDateWidget extends AbstractUniversalDateWidget {

    private EditText dayTxt;
    private AutoCompleteTextView monthTxt;
    private EditText yearTxt;
    private TextView dayOfWeek;
    private Calendar selectedDate;
    private String[] monthNames;
    private List<String> monthList;
    private final int MIN_YEAR = 1900;
    private final String DAYFORMAT = "%02d";
    private final String YEARFORMAT = "%04d";
    private int maxYear;

    protected LinearLayout gregorianView;

    public GregorianDateWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        selectedDate = Calendar.getInstance();
        maxYear = selectedDate.get(Calendar.YEAR) + 1;
        ImageButton clearAll = (ImageButton) findViewById(R.id.clearall);
        clearAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAll();
            }
        });
    }

    @Override
    protected void initText(){
        dayOfWeek = (TextView) findViewById(R.id.gregdayofweek);
        dayTxt = (EditText)findViewById(R.id.daytxtfield);
        yearTxt = (EditText)findViewById(R.id.yeartxtfield);

        MonthAdapter monthAdapter = new MonthAdapter(getContext(), monthList);
        monthTxt = (AutoCompleteTextView) findViewById(R.id.monthtxtfield);
        monthTxt.setAdapter(monthAdapter);

        dayTxt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String contents = s.toString();

                if(contents.length() > 1) {

                    int num = Integer.parseInt(contents);
                    int monthMax = selectedDate.getActualMaximum(Calendar.DAY_OF_MONTH);

                    if (num <= monthMax && num >= 1) {
                        selectedDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(s.toString()));
                    }
                }
            }
        });

        monthTxt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String content = s.toString();

                if(monthList.contains(content)){
                    monthArrayPointer = monthList.indexOf(content);
                    selectedDate.set(Calendar.MONTH, monthArrayPointer);
                }
            }
        });


        yearTxt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String contents = s.toString();

                if(contents.length() >= 4 ){
                    if(Integer.parseInt(contents) <= maxYear){
                        selectedDate.set(Calendar.YEAR, Integer.parseInt(contents));
                    }
                }
            }
        });
    }

    private void clearAll(){
        dayTxt.setText("");
        monthTxt.setText("");
        yearTxt.setText("");
        super.setFocus(getContext());
    }

    @Override
    protected void inflateView(Context context){
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        gregorianView = (LinearLayout) vi.inflate(R.layout.gregorian_date_widget, null);
        addView(gregorianView);
    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        monthArrayPointer = dateUniv.month - 1;
        dayTxt.setText(String.format(DAYFORMAT, dateUniv.day));
        monthTxt.setText(monthsArray[monthArrayPointer]);
        yearTxt.setText(String.format(YEARFORMAT, dateUniv.year));
        selectedDate.setTimeInMillis(millisFromJavaEpoch);
        dayOfWeek.setText(selectedDate.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));
    }

    @Override
    protected long getCurrentMillis() {

        checkEmptyText();

        int day = Integer.parseInt(dayTxt.getText().toString());
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(yearTxt.getText().toString());

        if(day > selectedDate.getActualMaximum(Calendar.DAY_OF_MONTH)){
            dayTxt.setText(String.valueOf(selectedDate.getActualMaximum(Calendar.DAY_OF_MONTH)));
            day = Integer.parseInt(dayTxt.getText().toString());
        }

        return toMillisFromJavaEpoch(year, month, day, millisOfDayOffset);
    }

    private void checkEmptyText() {
        if(dayTxt.getText().toString().equals("")){
            dayTxt.setText(String.valueOf(selectedDate.get(Calendar.DAY_OF_MONTH)));
        }

        if(monthTxt.getText().toString().equals("")){
            monthTxt.setText(monthsArray[monthArrayPointer]);
        }

        if(yearTxt.getText().toString().equals("")){
            yearTxt.setText(String.valueOf(selectedDate.get(Calendar.YEAR)));
        }
    }

    @Override
    protected void updateGregorianDateHelperDisplay(){}

    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).minusMonths(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).minusYears(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        return constructUniversalDate(new DateTime(millisFromJavaEpoch));
    }

    @Override
    protected String[] getMonthsArray() {
        if(selectedDate == null) {
            selectedDate = Calendar.getInstance();
        }

        if(monthNames == null){
            monthNames = new String[12];
            final Map<String, Integer> monthMap = selectedDate.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
            monthList = new ArrayList<>(monthMap.keySet());

            Collections.sort(monthList, new Comparator<String>(){
                @Override
                public int compare(String a, String b){
                    return monthMap.get(a) - monthMap.get(b);
                }
            });

            monthNames = monthList.toArray(monthNames);
        }

        return monthNames;
    }

    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).plusMonths(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).plusYears(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day, long millisOffset) {
        DateTime dt = new DateTime()
                .withYear(year)
                .withMonthOfYear(month)
                .withDayOfMonth(day)
                .withMillisOfDay((int)millisOffset);
        return dt.getMillis();
    }

    @Override
    public IAnswerData getAnswer(){

        //Empty text fields
        if(monthTxt.getText().toString().equals("") || dayTxt.getText().toString().equals("") || yearTxt.getText().toString().equals("")){
            setFocus(getContext());
            return new InvalidData(Localization.get("empty.fields"), new DateData(selectedDate.getTime()));
        }
        //Invalid year (too low)
        if(Integer.parseInt(yearTxt.getText().toString()) < MIN_YEAR){
            setFocus(getContext());
            return new InvalidData(Localization.get("low.year"), new DateData(selectedDate.getTime()));
        }
        //Invalid month
        if(!monthList.contains(monthTxt.getText().toString())){
            setFocus(getContext());
            return new InvalidData(Localization.get("invalid.month"), new DateData(selectedDate.getTime()));
        }

        //Invalid day (too high)
        if(Integer.parseInt(dayTxt.getText().toString()) > selectedDate.getActualMaximum(Calendar.DAY_OF_MONTH)){
            setFocus(getContext());
            return new InvalidData(Localization.get("high.date") + String.valueOf(selectedDate.getActualMaximum(Calendar.DAY_OF_MONTH)), new DateData(selectedDate.getTime()));
        }

        //Invalid year (too high)
        if(Integer.parseInt(yearTxt.getText().toString()) > maxYear){
            setFocus(getContext());
            return new InvalidData(Localization.get("high.year") + String.valueOf(maxYear), new DateData(selectedDate.getTime()));
        }
        return super.getAnswer();
    }

    private UniversalDate constructUniversalDate(DateTime dt) {
        return new UniversalDate(
                dt.getYear(),
                dt.getMonthOfYear(),
                dt.getDayOfMonth(),
                dt.getMillis()
        );
    }

    protected void setDate(DateData newDate){
        Date nextDate = (Date) newDate.getValue();
        updateDateDisplay(nextDate.getTime());
    }

    protected void refresh(){
        setDate(new DateData(selectedDate.getTime()));
    }

    protected Calendar getMyCalendar(){
        return selectedDate;
    }

    private class MonthAdapter extends ArrayAdapter<String>{

        private LayoutInflater mInflater;

        public MonthAdapter(Context context,List<String> months){
            super(context, R.layout.calendar_date, months);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent){
            if(view == null){
                view = mInflater.inflate(R.layout.calendar_date, null);
            }

            String month = getItem(position);

            TextView text = (TextView) view;
            text.setHeight(120);
            text.setText(month);
            return text;
        }
    }
}