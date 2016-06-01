package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    public GregorianDateWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        myCal = Calendar.getInstance();
    }

    @Override
    protected void initText(){
        dayOfWeek = (TextView) findViewById(R.id.gregdayofweek);
        dayTxt = (EditText)findViewById(R.id.daytxtfield);
        yearTxt = (EditText)findViewById(R.id.yeartxtfield);

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(getContext(), R.layout.autocomplete_month, monthList);
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

                    if (num > monthMax) {
                        s.clear();
                        s.append(String.valueOf(monthMax));
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
                }
            }
        });

        yearTxt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) { //validate for year too large
                String contents = s.toString();

                if(contents.length() > 0){
                    int maxYear = myCal.get(Calendar.YEAR)+1;

                    if(Integer.parseInt(contents) > maxYear){
                        s.clear();
                        s.append(String.valueOf(maxYear));
                    }
                }
            }
        });
    }

    public void clearAll(){
        dayTxt.setText("");
        monthTxt.setText("");
        yearTxt.setText("");
    }

    @Override
    protected void inflateView(Context context){
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View vv = vi.inflate(R.layout.gregorian_date_widget, null);
        addView(vv);
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
        if(dayTxt.getText().toString().length()==0){ //Validate for empty string in day
            dayTxt.append(String.valueOf(myCal.get(Calendar.DAY_OF_MONTH)));
        }

        if(yearTxt.getText().toString().length()==0 || Integer.parseInt(yearTxt.getText().toString()) < MIN_YEAR){ //Validate for empty string or too low for year
            yearTxt.setText(String.valueOf(myCal.get(Calendar.YEAR)));
        }
        if(!monthList.contains(monthTxt.getText().toString())){ //Validate for invalid month String
            monthArrayPointer = myCal.get(Calendar.MONTH);
            monthTxt.setText(myMonths[monthArrayPointer]);
        }

        int day = Integer.parseInt(dayTxt.getText().toString());
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(yearTxt.getText().toString());
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
            monthList = new ArrayList<String>(monthMap.keySet());

            Collections.sort(monthList, new Comparator<String>(){
                @Override
                public int compare(String a, String b){
                    return monthMap.get(a) - monthMap.get(b);
                }
            });

            myMonths = monthList.toArray(myMonths);
        }

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
        if(monthTxt.getText().toString().equals("") || dayTxt.getText().toString().equals("") || yearTxt.getText().toString().equals("")){
            return null;
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

    public void setDate(DateData newDate){
        Date nextDate = (Date) newDate.getValue();
        updateDateDisplay(nextDate.getTime());
    }

    public void removeQuestionText(){
        mQuestionText.setVisibility(GONE);
    }
}