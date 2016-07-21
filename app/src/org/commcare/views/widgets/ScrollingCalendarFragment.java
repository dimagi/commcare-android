package org.commcare.views.widgets;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import org.commcare.dalvik.R;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Saumya on 7/12/2016.
 */
public class ScrollingCalendarFragment extends CalendarFragment{

    private Spinner monthSpinner;
    private Spinner yearSpinner;

    @Override
    protected void initMonthComponents(){
        monthSpinner = (Spinner) myLayout.findViewById(R.id.calendar_spinner);

        final Map<String, Integer> monthMap = calendar.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        ArrayList<String> monthList = new ArrayList<>(monthMap.keySet());
        Collections.sort(monthList, new Comparator<String>(){
            @Override
            public int compare(String a, String b){
                return monthMap.get(a) - monthMap.get(b);
            }
        });
        monthSpinner.setAdapter(new ArrayAdapter<>(getContext(), R.layout.calendar_date, monthList));

        monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calendar.set(Calendar.MONTH, position);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    protected void inflateView(LayoutInflater inflater, ViewGroup container) {
        myLayout = (LinearLayout) inflater.inflate(R.layout.scrolling_calendar_widget, container);
    }

    @Override
    protected void setupYearComponents() {
        yearSpinner = (Spinner) myLayout.findViewById(R.id.year_spinner);

        ArrayList<String> years = new ArrayList<>();

        for(int i = 1900; i <= calendar.get(Calendar.YEAR)+4; i++){
            years.add(String.valueOf(i));
        }

        ArrayAdapter<String> yearAdapter = new ArrayAdapter<String>(getContext(), R.layout.calendar_date, years);
        yearSpinner.setAdapter(yearAdapter);

        yearSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calendar.set(Calendar.YEAR, position+1900);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    protected void updateMonthOnDateChange(){
        monthSpinner.setSelection(calendar.get(Calendar.MONTH));
    }

    @Override
    protected void updateYearOnDateChange(){
        yearSpinner.setSelection(calendar.get(Calendar.YEAR)-1900);
    }
}
