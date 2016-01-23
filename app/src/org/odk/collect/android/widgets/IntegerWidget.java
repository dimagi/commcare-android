package org.odk.collect.android.widgets;

import android.content.Context;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.javarosa.core.model.condition.pivot.IntegerRangeHint;
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.LongData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.utilities.IntegerSizeFilter;

/**
 * Widget that restricts values to integers.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class IntegerWidget extends StringWidget {
    
    //1 for int. 0 for long?
    private final int number_type;

    public IntegerWidget(Context context, FormEntryPrompt prompt, boolean secret, int num_type) {
        super(context, prompt, secret);

        mAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_ACTION_NEXT);
        
        this.number_type=num_type;

        // needed to make long readonly text scroll
        mAnswer.setHorizontallyScrolling(false);
        if(!secret) {
            mAnswer.setSingleLine(false);
        }

        // only allows numbers and no periods
        mAnswer.setKeyListener(new DigitsKeyListener(true, false));

        addAnswerFilter(new IntegerSizeFilter());
        
        //We might have 
        
        if (prompt.isReadOnly()) {
            setBackgroundDrawable(null);
            setFocusable(false);
            setClickable(false);
        }
        
        if (getCurrentAnswer() != null){
            if(number_type==1){
                Integer i = (Integer) getCurrentAnswer().getValue();
                if (i != null) {
                    mAnswer.setText(i.toString());
                }
            }
            else{
                Long i= (Long) getCurrentAnswer().getValue();
                if (i != null) {
                    mAnswer.setText(i.toString());
                }
            }
        }
    }
    
    
    @Override
    protected int guessMaxStringLength(FormEntryPrompt prompt) throws UnpivotableExpressionException{
        int existingGuess = Integer.MAX_VALUE;
        try {
            existingGuess = super.guessMaxStringLength(prompt);
        } catch (UnpivotableExpressionException e) {
            
        }
        try {
            //Awful. Need factory for this
            IntegerRangeHint hint = new IntegerRangeHint();
            prompt.requestConstraintHint(hint);
            
            IntegerData maxexample = hint.getMax();
            IntegerData minexample = hint.getMin();
            
            if(minexample != null) {
                //If we didn't constrain the input to be 0 or more, don't bother
                if((Integer) minexample.getValue() < 0) {
                    throw new UnpivotableExpressionException(); 
                }
            } else {
                //could be negative. Not worth it
                throw new UnpivotableExpressionException();
            }
            
            if(maxexample != null) {
                int max = (Integer) maxexample.getValue();
                if(!hint.isMaxInclusive()) {
                    max -= 1;
                }
                return Math.min(existingGuess, String.valueOf(max).length());
            }                    
        } catch(Exception e) {

        }
        if(number_type==1){
            return Math.min(existingGuess, 9);
        } else {
            return Math.min(existingGuess, 15);
        }
    }
    
    @Override
    protected void setTextInputType(EditText mAnswer) {
        if(secret) {
            mAnswer.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
            mAnswer.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
    }


    @Override
    public IAnswerData getAnswer() {
        String s = mAnswer.getText().toString().trim();
        if (s == null || s.equals("")) {
            return null;
        } else {
            try {
                if(number_type==1){
                    return new IntegerData(Integer.parseInt(s));
                }
                else{
                    return new LongData(Long.parseLong(s));
                }
            } catch (Exception NumberFormatException) { 
                return null;
            }
        }
    }
    /**
     * If this is the last question, set the action button to close the keyboard
     */
    @Override
    public void setLastQuestion(boolean isLast){
        if(isLast){
            mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_ACTION_DONE);
        } else{
            mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_ACTION_NEXT);
        }
    }

    @Override
    public void onClick(View v) {
        setFocus(getContext());
        // don't revert click behavior in this case since it might be customized.
    }

}
