package org.commcare.views.widgets;

import android.content.Context;
import android.view.View;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Created by Saumya on 5/29/2016.
 */
public class CalendarWidget extends QuestionWidget{


    public CalendarWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt);
    }

    @Override
    public IAnswerData getAnswer() {
        return null;
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
