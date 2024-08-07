package org.commcare.views;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.WidgetChangedListener;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.preferences.FormEntryPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.BlockingActionsManager;
import org.commcare.utils.CompoundIntentList;
import org.commcare.utils.MarkupUtil;
import org.commcare.views.widgets.DateTimeWidget;
import org.commcare.views.widgets.IntentWidget;
import org.commcare.views.widgets.QuestionWidget;
import org.commcare.views.widgets.StringWidget;
import org.commcare.views.widgets.TimeWidget;
import org.commcare.views.widgets.WidgetFactory;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.Logger;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.XPathException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author carlhartung
 */
public class QuestionsView extends ScrollView
        implements OnLongClickListener, WidgetChangedListener {

    // starter random number for view IDs
    private final static int VIEW_ID = 12345;

    private final LinearLayout mView;
    private final LinearLayout.LayoutParams mLayout;
    private final ArrayList<QuestionWidget> widgets;
    private final ArrayList<View> dividers;

    private final int mQuestionFontsize;

    private WidgetChangedListener wcListener;
    private boolean hasListener = false;

    private int widgetIdCount = 0;
    private int mViewBannerCount = 0;

    private SpannableStringBuilder mGroupLabel;

    private final BlockingActionsManager blockingActionsManager;

    /**
     * If enabled, we use dividers between question prompts
     */
    private static final boolean SEPERATORS_ENABLED = false;

    public QuestionsView(Context context, BlockingActionsManager blockingActionsManager) {
        super(context);
        mQuestionFontsize = FormEntryPreferences.getQuestionFontSize();
        widgets = new ArrayList<>();
        dividers = new ArrayList<>();

        mView = (LinearLayout)inflate(getContext(), R.layout.odkview_layout, null);

        mLayout =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

        mGroupLabel = null;
        this.blockingActionsManager = blockingActionsManager;
    }

    public QuestionsView(Context context, FormEntryPrompt[] questionPrompts,
                         FormEntryCaption[] groups, WidgetFactory factory,
                         WidgetChangedListener wcl, BlockingActionsManager blockingActionsManager) {
        this(context, blockingActionsManager);

        if (wcl != null) {
            hasListener = true;
            wcListener = wcl;
        }

        // display which group you are in as well as the question
        mGroupLabel = deriveGroupText(groups);

        String hintText = getHintText(questionPrompts);
        addHintText(hintText);

        boolean first = true;

        for (FormEntryPrompt p : questionPrompts) {
            if (!first) {
                View divider = new View(getContext());
                if (SEPERATORS_ENABLED) {
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
            qw = factory.createWidgetFromPrompt(p,
                    getContext(),
                    FormEntryActivity.mFormController.indexIsInCompact(p.getIndex()));
            qw.setLongClickable(true);
            qw.setOnLongClickListener(this);
            qw.setId(VIEW_ID + widgetIdCount++);

            //Suppress the hint text if we bubbled it
            if (hintText != null) {
                qw.hideHintText();
            }

            widgets.add(qw);
            mView.addView(qw, mLayout);

            qw.setChangedListeners(this, blockingActionsManager);
        }

        markLastStringWidget();

        addView(mView);
    }

    private void removeQuestionFromIndex(int i) {
        int dividerIndex = Math.max(i - 1, 0);

        if (dividerIndex < dividers.size()) {
            mView.removeView(dividers.get(dividerIndex));
            dividers.remove(dividerIndex);
        }

        if (i < widgets.size()) {
            mView.removeView(widgets.get(i));
            widgets.get(i).unsetListeners();
            widgets.remove(i);
        }
    }

    public void removeQuestionsFromIndex(ArrayList<Integer> indexes) {
        //Always gotta move backwards when removing, ensure that this list
        //goes backwards
        Collections.sort(indexes);
        Collections.reverse(indexes);

        for (int i = 0; i < indexes.size(); i++) {
            removeQuestionFromIndex(indexes.get(i));
        }
    }

    public void addQuestionToIndex(FormEntryPrompt fep, WidgetFactory factory, int i, boolean inCompactGroup) {
        View divider = new View(getContext());
        if (SEPERATORS_ENABLED) {
            divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
            divider.setMinimumHeight(3);
        } else {
            divider.setMinimumHeight(0);
        }
        int dividerIndex = mViewBannerCount;
        if (i > 0) {
            dividerIndex += 2 * i - 1;
        }
        mView.addView(divider, getViewIndex(dividerIndex));
        dividers.add(Math.max(0, i - 1), divider);

        QuestionWidget qw = factory.createWidgetFromPrompt(fep, getContext(), inCompactGroup);
        qw.setLongClickable(true);
        qw.setOnLongClickListener(this);
        qw.setId(VIEW_ID + widgetIdCount++);

        //Suppress the hint text if we bubbled it
//        if(hintText != null) { //TODO figure this out
//            qw.hideHintText();
//        }

        widgets.add(i, qw);
        mView.addView(qw, getViewIndex(2 * i + mViewBannerCount), mLayout);

        qw.setChangedListeners(this, blockingActionsManager);
    }


    /**
     * @return a HashMap of answers entered by the user for this set of widgets
     */
    public HashMap<FormIndex, IAnswerData> getAnswers() {
        HashMap<FormIndex, IAnswerData> answers = new HashMap<>();
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
        int oldHeight = this.getMeasuredHeight();

        if (oldHeight == 0 || Math.abs(((newHeight * 1.0 - oldHeight) / oldHeight)) > .2) {
            // Update the frame size and hint height based on the new height
            for (QuestionWidget qw : this.widgets) {
                qw.updateFrameSize(newHeight);
                qw.updateHintHeight(newHeight / 4);
            }
        } else {
            // Check to see if any of our QuestionWidgets have a hint text that was initially
            // displayed without proper height spec information
            for (QuestionWidget qw : this.widgets) {
                if (qw.hintTextNeedsHeightSpec) {
                    qw.updateHintHeight(newHeight / 4);
                }
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateConstraintRelevancies(QuestionWidget changedWidget) {
        if (hasListener) {
            wcListener.widgetEntryChanged(changedWidget);
        }
    }

    /**
     * Returns the hierarchy of groups to which the question belongs.
     */
    private SpannableStringBuilder deriveGroupText(FormEntryCaption[] groups) {
        SpannableStringBuilder s = new SpannableStringBuilder("");
        String t;
        String m;
        int i;
        // list all groups in one string
        for (FormEntryCaption g : groups) {
            i = g.getMultiplicity() + 1;
            t = g.getLongText();
            m = g.getMarkdownText();

            if (m != null) {
                Spannable markdownSpannable = MarkupUtil.returnMarkdown(getContext(), m);
                s.append(markdownSpannable);
            } else if (t != null && !t.trim().equals("")) {
                s.append(t);
            } else {
                continue;
            }

            if (g.repeats() && i > 0) {
                s.append(" (").append(String.valueOf(i)).append(")");
            }
            s.append(" > ");
        }

        //remove the trailing " > "
        if (s.length() > 0) {
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

    private String getHintText(FormEntryPrompt[] questionPrompts) {
        //Figure out if we share hint text between questions
        String hintText = null;
        try {
            if (questionPrompts.length > 1) {
                hintText = questionPrompts[0].getHintText();
                for (FormEntryPrompt p : questionPrompts) {
                    //If something doesn't have hint text at all,
                    //bail
                    String curHintText = p.getHintText();
                    //Otherwise see if it matches
                    if (curHintText == null || !curHintText.equals(hintText)) {
                        //If not, we can't do this trick
                        hintText = null;
                        break;
                    }
                }
            }
        } catch (XPathException exception) {
            new UserfacingErrorHandling<>().createErrorDialog((CommCareActivity)getContext(), exception.getLocalizedMessage(), true);
        }

        return hintText;
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

    public void setFocus(Context context, int indexOfLastChangedWidget) {
        QuestionWidget widgetToFocus = null;
        if (indexOfLastChangedWidget != -1 && indexOfLastChangedWidget < widgets.size()) {
            widgetToFocus = widgets.get(indexOfLastChangedWidget);
        } else if (widgets.size() > 0) {
            widgetToFocus = widgets.get(0);
        }
        if (widgetToFocus != null) {
            scrollToWidget(widgetToFocus);
            widgetToFocus.setFocus(context);
        }
    }

    private void scrollToWidget(final QuestionWidget widget) {
        new Handler().post(() -> QuestionsView.this.scrollTo(0, widget.getTop()));
    }

    /**
     * @param pendingIntentWidget - the widget for which a callout from form entry just occurred,
     *                            if there is one
     * @return the index of the widget that focus was restored to, or -1 if there was no
     * widget that just called out
     */
    public int restoreFocusToQuestionThatCalledOut(Context context, QuestionWidget pendingIntentWidget) {
        if (pendingIntentWidget != null) {
            int index = widgets.indexOf(pendingIntentWidget);
            setFocus(context, index);
            return index;
        }
        return -1;
    }

    /**
     * Called when another activity returns information to answer this question.
     */
    public void setBinaryData(Object answer, PendingCalloutInterface pendingCalloutInterface) {
        FormIndex questionFormIndex = pendingCalloutInterface.getPendingCalloutFormIndex();
        if (questionFormIndex == null) {
            Logger.log(LogTypes.SOFT_ASSERT,
                    "No pending callout index was set when trying to attach pending data.");
            return;
        }

        for (QuestionWidget q : widgets) {
            if (questionFormIndex.equals(q.getFormId())) {
                q.setBinaryData(answer);
                return;
            }
        }
        Logger.log(LogTypes.SOFT_ASSERT,
                "Unable to find question widget to attach pending data to.");
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

    public boolean isQuestionList() {
        return widgets.size() > 1;
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        for (QuestionWidget qw : widgets) {
            qw.setOnFocusChangeListener(l);
        }
    }

    public void teardownView() {
        for (QuestionWidget widget : widgets) {
            widget.unsetListeners();
            widget.setOnCreateContextMenuListener(null);
        }
        wcListener = null;
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
    public void widgetEntryChanged(QuestionWidget changedWidget) {
        updateConstraintRelevancies(changedWidget);
        markLastStringWidget();
    }

    private void markLastStringWidget() {
        StringWidget last = null;
        for (QuestionWidget q : widgets) {
            if (q instanceof StringWidget) {
                if (last != null) {
                    last.setLastQuestion(false);
                }
                last = (StringWidget)q;
                last.setLastQuestion(true);
            }
        }
    }

    /**
     * Translate question index to view index.
     *
     * @param questionIndex Index in the list of questions.
     * @return Index of question's view in mView.
     */
    private int getViewIndex(int questionIndex) {
        return questionIndex;
    }

    /**
     * Takes in a form entry prompt that is obtained generically and if there
     * is already one on screen (which, for instance, may have cached some of its data)
     * returns the object in use currently.
     */
    public FormEntryPrompt getOnScreenPrompt(FormEntryPrompt prompt) {
        FormIndex index = prompt.getIndex();
        for (QuestionWidget widget : this.getWidgets()) {
            if (widget.getFormId().equals(index)) {
                return widget.getPrompt();
            }
        }
        return prompt;
    }

    public CompoundIntentList getAggregateIntentCallout() {
        CompoundIntentList compoundedCallout = null;
        for (QuestionWidget widget : this.getWidgets()) {
            if (widget instanceof IntentWidget) {
                boolean expectResult = compoundedCallout != null;
                compoundedCallout = ((IntentWidget)widget).addToCompoundIntent(compoundedCallout);
                if (compoundedCallout == null && expectResult) {
                    return null;
                }
            }
        }
        if (compoundedCallout == null || compoundedCallout.getNumberOfCallouts() <= 1) {
            return null;
        }
        return compoundedCallout;
    }
}
