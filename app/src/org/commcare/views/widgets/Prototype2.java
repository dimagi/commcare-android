package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.commcare.utils.UniversalDate.MILLIS_IN_DAY;

/**
 * Created by Saumya on 6/2/2016.
 */
public class Prototype2 extends QuestionWidget {

    private GregorianDateWidget myGreg;
    private CalendarWidget myCal;

    public Prototype2(Context context, FormEntryPrompt prompt){
        super(context, prompt);

        myGreg = new GregorianDateWidget(context, prompt);
        myCal = new CalendarWidget(context, prompt);

        myCal.setVisibility(GONE);

        myGreg.removeQuestionText();
        myCal.removeQuestionText();

        addView(myCal);
        addView(myGreg);

        initView();
    }

    private void initView(){
        removeView(findViewById(R.id.dayupbtn));
        removeView(findViewById(R.id.daydownbtn));
        removeView(findViewById(R.id.monthupbtn));
        removeView(findViewById(R.id.monthdownbtn));
        removeView(findViewById(R.id.yearupbtn));
        removeView(findViewById(R.id.yeardownbtn));
        removeView(findViewById(R.id.clearall));
        removeView(findViewById(R.id.gregdayofweek));
    }

    @Override
    public IAnswerData getAnswer() {
        return myGreg.getAnswer();
    }

    @Override
    public void clearAnswer() {
        myGreg.clearAnswer();
    }

    @Override
    public void setFocus(Context context) {

    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }
}
