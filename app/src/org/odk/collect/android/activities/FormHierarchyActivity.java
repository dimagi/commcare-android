package org.odk.collect.android.activities;

import android.app.ActionBar;
import android.app.ListActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.android.framework.SessionActivityRegistration;
import org.commcare.dalvik.R;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.adapters.HierarchyListAdapter;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.logic.HierarchyElement;

import java.util.ArrayList;
import java.util.List;

public class FormHierarchyActivity extends ListActivity {
    private static final int CHILD = 1;
    private static final int EXPANDED = 2;
    private static final int COLLAPSED = 3;
    private static final int QUESTION = 4;

    private Button jumpPreviousButton;
    private List<HierarchyElement> formList;
    private TextView mPath;
    private FormIndex mStartIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hierarchy_layout);

        addActionBarBack();

        // We use a static FormEntryController to make jumping faster.
        mStartIndex = FormEntryActivity.mFormController.getFormIndex();

        setTitle(Localization.get("home.menu.saved.forms"));

        mPath = (TextView)findViewById(R.id.pathtext);

        jumpPreviousButton = (Button)findViewById(R.id.jumpPreviousButton);
        jumpPreviousButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                goUpLevel();
            }
        });

        Button jumpBeginningButton = (Button)findViewById(R.id.jumpBeginningButton);
        jumpBeginningButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                FormEntryActivity.mFormController.jumpToIndex(FormIndex
                        .createBeginningOfFormIndex());
                setResult(RESULT_OK);
                finish();
            }
        });

        Button jumpEndButton = (Button)findViewById(R.id.jumpEndButton);
        jumpEndButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                FormEntryActivity.mFormController.jumpToIndex(FormIndex.createEndOfFormIndex());
                setResult(RESULT_OK);
                finish();
            }
        });

        // kinda slow, but works.
        // this scrolls to the last question the user was looking at
        getListView().post(new Runnable() {
            @Override
            public void run() {
                int position = 0;
                for (int i = 0; i < getListAdapter().getCount(); i++) {
                    HierarchyElement he = (HierarchyElement)getListAdapter().getItem(i);
                    if (mStartIndex.equals(he.getFormIndex())) {
                        position = i;
                        break;
                    }
                }
                getListView().setSelection(position);
            }
        });

        refreshView();
    }

    private void addActionBarBack() {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            ActionBar bar = getActionBar();
            if (bar != null){
                bar.setDisplayShowHomeEnabled(true);
                bar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SessionActivityRegistration.handleOrListenForSessionExpiration(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
    }

    private void goUpLevel() {
        FormIndex index = stepIndexOut(FormEntryActivity.mFormController.getFormIndex());
        int currentEvent = FormEntryActivity.mFormController.getEvent();

        // Step out of any group indexes that are present.
        while (index != null
                && FormEntryActivity.mFormController.getEvent(index) == FormEntryController.EVENT_GROUP) {
            index = stepIndexOut(index);
        }

        if (index == null) {
            FormEntryActivity.mFormController.jumpToIndex(FormIndex.createBeginningOfFormIndex());
        } else {
            if (currentEvent == FormEntryController.EVENT_REPEAT) {
                // We were at a repeat, so stepping back brought us to then previous level
                FormEntryActivity.mFormController.jumpToIndex(index);
            } else {
                // We were at a question, so stepping back brought us to either:
                // The beginning. or The start of a repeat. So we need to step
                // out again to go passed the repeat.
                index = stepIndexOut(index);
                if (index == null) {
                    FormEntryActivity.mFormController.jumpToIndex(FormIndex
                            .createBeginningOfFormIndex());
                } else {
                    FormEntryActivity.mFormController.jumpToIndex(index);
                }
            }
        }

        refreshView();
    }

    private String getCurrentPath() {
        FormIndex index = stepIndexOut(FormEntryActivity.mFormController.getFormIndex());

        String path = "";
        while (index != null) {
            path =
                    FormEntryActivity.mFormController.getCaptionPrompt(index).getLongText()
                            + " ("
                            + (FormEntryActivity.mFormController.getCaptionPrompt(index)
                            .getMultiplicity() + 1) + ") > " + path;

            index = stepIndexOut(index);
        }
        // return path?
        return path.substring(0, path.length() - 2);
    }

    private void refreshView() {
        // Record the current index so we can return to the same place if the user hits 'back'.
        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();

        // If we're not at the first level, we're inside a repeated group so we want to only display
        // everything enclosed within that group.
        String enclosingGroupRef = "";
        formList = new ArrayList<>();

        // If we're currently at a repeat node, record the name of the node and step to the next
        // node to display.
        if (FormEntryActivity.mFormController.getEvent() == FormEntryController.EVENT_REPEAT) {
            enclosingGroupRef =
                    FormEntryActivity.mFormController.getFormIndex().getReference().toString(false);
            FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
        } else {
            FormIndex startTest = stepIndexOut(currentIndex);
            // If we have a 'group' tag, we want to step back until we hit a repeat or the
            // beginning.
            while (startTest != null
                    && FormEntryActivity.mFormController.getEvent(startTest) == FormEntryController.EVENT_GROUP) {
                startTest = stepIndexOut(startTest);
            }
            if (startTest == null) {
                // check to see if the question is at the first level of the hierarchy. If it is,
                // display the root level from the beginning.
                FormEntryActivity.mFormController.jumpToIndex(FormIndex
                        .createBeginningOfFormIndex());
            } else {
                // otherwise we're at a repeated group
                FormEntryActivity.mFormController.jumpToIndex(startTest);
            }

            // now test again for repeat. This should be true at this point or we're at the
            // beginning
            if (FormEntryActivity.mFormController.getEvent() == FormEntryController.EVENT_REPEAT) {
                enclosingGroupRef =
                        FormEntryActivity.mFormController.getFormIndex().getReference().toString(false);
                FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
            }
        }

        int event = FormEntryActivity.mFormController.getEvent();
        if (event == FormEntryController.EVENT_BEGINNING_OF_FORM) {
            // The beginning of form has no valid prompt to display.
            FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
            mPath.setVisibility(View.GONE);
            jumpPreviousButton.setEnabled(false);
            jumpPreviousButton.setTextColor(getResources().getColor(R.color.edit_text_color));
        } else {
            mPath.setVisibility(View.VISIBLE);
            mPath.setText(getCurrentPath());
            jumpPreviousButton.setEnabled(true);
            jumpPreviousButton.setTextColor(getResources().getColor(R.color.cc_brand_color));
        }

        // Refresh the current event in case we did step forward.
        event = FormEntryActivity.mFormController.getEvent();

        // There may be repeating Groups at this level of the hierarchy, we use this variable to
        // keep track of them.
        String repeatedGroupRef = "";

        event_search:
        while (event != FormEntryController.EVENT_END_OF_FORM) {
            switch (event) {
                case FormEntryController.EVENT_QUESTION:
                    if (!repeatedGroupRef.equalsIgnoreCase("")) {
                        // We're in a repeating group, so skip this question and move to the next
                        // index.
                        event =
                                FormEntryActivity.mFormController
                                        .stepToNextEvent(FormController.STEP_INTO_GROUP);
                        continue;
                    }

                    FormEntryPrompt fp = FormEntryActivity.mFormController.getQuestionPrompt();
                    int fepIcon = getFormEntryPromptIcon(fp);
                    formList.add(new HierarchyElement(fp.getLongText(), fp.getAnswerText(), fepIcon == -1 ? null : getResources().getDrawable(fepIcon),
                            Color.WHITE, QUESTION, fp.getIndex()));
                    break;
                case FormEntryController.EVENT_GROUP:
                    // ignore group events
                    break;
                case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                    if (enclosingGroupRef.compareTo(FormEntryActivity.mFormController
                            .getFormIndex().getReference().toString(false)) == 0) {
                        // We were displaying a set of questions inside of a repeated group. This is
                        // the end of that group.
                        break event_search;
                    }

                    if (repeatedGroupRef.compareTo(FormEntryActivity.mFormController.getFormIndex()
                            .getReference().toString(false)) != 0) {
                        // We're in a repeating group, so skip this repeat prompt and move to the
                        // next event.
                        event =
                                FormEntryActivity.mFormController
                                        .stepToNextEvent(FormController.STEP_INTO_GROUP);
                        continue;
                    }

                    if (repeatedGroupRef.compareTo(FormEntryActivity.mFormController.getFormIndex()
                            .getReference().toString(false)) == 0) {
                        // This is the end of the current repeating group, so we reset the
                        // repeatedGroupName variable
                        repeatedGroupRef = "";
                    }
                    break;
                case FormEntryController.EVENT_REPEAT:
                    FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();
                    if (enclosingGroupRef.compareTo(FormEntryActivity.mFormController
                            .getFormIndex().getReference().toString(false)) == 0) {
                        // We were displaying a set of questions inside a repeated group. This is
                        // the end of that group.
                        break event_search;
                    }
                    if (repeatedGroupRef.equalsIgnoreCase("") && fc.getMultiplicity() == 0) {
                        // This is the start of a repeating group. We only want to display
                        // "Group #", so we mark this as the beginning and skip all of its children
                        HierarchyElement group =
                                new HierarchyElement(fc.getLongText(), null, getResources()
                                        .getDrawable(R.drawable.expander_ic_minimized), Color.WHITE,
                                        COLLAPSED, fc.getIndex());
                        repeatedGroupRef =
                                FormEntryActivity.mFormController.getFormIndex().getReference()
                                        .toString(false);
                        formList.add(group);
                    }

                    if (repeatedGroupRef.compareTo(FormEntryActivity.mFormController.getFormIndex()
                            .getReference().toString(false)) == 0) {
                        // Add this group name to the drop down list for this repeating group.
                        HierarchyElement h = formList.get(formList.size() - 1);
                        String mIndent = "     ";
                        h.addChild(new HierarchyElement(mIndent + fc.getLongText() + " "
                                + (fc.getMultiplicity() + 1), null, null, Color.WHITE, CHILD, fc
                                .getIndex()));
                    }
                    break;
            }
            event =
                    FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
        }

        HierarchyListAdapter itla = new HierarchyListAdapter(this);
        itla.setListItems(formList);
        setListAdapter(itla);

        // set the controller back to the current index in case the user hits 'back'
        FormEntryActivity.mFormController.jumpToIndex(currentIndex);
    }

    private int getFormEntryPromptIcon(FormEntryPrompt fep) {
        switch (fep.getControlType()) {
            case Constants.CONTROL_SELECT_ONE:
                return R.drawable.avatar_vellum_single_answer;
            case Constants.CONTROL_SELECT_MULTI:
                return R.drawable.avatar_vellum_multi_answer;
            case Constants.CONTROL_TEXTAREA:
                return R.drawable.avatar_vellum_text;
            case Constants.CONTROL_SECRET:
                return R.drawable.avatar_vellum_password;
            case Constants.CONTROL_LABEL:
                return R.drawable.avatar_vellum_label;
            case Constants.CONTROL_AUDIO_CAPTURE:
                return R.drawable.avatar_vellum_audio_capture;
            case Constants.CONTROL_VIDEO_CAPTURE:
                return R.drawable.avatar_vellum_video;
            case Constants.CONTROL_TRIGGER:
                return R.drawable.avatar_vellum_question_list;
            case Constants.CONTROL_IMAGE_CHOOSE:
                return R.drawable.avatar_search;
            case Constants.CONTROL_RANGE:
            case Constants.CONTROL_UPLOAD:
            case Constants.CONTROL_SUBMIT:
            case Constants.CONTROL_INPUT:
                return getDrawableIDFor(fep);
        }
        return -1;
    }

    private static int getDrawableIDFor(FormEntryPrompt fep) {
        switch (fep.getDataType()) {
            case Constants.DATATYPE_TEXT:
                return R.drawable.avatar_vellum_text;
            case Constants.DATATYPE_INTEGER:
                return R.drawable.avatar_vellum_integer;
            case Constants.DATATYPE_DECIMAL:
                return R.drawable.avatar_vellum_decimal;
            case Constants.DATATYPE_DATE:
                return R.drawable.avatar_vellum_date;
            case Constants.DATATYPE_DATE_TIME:
                return R.drawable.avatar_vellum_datetime;
            case Constants.DATATYPE_CHOICE:
                return R.drawable.avatar_vellum_single_answer;
            case Constants.DATATYPE_CHOICE_LIST:
                return R.drawable.avatar_vellum_multi_answer;
            case Constants.DATATYPE_GEOPOINT:
                return R.drawable.avatar_vellum_gps;
            case Constants.DATATYPE_BARCODE:
                return R.drawable.avatar_vellum_barcode;
        }
        return -1;
    }

    /**
     * used to go up one level in the formIndex. That is, if you're at 5_0, 1 (the second question
     * in a repeating group), this method will return a FormInex of 5_0 (the start of the repeating
     * group). If your at index 16 or 5_0, this will return null;
     */
    private FormIndex stepIndexOut(FormIndex index) {
        if (index.isTerminal()) {
            return null;
        } else {
            return new FormIndex(stepIndexOut(index.getNextLevel()), index);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        HierarchyElement h = (HierarchyElement)l.getItemAtPosition(position);
        if (h.getFormIndex() == null) {
            goUpLevel();
            return;
        }

        switch (h.getType()) {
            case EXPANDED:
                h.setType(COLLAPSED);
                ArrayList<HierarchyElement> children = h.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    formList.remove(position + 1);
                }
                h.setIcon(getResources().getDrawable(R.drawable.expander_ic_minimized));
                h.setColor(Color.WHITE);
                break;
            case COLLAPSED:
                h.setType(EXPANDED);
                ArrayList<HierarchyElement> children1 = h.getChildren();
                for (int i = 0; i < children1.size(); i++) {
                    formList.add(position + 1 + i, children1.get(i));
                }
                h.setIcon(getResources().getDrawable(R.drawable.expander_ic_maximized));
                h.setColor(Color.WHITE);
                break;
            case QUESTION:
                FormEntryActivity.mFormController.jumpToIndex(h.getFormIndex());
                setResult(RESULT_OK);
                finish();
                return;
            case CHILD:
                FormEntryActivity.mFormController.jumpToIndex(h.getFormIndex());
                refreshView();
                return;
        }

        // Should only get here if we've expanded or collapsed a group
        HierarchyListAdapter itla = new HierarchyListAdapter(this);
        itla.setListItems(formList);
        setListAdapter(itla);
        getListView().setSelection(position);
    }

    @Override
    public void onBackPressed() {
        if (FormEntryActivity.mFormController.getFormIndex().isTerminal()) {
            super.onBackPressed();
        } else {
            goUpLevel();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
