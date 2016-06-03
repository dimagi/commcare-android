package org.commcare.views.widgets;

import android.content.Context;
import android.view.View;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Created by Saumya on 6/2/2016.
 */
public class Prototype3 extends QuestionWidget{

    private Prototype2 myPro;
    private GregorianDateWidget myGreg;

    public Prototype3(Context context, FormEntryPrompt prompt){
        //TODO: Add buttons
        super(context, prompt);
        myPro = new Prototype2(context, prompt);
        myGreg = myPro.getMyGreg();

        myGreg.findViewById(R.id.gregdayofweek).setVisibility(VISIBLE);

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
