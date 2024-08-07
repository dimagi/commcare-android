package org.commcare.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.AlertDialogFragment;

import org.commcare.adapters.HierarchyListAdapter;
import org.commcare.dalvik.R;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.logic.FormHierarchyBuilder;
import org.commcare.logic.HierarchyElement;
import org.commcare.logic.HierarchyEntryType;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xpath.XPathException;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CommCareAlertDialog;
import androidx.fragment.app.DialogFragment;
import org.commcare.views.dialogs.AlertDialogController;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.ActionBar;

public class FormHierarchyActivity extends SessionAwareListActivity implements AlertDialogController {
    private Button jumpPreviousButton;
    private List<HierarchyElement> formList;
    private TextView mPath;

    public final static String ERROR_DIALOG = "error-dialog";

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        if (FormEntryActivity.mFormController == null) {
            throw new SessionUnavailableException(
                    "Resuming form hierarchy view after process was killed. Form state is unrecoverable.");
        }

        FormEntryActivity.mFormController.storeFormIndexToReturnTo();

        addActionBarBackArrow();

        setTitle(Localization.get("form.hierarchy"));

        mPath = findViewById(R.id.pathtext);

        jumpPreviousButton = findViewById(R.id.jumpPreviousButton);
        jumpPreviousButton.setOnClickListener(v -> goUpLevel());

        Button jumpBeginningButton = findViewById(R.id.jumpBeginningButton);
        jumpBeginningButton.setOnClickListener(v -> {
            FormEntryActivity.mFormController.jumpToIndex(FormIndex
                    .createBeginningOfFormIndex());
            setResult(RESULT_OK);
            finish();
        });

        Button jumpEndButton = findViewById(R.id.jumpEndButton);
        jumpEndButton.setOnClickListener(v -> {
            FormEntryActivity.mFormController.jumpToIndex(FormIndex.createEndOfFormIndex());
            setResult(RESULT_OK);
            finish();
        });

        // We use a static FormEntryController to make jumping faster.
        final FormIndex mStartIndex = FormEntryActivity.mFormController.getFormIndex();

        // kinda slow, but works.
        // this scrolls to the last question the user was looking at
        getListView().post(() -> {
            int position = 0;
            ListAdapter adapter = getListAdapter();
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
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

    @Override
    public int getLayoutResource() {
        return R.layout.hierarchy_layout;
    }

    private void addActionBarBackArrow() {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayShowHomeEnabled(true);
            bar.setDisplayHomeAsUpEnabled(true);
        }
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

    public static String getCurrentPath() {
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

    @Override
    public void showAlertDialog(CommCareAlertDialog dialog) {
        AlertDialogFragment.fromCommCareAlertDialog(dialog)
                .show(getSupportFragmentManager(), ERROR_DIALOG);
    }

    @Override
    public void dismissAlertDialog() {
        DialogFragment alertDialog = getCurrentAlertDialog();
        if (alertDialog != null) {
            alertDialog.dismiss();
        }
    }

    public AlertDialogFragment getCurrentAlertDialog() {
        return (AlertDialogFragment)getSupportFragmentManager().
                findFragmentByTag(ERROR_DIALOG);
    }

    private void refreshView() {
        // Record the current index so we can return to the same place if the user hits 'back'.
        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();

        formList = new ArrayList<>();

        String hierarchyPath;
        try {
            hierarchyPath = FormHierarchyBuilder.populateHierarchyList(this, formList);
        } catch (XPathException e) {
            new UserfacingErrorHandling().logErrorAndShowDialog(this, e, true);
            return;
        }

        setGoUpButton(hierarchyPath);

        HierarchyListAdapter itla = new HierarchyListAdapter(this);
        itla.setListItems(formList);
        setListAdapter(itla);

        // set the controller back to the current index in case the user hits 'back'
        FormEntryActivity.mFormController.jumpToIndex(currentIndex);
    }

    private void setGoUpButton(String hierarchyPath) {
        if ("".equals(hierarchyPath)) {
            mPath.setVisibility(View.GONE);
            jumpPreviousButton.setEnabled(false);
            jumpPreviousButton.setTextColor(getResources().getColor(R.color.edit_text_color));
        } else {
            mPath.setVisibility(View.VISIBLE);
            mPath.setText(hierarchyPath);
            jumpPreviousButton.setEnabled(true);
            jumpPreviousButton.setTextColor(getResources().getColor(R.color.cc_brand_color));
        }
    }

    /**
     * used to go up one level in the formIndex. That is, if you're at 5_0, 1 (the second question
     * in a repeating group), this method will return a FormInex of 5_0 (the start of the repeating
     * group). If your at index 16 or 5_0, this will return null;
     */
    public static FormIndex stepIndexOut(FormIndex index) {
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
            case expanded:
                h.setType(HierarchyEntryType.collapsed);
                ArrayList<HierarchyElement> children = h.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    formList.remove(position + 1);
                }
                h.setIcon(getResources().getDrawable(R.drawable.expander_ic_minimized));
                break;
            case collapsed:
                h.setType(HierarchyEntryType.expanded);
                ArrayList<HierarchyElement> children1 = h.getChildren();
                for (int i = 0; i < children1.size(); i++) {
                    formList.add(position + 1 + i, children1.get(i));
                }
                h.setIcon(getResources().getDrawable(R.drawable.expander_ic_maximized));
                break;
            case question:
                FormEntryActivity.mFormController.jumpToIndex(h.getFormIndex());
                setResult(RESULT_OK);
                finish();
                return;
            case child:
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
        FormEntryActivity.mFormController.returnToStoredIndex();
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!isFinishing()) {
                this.onBackPressed();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
