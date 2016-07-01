package org.commcare.activities.components;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logic.FormController;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.utils.MarkupUtil;
import org.commcare.views.ClippingFrame;
import org.commcare.views.QuestionsView;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.widgets.QuestionWidget;
import org.javarosa.xpath.XPathException;

import java.util.ArrayList;

public class FormNavigationUI {
    /**
     * Update progress bar's max and value, and the various buttons and navigation cues
     * associated with navigation
     */
    public static void updateNavigationCues(CommCareActivity activity,
                                            FormController formController,
                                            QuestionsView view) {
        if (view == null) {
            return;
        }

        updateFloatingLabels(activity, view);

        FormNavigationController.NavigationDetails details;
        try {
            details = FormNavigationController.calculateNavigationStatus(formController, view);
        } catch (XPathException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(activity, e, true);
            return;
        }

        ProgressBar progressBar = (ProgressBar)activity.findViewById(R.id.nav_prog_bar);

        ImageButton nextButton = (ImageButton)activity.findViewById(R.id.nav_btn_next);
        ImageButton prevButton = (ImageButton)activity.findViewById(R.id.nav_btn_prev);

        ClippingFrame finishButton = (ClippingFrame)activity.
                findViewById(R.id.nav_btn_finish);

        if (!details.relevantBeforeCurrentScreen) {
            prevButton.setImageResource(R.drawable.icon_close_darkwarm);
            prevButton.setTag(FormEntryActivity.NAV_STATE_QUIT);
        } else {
            prevButton.setImageResource(R.drawable.icon_chevron_left_brand);
            prevButton.setTag(FormEntryActivity.NAV_STATE_BACK);
        }

        //Apparently in Android 2.3 setting the drawable resource for the progress bar
        //causes it to lose it bounds. It's a bit cheaper to keep them around than it
        //is to invalidate the view, though.
        Rect bounds = progressBar.getProgressDrawable().getBounds(); //Save the drawable bound

        Log.i("Questions", "Total questions: " + details.totalQuestions + " | Completed questions: " + details.completedQuestions);

        progressBar.setMax(details.totalQuestions);

        if (details.isFormDone()) {
            setDoneState(nextButton, activity, finishButton, details, progressBar);
        } else {
            setMoreQuestionsState(nextButton, activity, finishButton, details, progressBar);
        }

        progressBar.getProgressDrawable().setBounds(bounds);  //Set the bounds to the saved value
    }

    private static void setDoneState(ImageButton nextButton,
                                     Context context,
                                     final ClippingFrame finishButton,
                                     FormNavigationController.NavigationDetails details,
                                     ProgressBar progressBar) {
        if (nextButton.getTag() == null) {
            setFinishVisible(finishButton);
        } else if (!FormEntryActivity.NAV_STATE_DONE.equals(nextButton.getTag())) {
            nextButton.setTag(FormEntryActivity.NAV_STATE_DONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                expandAndShowFinishButton(context, finishButton);
            } else {
                setFinishVisible(finishButton);
            }
        }

        progressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.progressbar_full));
        progressBar.setProgress(details.totalQuestions);

        Log.i("Questions", "Form complete");
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void expandAndShowFinishButton(Context context,
                                                  final ClippingFrame finishButton) {

        Animator animator = AnimatorInflater.loadAnimator(context, R.animator.grow_in_visible);

        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                finishButton.setVisibility(View.VISIBLE);
                finishButton.setClipHeight(0);
                finishButton.setClipWidth(0);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                finishButton.setClipHeight(1);
                finishButton.setClipWidth(1);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.setTarget(finishButton);
        animator.start();
    }

    private static void setMoreQuestionsState(ImageButton nextButton,
                                              Context context,
                                              ClippingFrame finishButton,
                                              FormNavigationController.NavigationDetails details,
                                              ProgressBar progressBar) {
        if (!FormEntryActivity.NAV_STATE_NEXT.equals(nextButton.getTag())) {
            nextButton.setTag(FormEntryActivity.NAV_STATE_NEXT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                finishButton.setVisibility(View.GONE);
            }
        }

        progressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.progressbar_modern));
        progressBar.setProgress(details.completedQuestions);
    }

    public static void animateFinishArrow(final CommCareActivity activity) {
        final View coverView = activity.findViewById(R.id.form_entry_cover);

        Animation growShrinkAnimation = AnimationUtils.loadAnimation(activity, R.anim.grow_shrink);
        growShrinkAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                coverView.setVisibility(View.VISIBLE);
                activity.setMainScreenBlocked(true);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                coverView.setVisibility(View.GONE);
                activity.setMainScreenBlocked(false);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        View finishButton = activity.findViewById(R.id.nav_image_finish);
        finishButton.startAnimation(growShrinkAnimation);
    }

    private static void setFinishVisible(ClippingFrame finishButton) {
        finishButton.setVisibility(View.VISIBLE);
        finishButton.setClipWidth(1);
        finishButton.setClipHeight(1);
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

    private static void updateFloatingLabels(CommCareActivity activity,
                                             QuestionsView currentView) {
        //TODO: this should actually be set up to scale per screen size.
        ArrayList<Pair<CharSequence, FloatingLabel>> smallLabels = new ArrayList<>();
        ArrayList<Pair<CharSequence, FloatingLabel>> largeLabels = new ArrayList<>();

        FloatingLabel[] labelTypes = FloatingLabel.values();

        for (QuestionWidget widget : currentView.getWidgets()) {
            String hint = widget.getPrompt().getAppearanceHint();
            if (hint == null) {
                continue;
            }
            for (FloatingLabel type : labelTypes) {
                if (type.getAppearance().equals(hint)) {
                    CharSequence widgetText = widget.getPrompt().getQuestionText();
                    String markdownWidgetText = widget.getPrompt().getMarkdownText();
                    if (markdownWidgetText != null) {
                        widgetText = MarkupUtil.returnMarkdown(activity, markdownWidgetText);
                    }
                    if (widgetText != null && widgetText.length() < 15) {
                        smallLabels.add(new Pair<>(widgetText, type));
                    } else {
                        largeLabels.add(new Pair<>(widgetText, type));
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
