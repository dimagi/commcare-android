package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.model.data.StringData;
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
 * Inputs Gregorian dates using + and - buttons or direct text entry
 */
public class GregorianDateWidget extends AbstractUniversalDateWidget {

    private EditText dayTxt;
    private AutoCompleteTextView monthTxt;
    private EditText yearTxt;
    private TextView dayOfWeek;
    private Calendar myCal;
    private String[] myMonths;
    private List<String> monthList;
    private final int MIN_YEAR = 1900;
    private int maxYear;

    protected LinearLayout myView;

    public GregorianDateWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        myCal = Calendar.getInstance();
        maxYear = myCal.get(Calendar.YEAR) + 1;
        Button clearAll = (Button) findViewById(R.id.clearall);
        clearAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAll();
            }
        });
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
            public void afterTextChanged(Editable s) { //Validate for day too large
                String contents = s.toString();

                if(contents.length() > 1) {

                    int num = Integer.parseInt(contents);
                    int monthMax = myCal.getActualMaximum(Calendar.DAY_OF_MONTH);

                    if (num <= monthMax && num >= 1) {
                        myCal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(s.toString()));
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
            public void afterTextChanged(Editable s) { //Update month array pointer when month text updated
                String content = s.toString();

                if(monthList.contains(content)){
                    monthArrayPointer = monthList.indexOf(content);
                    myCal.set(Calendar.MONTH, monthArrayPointer);
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
                        myCal.set(Calendar.YEAR, Integer.parseInt(contents));
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
        myView = (LinearLayout) vi.inflate(R.layout.gregorian_date_widget, null);
        addView(myView);
    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        monthArrayPointer = dateUniv.month - 1;
        dayTxt.setText(String.format("%02d", dateUniv.day));
        monthTxt.setText(monthsArray[monthArrayPointer]);
        yearTxt.setText(String.format("%04d", dateUniv.year));
        myCal.setTimeInMillis(millisFromJavaEpoch);
        dayOfWeek.setText(myCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));
    }

    @Override
    protected long getCurrentMillis() {
        int day = Integer.parseInt(dayTxt.getText().toString());
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(yearTxt.getText().toString());

        if(day > myCal.getActualMaximum(Calendar.DAY_OF_MONTH)){
            dayTxt.setText(String.valueOf(myCal.getActualMaximum(Calendar.DAY_OF_MONTH)));
            day = Integer.parseInt(dayTxt.getText().toString());
        }

        return toMillisFromJavaEpoch(year, month, day, millisOfDayOffset);
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
        if(myCal == null) {
            myCal = Calendar.getInstance();
        }

        if(myMonths == null){
            myMonths = new String[12];
            final Map<String, Integer> monthMap = myCal.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
            monthList = new ArrayList<>(monthMap.keySet());

            Collections.sort(monthList, new Comparator<String>(){
                @Override
                public int compare(String a, String b){
                    return monthMap.get(a) - monthMap.get(b);
                }
            });

            myMonths = monthList.toArray(myMonths);
        }

        final Map<String, Integer> weekMap = myCal.getDisplayNames(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

        return myMonths;
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
            return new InvalidData("Please complete all fields", new DateData(myCal.getTime()));
        }
        //Invalid year (too low)
        if(Integer.parseInt(yearTxt.getText().toString()) < MIN_YEAR){
            setFocus(getContext());
            return new InvalidData("Year must be > 1900", new DateData(myCal.getTime()));
        }
        //Invalid month
        if(!monthList.contains(monthTxt.getText().toString())){
            setFocus(getContext());
            return new InvalidData("Month text is invalid", new DateData(myCal.getTime()));
        }

        if(Integer.parseInt(dayTxt.getText().toString()) > myCal.getActualMaximum(Calendar.DAY_OF_MONTH)){
            setFocus(getContext());
            return new InvalidData("Day can be at most " + String.valueOf(myCal.getActualMaximum(Calendar.DAY_OF_MONTH)), new DateData(myCal.getTime()));
        }

        if(Integer.parseInt(yearTxt.getText().toString()) > maxYear){
            setFocus(getContext());
            return new InvalidData("Year can be at most " + String.valueOf(maxYear), new DateData(myCal.getTime()));
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
        setDate(new DateData(myCal.getTime()));
    }

    protected Calendar getMyCalendar(){
        return myCal;
    }
}