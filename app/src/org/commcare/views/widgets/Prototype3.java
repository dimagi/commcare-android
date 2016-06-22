package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateData;
import org.javarosa.form.api.FormEntryPrompt;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Saumya on 6/2/2016.
 * Prototype 3 of 3
 */
public class Prototype3 extends Prototype2{

    private LinearLayout myLayout;
    private TextView weekday;

    public Prototype3(Context context, FormEntryPrompt prompt){

        super(context, prompt);

        LayoutInflater inflater = (LayoutInflater)context.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        myLayout = (LinearLayout) inflater.inflate(R.layout.prototype3, null);
        initView();
        addView(myLayout, 1);
    }

    private void initView(){

        weekday = (TextView) findViewById(R.id.greg_day_of_week);
        ((TextView) myLayout.findViewById(R.id.pro_3_day)).setText(weekday.getText());

        Button decDay = (Button) myLayout.findViewById(R.id.dec_day);

        decDay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.DATE, -1);
                refreshDisplay();
                ((TextView) myLayout.findViewById(R.id.pro_3_day)).setText(weekday.getText());
            }
        });

        Button incrDay = (Button) myLayout.findViewById(R.id.incr_day);

        incrDay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.DATE, 1);
                refreshDisplay();
                ((TextView) myLayout.findViewById(R.id.pro_3_day)).setText(weekday.getText());
            }
        });

        Button incWeek = (Button) myLayout.findViewById(R.id.inc_week);

        incWeek.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.DATE, 7);
                refreshDisplay();
                ((TextView) myLayout.findViewById(R.id.pro_3_day)).setText(weekday.getText());
            }
        });

        Button decWeek = (Button) myLayout.findViewById(R.id.dec_week);

        decWeek.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.DATE, -7);
                refreshDisplay();
                ((TextView) myLayout.findViewById(R.id.pro_3_day)).setText(weekday.getText());
            }
        });
    }
}
