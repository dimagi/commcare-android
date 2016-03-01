package org.commcare.activities;

import android.app.ActionBar;
import android.app.ListActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.adapters.HierarchyListAdapter;
import org.commcare.android.framework.SessionActivityRegistration;
import org.commcare.android.logging.XPathErrorLogger;
import org.commcare.dalvik.R;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xpath.XPathArityException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.odk.collect.android.logic.FormHierarchyBuilder;
import org.odk.collect.android.logic.HierarchyElement;
import org.odk.collect.android.logic.HierarchyEntryType;

import java.util.ArrayList;
import java.util.List;

public class FormHierarchyActivity extends ListActivity {
    private Button jumpPreviousButton;
    private List<HierarchyElement> formList;
    private TextView mPath;
    private FormIndex mStartIndex;
    public final static int RESULT_XPATH_ERROR = RESULT_FIRST_USER + 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hierarchy_layout);

        addActionBarBackArrow();

        // We use a static FormEntryController to make jumping faster.
        mStartIndex = FormEntryActivity.mFormController.getFormIndex();

        setTitle(Localization.get("form.hierarchy"));

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
            }
        });

        refreshView();
    }

    private void addActionBarBackArrow() {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            ActionBar bar = getActionBar();
            if (bar != null) {
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

    private void refreshView() {
        // Record the current index so we can return to the same place if the user hits 'back'.
        FormIndex currentIndex = FormEntryActivity.mFormController.getFormIndex();

        formList = new ArrayList<>();

        String hierarchyPath;
        try {
            hierarchyPath = FormHierarchyBuilder.populateHierarchyList(this, formList);
        } catch (XPathTypeMismatchException | XPathArityException e) {
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(e);

            final String errorMsg = "Encounted xpath error: " + e.getMessage();
            // TODO PLM: show blocking dialog with error; requires
            // making this implement DialogController & use Fragments
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            setResult(RESULT_XPATH_ERROR);
            finish();
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
