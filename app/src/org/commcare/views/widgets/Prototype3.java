package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Saumya on 6/2/2016.
 */
public class Prototype3 extends QuestionWidget{

    private Prototype2 myPro2;
    private LinearLayout myLayout;

    public Prototype3(Context context, FormEntryPrompt prompt){
        //TODO: Add buttons
        super(context, prompt);
        myPro2 = new Prototype2(context, prompt);
        myPro2.removeQuestionText();

        LayoutInflater inflater = (LayoutInflater)context.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        myLayout = (LinearLayout) inflater.inflate(R.layout.prototype3, null);

        initView();
        addView(myLayout);
        addView(myPro2);

    }

    private void initView(){

        TextView weekday = (TextView) myPro2.findViewById(R.id.gregdayofweek);
        ((TextView) myLayout.findViewById(R.id.pro3day)).setText(weekday.getText());

        weekday.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String content = s.toString();
                TextView pro3day = (TextView) findViewById(R.id.pro3day);
                pro3day.setText(content);
            }
        });

        Button decDay = (Button) myLayout.findViewById(R.id.decday);

        decDay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Date current = (Date) myPro2.getMyGreg().getAnswer().getValue();
                Calendar cal = Calendar.getInstance();
                cal.setTime(current);
                cal.add(Calendar.DATE, -1);
                myPro2.getMyGreg().setDate(new DateData(cal.getTime()));
            }
        });

        Button incrDay = (Button) myLayout.findViewById(R.id.incrday);

        incrDay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Date current = (Date) myPro2.getMyGreg().getAnswer().getValue();
                Calendar cal = Calendar.getInstance();
                cal.setTime(current);
                cal.add(Calendar.DATE, 1);
                myPro2.getMyGreg().setDate(new DateData(cal.getTime()));
            }
        });

        Button incWeek = (Button) myLayout.findViewById(R.id.incweek);

        incWeek.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Date current = (Date) myPro2.getMyGreg().getAnswer().getValue();
                Calendar cal = Calendar.getInstance();
                cal.setTime(current);
                cal.add(Calendar.DATE, 7);
                myPro2.getMyGreg().setDate(new DateData(cal.getTime()));
            }
        });

        Button decWeek = (Button) myLayout.findViewById(R.id.decweek);

        decWeek.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Date current = (Date) myPro2.getMyGreg().getAnswer().getValue();
                Calendar cal = Calendar.getInstance();
                cal.setTime(current);
                cal.add(Calendar.DATE, -7);
                myPro2.getMyGreg().setDate(new DateData(cal.getTime()));
            }
        });
    }


    @Override
    public IAnswerData getAnswer() {
        return myPro2.getAnswer();
    }

    @Override
    public void clearAnswer() {

    }

    @Override
    public void setFocus(Context context) {

    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }
}
