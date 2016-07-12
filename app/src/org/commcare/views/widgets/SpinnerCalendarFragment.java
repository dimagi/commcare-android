package org.commcare.views.widgets;

import android.content.res.Resources;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;

import org.commcare.dalvik.R;

import java.util.Calendar;

/**
 * Created by Saumya on 7/12/2016.
 */
public class SpinnerCalendarFragment extends CalendarFragment{

    private DatePicker datepicker;

    @Override
    protected void initMonthComponents(){
        datepicker  = (DatePicker) myLayout.findViewById(R.id.date_picker);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            datepicker.setCalendarViewShown(false);
        }

        datepicker.findViewById(Resources.getSystem().getIdentifier("day", "id", "android")).setVisibility(View.GONE);
        datepicker.findViewById(Resources.getSystem().getIdentifier("year", "id", "android")).setVisibility(View.GONE);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        datepicker.init(year, month, day, new DatePicker.OnDateChangedListener() {
            @Override
            public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                calendar.set(year, monthOfYear, dayOfMonth);
                refresh();
            }
        });

    }

    @Override
    protected void inflateView(LayoutInflater inflater, ViewGroup container) {
        myLayout = (LinearLayout) inflater.inflate(R.layout.spinner_calendar_widget, container);

    }

    @Override
    protected void updateMonthOnDateChange(){

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        datepicker.init(year, month, day, new DatePicker.OnDateChangedListener() {
            @Override
            public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                calendar.set(year, monthOfYear, dayOfMonth);
                refresh();
            }
        });
    }
}
