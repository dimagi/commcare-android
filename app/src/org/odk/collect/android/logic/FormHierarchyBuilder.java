package org.odk.collect.android.logic;

import android.content.Context;
import android.graphics.Color;

import org.commcare.dalvik.R;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.activities.FormHierarchyActivity;

import java.util.List;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormHierarchyBuilder {
    private final List<HierarchyElement> formList;
    private final Context context;
    private boolean atBeginningOfForm;

    private String enclosingGroupRef = "";

    private FormHierarchyBuilder(Context context, List<HierarchyElement> formList) {
        this.formList = formList;
        this.context = context;
    }

    public static boolean build(Context context, List<HierarchyElement> formList, FormIndex currentIndex) {
        FormHierarchyBuilder builder = new FormHierarchyBuilder(context, formList);
        builder.hierarchyIndexSetup(currentIndex);
        builder.buildHierarchyList();
        return builder.atBeginningOfForm;
    }

    public void hierarchyIndexSetup(FormIndex currentIndex) {
        // If we're not at the first level, we're inside a repeated group so we want to only display
        // everything enclosed within that group.

        // If we're currently at a repeat node, record the name of the node and step to the next
        // node to display.
        if (FormEntryActivity.mFormController.getEvent() == FormEntryController.EVENT_REPEAT) {
            enclosingGroupRef =
                    FormEntryActivity.mFormController.getFormIndex().getReference().toString(false);
            FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
        } else {
            FormIndex startTest = FormHierarchyActivity.stepIndexOut(currentIndex);
            // If we have a 'group' tag, we want to step back until we hit a repeat or the
            // beginning.
            while (startTest != null
                    && FormEntryActivity.mFormController.getEvent(startTest) == FormEntryController.EVENT_GROUP) {
                startTest = FormHierarchyActivity.stepIndexOut(startTest);
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
            FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
            atBeginningOfForm = true;
        } else {
            atBeginningOfForm = false;
        }
    }

    public void buildHierarchyList() {
        // Refresh the current event in case we did step forward.
        int event = FormEntryActivity.mFormController.getEvent();

        while (event != FormEntryController.EVENT_END_OF_FORM && indexRefCompletelyPrefixedBy(enclosingGroupRef)) {
            switch (event) {
                case FormEntryController.EVENT_QUESTION:
                    addQuestionEntry();
                    break;
                case FormEntryController.EVENT_GROUP:
                    break;
                case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                    addNewRepeatHeading();
                    break;
                case FormEntryController.EVENT_REPEAT:
                    if (indexPointsToReference(enclosingGroupRef)) {
                        // Done displaying entries in a repeat element because
                        // we've reached the next repeat element.
                        return;
                    }
                    addRepeatHeading();
                    event = addRepeatChildren();
                    continue;
            }
            event = FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
        }
    }

    private void addQuestionEntry() {
        FormEntryPrompt fp = FormEntryActivity.mFormController.getQuestionPrompt();

        int fepIcon = getFormEntryPromptIcon(fp);
        formList.add(new HierarchyElement(fp.getLongText(), fp.getAnswerText(),
                fepIcon == -1 ? null : context.getResources().getDrawable(fepIcon),
                Color.WHITE, HierarchyEntryType.question, fp.getIndex()));
    }

    private void addNewRepeatHeading() {
        FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();

        int fepIcon = R.drawable.avatar_vellum_repeat_group;
        formList.add(new HierarchyElement(fc.getLongText(), null,
                context.getResources().getDrawable(fepIcon),
                Color.WHITE, HierarchyEntryType.question, fc.getIndex()));
    }

    private void addRepeatHeading() {
        FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();
        if (fc.getMultiplicity() == 0) {
            // This is the start of a repeating group. We only want to display
            // "Group #", so we mark this as the beginning and skip all of its children
            HierarchyElement group =
                    new HierarchyElement(fc.getLongText(), null,
                            context.getResources().getDrawable(R.drawable.expander_ic_minimized),
                            Color.WHITE,
                            HierarchyEntryType.collapsed, fc.getIndex());
            formList.add(group);
        }
    }

    private int addRepeatChildren() {
        int event = FormEntryActivity.mFormController.getEvent();
        String repeatReference =
                FormEntryActivity.mFormController.getFormIndex().getReference().toString(false);

        while (event != FormEntryController.EVENT_END_OF_FORM) {
            if (event == FormEntryController.EVENT_REPEAT && indexPointsToReference(repeatReference)) {
                addRepeatChild();
            } else if (!indexRefCompletelyPrefixedBy(repeatReference)) {
                return event;
            }
            event = FormEntryActivity.mFormController.stepToNextEvent(FormController.STEP_OVER_GROUP);
        }
        return event;
    }

    private boolean indexPointsToReference(String reference) {
        String ref = FormEntryActivity.mFormController.getFormIndex().getReference().toString(false);
        return reference.compareTo(ref) == 0;
    }

    private boolean indexRefCompletelyPrefixedBy(String prefixReference) {
        String indexReference =
                FormEntryActivity.mFormController.getFormIndex().getReference().toString(false);

        return indexReference.length() >= prefixReference.length() &&
                (prefixReference.equals(indexReference.substring(0, prefixReference.length())));
    }

    private void addRepeatChild() {
        // Add this group name to the drop down list for this repeating group.
        HierarchyElement h = formList.get(formList.size() - 1);
        String mIndent = "     ";
        FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();
        h.addChild(new HierarchyElement(mIndent + fc.getLongText() + " "
                + (fc.getMultiplicity() + 1), null, null, Color.WHITE, HierarchyEntryType.child, fc
                .getIndex()));
    }

    private static int getFormEntryPromptIcon(FormEntryPrompt fep) {
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
}
