package org.commcare.views.widgets;

import android.content.Context;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Gregorian date widget that has a wheel for choosing month instead of text entry
 */
public class SpinnerGregorianWidget extends GregorianDateWidget {

    public SpinnerGregorianWidget(Context context, FormEntryPrompt prompt, boolean closeButton, String calendarType){
        super(context, prompt, closeButton, calendarType);
    }

    @Override
    protected void inflateView(Context context){

    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {

    }

    @Override
    protected void autoFillEmptyTextFields() {

    }

    @Override
    protected void validateTextOnButtonPress() {
    }

    @Override
    public IAnswerData getAnswer(){
        return null;
    }

    @Override
    protected void clearAll(){

    }

    @Override
    public void setFocus(Context context) {

    }

}
