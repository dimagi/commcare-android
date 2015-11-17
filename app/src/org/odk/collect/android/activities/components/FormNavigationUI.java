package org.odk.collect.android.activities.components;

import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.UserfacingErrorHandling;
import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.views.ODKView;
import org.odk.collect.android.widgets.QuestionWidget;

import java.util.ArrayList;

public class FormNavigationUI {
    /**
     * Update progress bar's max and value, and the various buttons and navigation cues
     * associated with navigation
     *
     * @param view ODKView to update
     */
    public static void updateNavigationCues(CommCareActivity activity, FormController formController, View view) {
        updateFloatingLabels(activity, formController, view);

        FormNavigationController.NavigationDetails details;
        try {
            details = FormNavigationController.calculateNavigationStatus(formController, view);
        } catch (XPathTypeMismatchException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(activity, e, true);
            return;
        }

        ProgressBar progressBar = (ProgressBar)activity.findViewById(R.id.nav_prog_bar);

        ImageButton nextButton = (ImageButton)activity.findViewById(R.id.nav_btn_next);
        ImageButton prevButton = (ImageButton)activity.findViewById(R.id.nav_btn_prev);

        if (!details.relevantBeforeCurrentScreen) {
            prevButton.setImageResource(R.drawable.icon_close_darkwarm);
            prevButton.setTag("quit");
        } else {
            prevButton.setImageResource(R.drawable.icon_chevron_left_brand);
            prevButton.setTag("back");
        }

        //Apparently in Android 2.3 setting the drawable resource for the progress bar
        //causes it to lose it bounds. It's a bit cheaper to keep them around than it
        //is to invalidate the view, though.
        Rect bounds = progressBar.getProgressDrawable().getBounds(); //Save the drawable bound

        Log.i("Questions", "Total questions: " + details.totalQuestions + " | Completed questions: " + details.completedQuestions);

        progressBar.setMax(details.totalQuestions);

        if (details.relevantAfterCurrentScreen == 0 && (details.requiredOnScreen == details.answeredOnScreen || details.requiredOnScreen < 1)) {
            nextButton.setImageResource(R.drawable.icon_chevron_right_attnpos);

            //TODO: _really_? This doesn't seem right
            nextButton.setTag("done");

            progressBar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.progressbar_full));

            Log.i("Questions", "Form complete");
            // if we get here, it means we don't have any more relevant questions after this one, so we mark it as complete
            progressBar.setProgress(details.totalQuestions); // completely fills the progressbar
        } else {
            nextButton.setImageResource(R.drawable.icon_chevron_right_brand);

            //TODO: _really_? This doesn't seem right
            nextButton.setTag(FormEntryActivity.NAV_STATE_NEXT);

            progressBar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.progressbar_modern));

            progressBar.setProgress(details.completedQuestions);
        }

        progressBar.getProgressDrawable().setBounds(bounds);  //Set the bounds to the saved value
    }

    enum FloatingLabel {
        good("floating-good", R.drawable.label_floating_good, R.color.cc_attention_positive_text),
        caution("floating-caution", R.drawable.label_floating_caution, R.color.cc_light_warm_accent_color),
        bad("floating-bad", R.drawable.label_floating_bad, R.color.cc_attention_negative_color);

        final String label;
        final int resourceId;
        final int colorId;

        FloatingLabel(String label, int resourceId, int colorId) {
            this.label = label;
            this.resourceId = resourceId;
            this.colorId = colorId;
        }

        public String getAppearance() {
            return label;
        }
    }

    private static void updateFloatingLabels(CommCareActivity activity, FormController formController, View currentView) {
        //TODO: this should actually be set up to scale per screen size.
        ArrayList<Pair<String, FloatingLabel>> smallLabels = new ArrayList<>();
        ArrayList<Pair<String, FloatingLabel>> largeLabels = new ArrayList<>();

        FloatingLabel[] labelTypes = FloatingLabel.values();

        if (currentView instanceof ODKView) {
            for (QuestionWidget widget : ((ODKView)currentView).getWidgets()) {
                String hint = widget.getPrompt().getAppearanceHint();
                if (hint == null) {
                    continue;
                }
                for (FloatingLabel type : labelTypes) {
                    if (type.getAppearance().equals(hint)) {
                        String widgetText = widget.getPrompt().getQuestionText();
                        if (widgetText != null && widgetText.length() < 15) {
                            smallLabels.add(new Pair<>(widgetText, type));
                        } else {
                            largeLabels.add(new Pair<>(widgetText, type));
                        }
                    }
                }
            }
        }

        final ViewGroup parent = (ViewGroup)activity.findViewById(R.id.form_entry_label_layout);
        parent.removeAllViews();

        int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics());

        int minHeight = 7 * pixels;

        //Ok, now go ahead and add all of the small labels
        for (int i = 0; i < smallLabels.size(); i = i + 2) {
            if (i + 1 < smallLabels.size()) {
                LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setLayoutParams(lpp);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
                TextView left = (TextView)View.inflate(activity, R.layout.component_floating_label, null);
                left.setLayoutParams(lp);
                left.setText(smallLabels.get(i).first + "; " + smallLabels.get(i + 1).first);
                left.setBackgroundResource(smallLabels.get(i).second.resourceId);
                left.setPadding(pixels, 2 * pixels, pixels, 2 * pixels);
                left.setTextColor(smallLabels.get(i).second.colorId);
                left.setMinimumHeight(minHeight);
                layout.addView(left);

                parent.addView(layout);
            } else {
                largeLabels.add(smallLabels.get(i));
            }
        }
        for (int i = 0; i < largeLabels.size(); ++i) {
            final TextView view = (TextView)View.inflate(activity, R.layout.component_floating_label, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(lp);
            view.setPadding(pixels, 2 * pixels, pixels, 2 * pixels);
            view.setText(largeLabels.get(i).first);
            view.setBackgroundResource(largeLabels.get(i).second.resourceId);
            view.setTextColor(largeLabels.get(i).second.colorId);
            view.setMinimumHeight(minHeight);
            parent.addView(view);
        }
    }
}
