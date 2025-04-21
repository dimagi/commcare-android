package org.commcare.logic;

import android.content.Context;

import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.FormHierarchyActivity;
import org.commcare.dalvik.R;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.XPathTypeMismatchException;

import java.util.List;

/**
 * Traverses the form building a hierarchy representation of the form.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormHierarchyBuilder {
    private final List<HierarchyElement> formList;
    private final Context context;
    private String hierarchyPath;

    private FormIndex enclosingGroupIndex;

    private FormHierarchyBuilder(Context context, List<HierarchyElement> formList) {
        this.formList = formList;
        this.context = context;
    }

    /**
     * Builds the form hierarchy list starting at the form controller's current index.
     *
     * @param context  Used for drawable resource loading
     * @param formList hierarchy elements are added to this list
     * @return Path representing the nesting level of the entries being shown
     */
    public static String populateHierarchyList(Context context, List<HierarchyElement> formList) {
        FormHierarchyBuilder builder = new FormHierarchyBuilder(context, formList);

        builder.hierarchyIndexSetup();
        builder.buildHierarchyList();

        return builder.hierarchyPath;
    }

    private void hierarchyIndexSetup() {
        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();
        enclosingGroupIndex = null;

        // If we're not at the first level, we're inside a repeated group so we
        // want to only display everything enclosed within that group.

        // If we're currently at a repeat node, record the name of the node and
        // step to the next node to display.
        if (FormEntryActivity.mFormController.getEvent() == FormEntryController.EVENT_REPEAT) {
            enclosingGroupIndex = FormEntryActivity.mFormController.getFormIndex();
            FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP);
        } else {
            FormIndex startTest = FormHierarchyActivity.stepIndexOut(currentIndex);
            // If we have a 'group' tag, we want to step back until we hit a
            // repeat or the beginning.
            while (startTest != null
                    && FormEntryActivity.mFormController.getEvent(startTest) == FormEntryController.EVENT_GROUP) {
                startTest = FormHierarchyActivity.stepIndexOut(startTest);
            }
            if (startTest == null) {
                // check to see if the question is at the first level of the
                // hierarchy. If it is, display the root level from the
                // beginning.
                FormEntryActivity.mFormController.jumpToIndex(FormIndex
                        .createBeginningOfFormIndex());
            } else {
                // otherwise we're at a repeated group
                FormEntryActivity.mFormController.jumpToIndex(startTest);
            }

            // now test again for repeat. This should be true at this point or
            // we're at the beginning
            if (FormEntryActivity.mFormController.getEvent() == FormEntryController.EVENT_REPEAT) {
                enclosingGroupIndex = FormEntryActivity.mFormController.getFormIndex();
                FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP);
            }
        }
        int event = FormEntryActivity.mFormController.getEvent();
        if (event == FormEntryController.EVENT_BEGINNING_OF_FORM) {
            FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP);
            hierarchyPath = "";
        } else {
            hierarchyPath = FormHierarchyActivity.getCurrentPath();
        }
    }

    private void buildHierarchyList() {
        // Refresh the current event in case we did step forward.
        int event = FormEntryActivity.mFormController.getEvent();
        while (event != FormEntryController.EVENT_END_OF_FORM && isCurrentIndexSubOf(enclosingGroupIndex)) {
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
                    addRepeatHeading();
                    event = addRepeatChildren();
                    continue;
            }
            event = FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP);
        }
    }

    private boolean isCurrentIndexSubOf(FormIndex enclosingIndex) {
        if (enclosingIndex == null) {
            return true;
        }

        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();
        return FormIndex.isSubElement(enclosingIndex, currentIndex);
    }

    private void addQuestionEntry() {
        FormEntryPrompt fp = FormEntryActivity.mFormController.getQuestionPrompt();

        int fepIcon = getFormEntryPromptIcon(fp);
        String questionText;
        boolean isError = false;
        try {
            questionText = fp.getLongText();
        } catch (XPathTypeMismatchException e) {
            questionText = e.getMessage();
            isError = true;
        }
        formList.add(new HierarchyElement(context, questionText, fp.getAnswerText(),
                fepIcon == -1 ? null : context.getDrawable(fepIcon),
                isError, HierarchyEntryType.question, fp.getIndex()));
    }

    private void addNewRepeatHeading() {
        FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();

        int fepIcon = R.drawable.avatar_vellum_repeat_group;
        formList.add(new HierarchyElement(context, fc.getLongText(), null,
                context.getDrawable(fepIcon),
                false, HierarchyEntryType.question, fc.getIndex()));
    }

    private void addRepeatHeading() {
        FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();
        if (fc.getMultiplicity() == 0) {
            // Only add the heading if it is the repeat group entry, not an element in the group.
            HierarchyElement group =
                    new HierarchyElement(context, fc.getLongText(), null,
                            context.getDrawable(R.drawable.expander_ic_minimized),
                            false,
                            HierarchyEntryType.collapsed, fc.getIndex());
            formList.add(group);
        }
    }

    private int addRepeatChildren() {
        int event = FormEntryActivity.mFormController.getEvent();
        FormIndex firstRepeatChildIndex = FormEntryActivity.mFormController.getFormIndex();

        while (event != FormEntryController.EVENT_END_OF_FORM) {
            if (event == FormEntryController.EVENT_REPEAT && isCurrentIndexIsSiblingOf(firstRepeatChildIndex)) {
                addRepeatChild();
            } else if (!isCurrentIndexOutsideOfGroup(firstRepeatChildIndex)) {
                return event;
            }
            event = FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_OVER_GROUP);
        }
        return event;
    }

    private boolean isCurrentIndexIsSiblingOf(FormIndex enclosingIndex) {
        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();
        return FormIndex.areSiblings(enclosingIndex, currentIndex);
    }

    private boolean isCurrentIndexOutsideOfGroup(FormIndex enclosingIndex) {
        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();
        return FormIndex.overlappingLocalIndexesMatch(enclosingIndex, currentIndex);
    }


    private void addRepeatChild() {
        // Add this group name to the drop down list for this repeating group.
        HierarchyElement h = formList.get(formList.size() - 1);
        String mIndent = "     ";
        FormEntryCaption fc = FormEntryActivity.mFormController.getCaptionPrompt();
        h.addChild(new HierarchyElement(context, mIndent + fc.getLongText() + " "
                + (fc.getMultiplicity() + 1), null, null, false, HierarchyEntryType.child, fc
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
