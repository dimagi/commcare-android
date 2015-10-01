package org.odk.collect.android.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.commcare.android.util.MarkupUtil;
import org.commcare.dalvik.R;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.WidgetChangedListener;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.preferences.PreferencesActivity.ProgressBarMode;
import org.odk.collect.android.widgets.IBinaryWidget;
import org.odk.collect.android.widgets.QuestionWidget;
import org.odk.collect.android.widgets.StringWidget;
import org.odk.collect.android.widgets.WidgetFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author carlhartung
 */
public class ODKView extends ScrollView
        implements OnLongClickListener, WidgetChangedListener {

    // starter random number for view IDs
    private final static int VIEW_ID = 12345;  
    
    private static final String TAG = ODKView.class.getSimpleName();

    private final LinearLayout mView;
    private final LinearLayout.LayoutParams mLayout;
    private final ArrayList<QuestionWidget> widgets;
    private final ArrayList<View> dividers;
    private ProgressBar mProgressBar;
    
    private final int mQuestionFontsize;

    public final static String FIELD_LIST = "field-list";
    
    private WidgetChangedListener wcListener;
    private boolean hasListener = false;
    
    private int widgetIdCount = 0;
    private int mViewBannerCount = 0;

    private boolean mProgressEnabled;
    
    private final SpannableStringBuilder mGroupLabel;

    /**
     * If enabled, we use dividers between question prompts
     */
    private static final boolean SEPERATORS_ENABLED = false;

    public ODKView(Context context, FormEntryPrompt[] questionPrompts,
                   FormEntryCaption[] groups, WidgetFactory factory,
                   WidgetChangedListener wcl) {
        super(context);
        
        if(wcl !=null){
            hasListener = true;
            wcListener = wcl;
        }
        
        SharedPreferences settings = 
             PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        String question_font =
                settings.getString(PreferencesActivity.KEY_FONT_SIZE, Collect.DEFAULT_FONTSIZE);

        mQuestionFontsize = Integer.valueOf(question_font);
        
        widgets = new ArrayList<QuestionWidget>();
        dividers = new ArrayList<View>();

        mView = (LinearLayout) inflate(getContext(), R.layout.odkview_layout, null);
        
        if(PreferencesActivity.getProgressBarMode(context) == ProgressBarMode.ProgressOnly) {
            this.mProgressEnabled = true;
        }

        // Construct progress bar
        if (mProgressEnabled) {
            mProgressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
            mProgressBar.setProgressDrawable(getResources().getDrawable(R.drawable.progressbar));
            
            LinearLayout.LayoutParams barLayout =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
            barLayout.setMargins(15, 15, 15, 15);
            barLayout.gravity = Gravity.BOTTOM;
            
            LinearLayout barView = new LinearLayout(getContext());
            barView.setOrientation(LinearLayout.VERTICAL);
            barView.setGravity(Gravity.BOTTOM);
            barView.addView(mProgressBar);
            mView.addView(barView, barLayout);
        }

        mLayout =
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

        //Figure out if we share hint text between questions
        String hintText = null;
        if(questionPrompts.length > 1) {
            hintText = questionPrompts[0].getHintText();
            for (FormEntryPrompt p : questionPrompts) {
                //If something doesn't have hint text at all,
                //bail
                String curHintText = p.getHintText();
                //Otherwise see if it matches
                if(curHintText == null || !curHintText.equals(hintText)) {
                    //If not, we can't do this trick
                    hintText = null;
                    break;
                }
            }
        }

        // display which group you are in as well as the question
        mGroupLabel = deriveGroupText(groups);
        
        addHintText(hintText);
        
        boolean first = true;
        
        for (FormEntryPrompt p: questionPrompts) {
            
            if (!first) {
                View divider = new View(getContext());
                if(SEPERATORS_ENABLED) {
                    divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
                    divider.setMinimumHeight(3);
                } else {
                    divider.setMinimumHeight(0);
                }
                dividers.add(divider);
                mView.addView(divider);
            } else {
                first = false;
            }
            QuestionWidget qw;
            // if question or answer type is not supported, use text widget
            qw = factory.createWidgetFromPrompt(p, getContext());
            qw.setLongClickable(true);
            qw.setOnLongClickListener(this);
            qw.setId(VIEW_ID + widgetIdCount++);
            
            //Suppress the hint text if we bubbled it
            if(hintText != null) {
                qw.hideHintText();
            }

            widgets.add(qw);
            mView.addView(qw, mLayout);
            
            qw.setChangedListener(this);
        }
        
        updateLastQuestion();

        addView(mView);
    }
    
    void removeQuestionFromIndex(int i){
        int dividerIndex = Math.max(i - 1, 0);

        if (dividerIndex < dividers.size()) {
            mView.removeView(dividers.get(dividerIndex));
            dividers.remove(dividerIndex);
        }

        if (i < widgets.size()) {
            mView.removeView(widgets.get(i));
            widgets.remove(i);
        }
    }
    
    public void removeQuestionsFromIndex(ArrayList<Integer> indexes){
        //Always gotta move backwards when removing, ensure that this list
        //goes backwards
        Collections.sort(indexes);
        Collections.reverse(indexes);
        
        for(int i=0; i< indexes.size(); i++){
            removeQuestionFromIndex(indexes.get(i));
        }
    }
    
    public void addQuestionToIndex(FormEntryPrompt fep, WidgetFactory factory, int i){
        View divider = new View(getContext());
        if(SEPERATORS_ENABLED) {
            divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
            divider.setMinimumHeight(3);
        } else {
            divider.setMinimumHeight(0);
        }
        int dividerIndex = mViewBannerCount;
        if(i > 0) {
            dividerIndex += 2 * i - 1;
        }
        mView.addView(divider, getViewIndex(dividerIndex));
        dividers.add(Math.max(0, i - 1), divider);
        
        QuestionWidget qw = factory.createWidgetFromPrompt(fep, getContext());
        qw.setLongClickable(true);
        qw.setOnLongClickListener(this);
        qw.setId(VIEW_ID + widgetIdCount++);
        
        //Suppress the hint text if we bubbled it
//        if(hintText != null) { //TODO figure this out
//            qw.hideHintText();
//        }

        widgets.add(i, qw);
        mView.addView(qw, getViewIndex(2 * i + mViewBannerCount), mLayout);
        
        qw.setChangedListener(this);
    }


    /**
     * @return a HashMap of answers entered by the user for this set of widgets
     */
    public HashMap<FormIndex, IAnswerData> getAnswers() {
        HashMap<FormIndex, IAnswerData> answers = new HashMap<FormIndex, IAnswerData>();
        for (QuestionWidget q : widgets) {
            // The FormEntryPrompt has the FormIndex, which is where the answer gets stored. The
            // QuestionWidget has the answer the user has entered.
            FormEntryPrompt p = q.getPrompt();
            answers.put(p.getIndex(), q.getAnswer());
        }

        return answers;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int newHeight = MeasureSpec.getSize(heightMeasureSpec);
        int newWidth = MeasureSpec.getSize(widthMeasureSpec);
        int oldHeight = this.getMeasuredHeight();
        
        if(oldHeight == 0 || Math.abs(((newHeight * 1.0 - oldHeight) / oldHeight)) > .2) {
            for(QuestionWidget qw : this.widgets) { 
                qw.updateFrameSize(newWidth, newHeight);
            }
        }
        
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    
    private void updateConstraintRelevancies(){
        if(hasListener){
            wcListener.widgetEntryChanged();
        }
    }

    /**
     * Update progress bar
     * @param progress Current value
     * @param max Progress bar will be given range 0..max
     */
    public void updateProgressBar(int progress, int max) {
        if (mProgressBar != null) {
            mProgressBar.setMax(max);
            mProgressBar.setProgress(progress);
        }
    }

    /**
     * Returns the hierarchy of groups to which the question belongs.
     */
    private SpannableStringBuilder deriveGroupText(FormEntryCaption[] groups) {
        SpannableStringBuilder s = new SpannableStringBuilder("");
        String t = "";
        String m = "";
        int i;
        // list all groups in one string
        for (FormEntryCaption g : groups) {
            i = g.getMultiplicity() + 1;
            t = g.getLongText();
            m = g.getMarkdownText();

            if(m != null){
                Spannable markdownSpannable = MarkupUtil.returnMarkdown(getContext(), m);
                s.append(markdownSpannable);
            }
            else if (t != null) {
                s.append(t);
            }

            if (g.repeats() && i > 0) {
                s.append(" (" + i + ")");
            }
            s.append(" > ");
        }
        
        //remove the trailing " > "
        if(s.length() > 0) {
            s.delete(s.length() - 2, s.length());
        }
        
        return s;
    }
    
    
    /**
     * Ugh, the coupling here sucks, but this returns the group label
     * to be used for this odk view. 
     */
    public SpannableStringBuilder getGroupLabel() {
        return mGroupLabel;
    }
    
    private void addHintText(String hintText) {
        if (hintText != null && !hintText.equals("")) {
            TextView mHelpText = new TextView(getContext());
            mHelpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize - 3);
            mHelpText.setPadding(0, -5, 0, 7);
            // wrap to the widget of view
            mHelpText.setHorizontallyScrolling(false);
            mHelpText.setText(hintText);
            mHelpText.setTypeface(null, Typeface.ITALIC);

            mViewBannerCount++;
            mView.addView(mHelpText, mLayout);
        }
    }

    public void setFocus(Context context) {
        if (widgets.size() > 0) {
            widgets.get(0).setFocus(context);
        }
    }

    /**
     * Called when another activity returns information to answer this question.
     */
    public void setBinaryData(Object answer) {
        boolean set = false;
        for (QuestionWidget q : widgets) {
            if (q instanceof IBinaryWidget) {
                if (((IBinaryWidget) q).isWaitingForBinaryData()) {
                    ((IBinaryWidget) q).setBinaryData(answer);
                    set = true;
                    break;
                }
            }
        }

        if (!set) {
            Log.w(TAG, "Attempting to return data to a widget or set of widgets not looking for data");
                     
            for (QuestionWidget q : widgets) {
                if (q instanceof IBinaryWidget) {
                    ((IBinaryWidget) q).setBinaryData(answer);
                    break;
                }
            }
        }
    }


    /**
     * @return true if the answer was cleared, false otherwise.
     */
    public boolean clearAnswer() {
        // If there's only one widget, clear the answer.
        // If there are more, then force a long-press to clear the answer.
        if (widgets.size() == 1 && !widgets.get(0).getPrompt().isReadOnly()) {
            widgets.get(0).clearAnswer();
            return true;
        } else {
            return false;
        }
    }

    public ArrayList<QuestionWidget> getWidgets() {
        return widgets;
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        for (int i = 0; i < widgets.size(); i++) {
            QuestionWidget qw = widgets.get(i);
            qw.setOnFocusChangeListener(l);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        return false;
    }
    
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        for (QuestionWidget qw : widgets) {
            qw.cancelLongPress();
        }
    }

    @Override
    public void widgetEntryChanged() {
        updateConstraintRelevancies();
        updateLastQuestion();
    }
    
    void updateLastQuestion(){
        StringWidget last = null;
        
        for(QuestionWidget q: widgets){
            
            if(q instanceof StringWidget){
                if(last != null){
                    last.setLastQuestion(false);
                }
                last = (StringWidget)q;
                last.setLastQuestion(true);
            }
        }
    }
    
    /**
     * Remove question, based on position. 
     * @param questionIndex Index in question list.
     */
    public void removeWidget(int questionIndex){
        mView.removeViewAt(getViewIndex(questionIndex));
    }
    
    /**
     * Remove question, based on view object.
     * @param v View to remove
     */
    public void removeWidget(View v){
        mView.removeView(v);
    }

    /**
     * Translate question index to view index.
     * @param questionIndex Index in the list of questions.
     * @return Index of question's view in mView.
     */
    private int getViewIndex(int questionIndex) {
        // Account for progress bar
        if (mProgressEnabled) {
            return questionIndex + 1;
        }
        return questionIndex;
    }

    /**
     * Takes in a form entry prompt that is obtained generically and if there
     * is already one on screen (which, for isntance, may have cached some of its data)
     * returns the object in use currently.
     */
    public FormEntryPrompt getOnScreenPrompt(FormEntryPrompt prompt) {
        FormIndex index = prompt.getIndex();
        for(QuestionWidget widget : this.getWidgets()) {
            if(widget.getFormId().equals(index)) {
                return widget.getPrompt();
            }
        }
        return prompt;
    }


}
