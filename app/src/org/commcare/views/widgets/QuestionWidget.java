package org.commcare.views.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.WidgetChangedListener;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.FormEntryPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.BlockingActionsManager;
import org.commcare.utils.DelayedBlockingAction;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.MarkupUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.ShrinkingTextView;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.ViewUtil;
import org.commcare.views.media.MediaLayout;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.core.model.QuestionExtensionReceiver;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.XPathException;

import java.io.File;
import java.util.Vector;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import javax.annotation.Nullable;

public abstract class QuestionWidget extends LinearLayout implements QuestionExtensionReceiver {
    private final static String TAG = QuestionWidget.class.getSimpleName();

    private final LinearLayout.LayoutParams mLayout;
    protected final FormEntryPrompt mPrompt;
    private final boolean mCompact;

    protected final int mQuestionFontSize;
    protected final int mAnswerFontSize;
    protected final static String ACQUIREFIELD = "acquire";

    //the height of the "Frame" available to this widget. The frame
    //is the size of the parent that is available (it is roughly
    //the window without the keyboard/top bars/etc.)
    //Note that this value is only populated after the widget is
    //drawn for now.
    private int mFrameHeight = -1;

    protected TextView mQuestionText;
    private FrameLayout helpPlaceholder;

    @Nullable
    private ShrinkingTextView mHintText;
    private View warningView;

    //Whether this question widget needs to request focus on
    //its next draw, due to a new element having been added (which couldn't have
    //requested focus yet due to having not been layed out)
    private boolean focusPending = false;

    protected WidgetChangedListener widgetChangedListener;
    protected BlockingActionsManager blockingActionsManager;

    public boolean hintTextNeedsHeightSpec = false;
    private LinearLayout compactLayout;

    public QuestionWidget(Context context, FormEntryPrompt p) {
        this(context, p, false);
    }

    public QuestionWidget(Context context, FormEntryPrompt p, boolean inCompactGroup) {
        super(context);
        mPrompt = p;
        mCompact = inCompactGroup;

        //this is pretty sketch but is the only way to make the required background to work trivially for now
        this.setClipToPadding(false);

        this.setOnClickListener(v -> QuestionWidget.this.acceptFocus());

        mQuestionFontSize = FormEntryPreferences.getQuestionFontSize();
        mAnswerFontSize = mQuestionFontSize + 2;

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.TOP);

        //TODO: This whole view should probably be inflated somehow 
        int padding = this.getResources().getDimensionPixelSize(R.dimen.question_widget_side_padding);
        setPadding(padding, 8, padding, 8);

        mLayout = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        addQuestionText();
        if (!isInCompactMode()) {
            addHelpPlaceholder();
            addHintText();
        }
    }

    protected void acceptFocus() {
    }

    private void addHelpPlaceholder() {
        if (!mPrompt.hasHelp()) {
            return;
        }

        helpPlaceholder = new FrameLayout(this.getContext());
        helpPlaceholder.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        final ImageButton trigger = new ImageButton(getContext());
        trigger.setScaleType(ScaleType.FIT_CENTER);
        trigger.setImageResource(R.drawable.icon_info_outline_lightcool);
        trigger.setBackgroundDrawable(null);
        trigger.setOnClickListener(v -> {
            trigger.setImageResource(R.drawable.icon_info_fill_lightcool);
            fireHelpText(() -> {
                // back to the old icon
                trigger.setImageResource(R.drawable.icon_info_outline_lightcool);
            });
        });
        trigger.setId(847294011);
        LinearLayout triggerLayout = new LinearLayout(getContext());
        triggerLayout.setOrientation(LinearLayout.HORIZONTAL);
        triggerLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        triggerLayout.setGravity(Gravity.RIGHT);
        triggerLayout.addView(trigger);

        MediaLayout helpLayout = createHelpLayout();
        helpLayout.setBackgroundResource(R.color.very_light_blue);
        helpPlaceholder.addView(helpLayout);

        this.addView(triggerLayout);
        this.addView(helpPlaceholder);
        helpPlaceholder.setVisibility(View.GONE);
    }

    public FormEntryPrompt getPrompt() {
        return mPrompt;
    }

    // Abstract methods
    public abstract IAnswerData getAnswer();

    public abstract void clearAnswer();

    public abstract void setFocus(Context context);

    @Override
    public abstract void setOnLongClickListener(OnLongClickListener l);

    @Override
    public void applyExtension(QuestionDataExtension extension) {
        // Intentionally empty method body -- subclasses of QuestionWidget that expect
        // to ever receive an extension should override this method and implement it accordingly
    }

    public void notifyPermission(String permission, boolean permissionGranted) {
        // Intentionally empty method body -- subclasses of QuestionWidget requesting any permissions
        // should override this method and implement it accordingly
    }

    private class URLSpanNoUnderline extends URLSpan {
        public URLSpanNoUnderline(String url) {
            super(url);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }

    /**
     * Add notification (e.g., validation error) to this question.
     *
     * @param text         Text of message.
     * @param strong       If true, display a visually stronger, negative background.
     * @param requestFocus If true, bring focus to this question.
     */
    private void notifyOnScreen(String text, boolean strong, boolean requestFocus) {
        int warningBackdropId;
        if (strong) {
            warningBackdropId = R.drawable.strong_warning_backdrop;
        } else {
            warningBackdropId = R.drawable.weak_warning_backdrop;
        }
        ViewUtil.setBackgroundRetainPadding(this,
                this.getContext().getResources().getDrawable(warningBackdropId));

        if (this.warningView == null) {
            // note: this is lame, but we bleed out the margins on the left and right here to make this overlap.
            // We could accomplish the same thing by having two backgrounds, one for the widget as a whole, and 
            // one for the internals (or splitting up the layout), but this'll do for now 
            this.warningView = View.inflate(this.getContext(), R.layout.question_warning_text_view, this).findViewById(R.id.warning_root);

            focusPending = requestFocus;
        } else {
            if (this.warningView.getVisibility() != View.VISIBLE) {
                this.warningView.setVisibility(View.VISIBLE);
                focusPending = requestFocus;
            }
        }
        TextView messageView = this.warningView.findViewById(R.id.message);
        messageView.setText(text);

        //If the warningView already exists, we can just scroll to it right now
        //if not, we actually have to do it later, when we lay this all back out
        if (!focusPending && requestFocus) {
            requestChildViewOnScreen(messageView);
        }
    }

    private void notifyWarning(String text) {
        notifyOnScreen(text, false, true);
    }

    public void notifyInvalid(String text, boolean requestFocus) {
        notifyOnScreen(text, true, requestFocus);
    }

    protected void checkForOversizedMedia(IAnswerData widgetAnswer) {
        if (widgetAnswer instanceof InvalidData) {
            String fileSizeString = widgetAnswer.getValue() + "";
            showOversizedMediaWarning(fileSizeString);
        }
    }

    private void showOversizedMediaWarning(String fileSizeString) {
        String maxAcceptable = FileUtil.bytesToMeg(FormUploadUtil.MAX_BYTES) + "";
        String[] args = new String[]{fileSizeString, maxAcceptable};
        notifyInvalid(StringUtils.getStringRobust(getContext(), R.string.attachment_above_size_limit, args), true);
    }

    /**
     * Use to signal that there's a portion of this view that wants to be
     * visible to the user on the screen. This method will place the sub
     * view on the screen, and will also place as much of this view as possible
     * on the screen. If this view is smaller than the viewable area available, it
     * will be fully visible in addition to the subview.
     */
    private void requestChildViewOnScreen(View child) {
        //Take focus so the user can be prepared to interact with this question, since
        //they will need to be fixing the input
        acceptFocus();

        //Get the rectangle that wants to put itself on the screen
        Rect vitalPortion = new Rect();
        child.getDrawingRect(vitalPortion);

        //Save a reference to it in case we have to manipulate it later.
        Rect vitalPortionSaved = new Rect();
        child.getDrawingRect(vitalPortionSaved);

        //Then get the bounding rectangle for this whole view.
        Rect wholeView = new Rect();
        this.getDrawingRect(wholeView);

        //If we don't know enough about the screen, just default to asking to see the
        //subview that was requested.
        if (mFrameHeight == -1) {
            child.requestRectangleOnScreen(vitalPortion);
            return;
        }

        //If the whole view fits, just request that we display the whole thing.
        if (wholeView.height() < mFrameHeight) {
            this.requestRectangleOnScreen(wholeView);
            return;
        }

        //The whole view will not fit, we need to scale down our requested focus.
        //Trying to construct the "ideal" rectangle here is actually pretty hard
        //but the base case is just to see if we can get the view onto the screen from
        //the bottom or the top

        int topY = wholeView.top;
        int bottomY = wholeView.bottom;

        //shrink the view to contain only the current frame size.
        wholeView.inset(0, (wholeView.height() - mFrameHeight) / 2);
        wholeView.offsetTo(wholeView.left, topY);

        //The view is now the size of the frame and anchored back at the top. 

        //Now let's contextualize where the child view actually is in this frame.
        this.offsetDescendantRectToMyCoords(child, vitalPortion);

        //If the newly transformed view now contains the child portion, we're good
        if (wholeView.contains(vitalPortion)) {
            this.requestRectangleOnScreen(wholeView);
            return;
        }

        //otherwise, move to the requested frame to be at the bottom of this view
        wholeView.offsetTo(wholeView.left, bottomY - wholeView.height());

        //now see if the transformed view contains the vital portion
        if (wholeView.contains(vitalPortion)) {
            this.requestRectangleOnScreen(wholeView);
            return;
        }

        //Otherwise the child is hidden in the frame, so it won't matter which
        //we choose.
        child.requestRectangleOnScreen(vitalPortionSaved);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        //If we're coming back in after we just laid out adding a new element that needs
        //focus, we can now scroll to it, since it's actually had its spacing declared.
        if (changed && focusPending) {
            focusPending = false;
            if (this.warningView != null) {
                TextView messageView = this.warningView.findViewById(R.id.message);
                requestChildViewOnScreen(messageView);
            }
        }
    }

    private void stripUnderlines(TextView textView) {
        Spannable s = (Spannable)textView.getText();
        URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            s.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL());
            s.setSpan(span, start, end, 0);
        }
        textView.setText(s);
    }

    public void setQuestionText(TextView textView, FormEntryPrompt prompt) {
        setTextFromPrompt(textView, prompt);
        if (prompt.getMarkdownText() != null) {
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            // Wrap to the size of the parent view
            textView.setHorizontallyScrolling(false);
        }
    }

    private void setTextFromPrompt(TextView textView, FormEntryPrompt prompt) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        if (prompt.getMarkdownText() != null) {
            builder.append(prompt.getMarkdownText());
        } else if (mPrompt.getLongText() != null) {
            builder.append(mPrompt.getLongText());
        }

        SpannableStringBuilder asteriskBuilder = new SpannableStringBuilder();
        if (HiddenPreferences.shouldLabelRequiredQuestionsWithAsterisk() && prompt.isRequired()) {
            asteriskBuilder.append(" ");
            int start = asteriskBuilder.length();
            asteriskBuilder.append("*");
            int end = asteriskBuilder.length();
            asteriskBuilder.setSpan(new ForegroundColorSpan(Color.RED), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (prompt.getMarkdownText() != null) {
            MarkupUtil.setMarkdown(textView, builder, asteriskBuilder);
        } else {
            textView.setText(builder.append(asteriskBuilder));
        }
    }

    public void setChoiceText(TextView choiceText, SelectChoice choice) {
        String markdownText = mPrompt.getSelectItemMarkdownText(choice);
        if (markdownText != null) {
            choiceText.setText(forceMarkdown(markdownText));
        } else {
            choiceText.setText(mPrompt.getSelectChoiceText(choice));
        }
    }

    /**
     * Add a Views containing the question text, audio (if applicable), and image (if applicable).
     * To satisfy the RelativeLayout constraints, we add the audio first if it exists, then the
     * TextView to fit the rest of the space, then the image if applicable.
     */
    protected void addQuestionText() {
        mQuestionText = (TextView)LayoutInflater.from(getContext()).inflate(getQuestionTextLayout(), this, false);
        mQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontSize);
        mQuestionText.setId(38475483); // assign random id

        setQuestionText(mQuestionText, mPrompt);

        if (mPrompt.getLongText() != null) {
            if (mPrompt.getLongText().contains("\u260E")) {
                if (Linkify.addLinks(mQuestionText, Linkify.PHONE_NUMBERS)) {
                    stripUnderlines(mQuestionText);
                } else {
                    Log.d(TAG, "this should be an error I'm thinking?");
                }
            }
        }

        if (mPrompt.getLongText() == null) {
            mQuestionText.setVisibility(GONE);
        }

        if (isInCompactMode()) {
            addToCompactLayout(mQuestionText);
        } else {
            // Create the layout for audio, image, text
            String imageURI = mPrompt.getImageText();
            String audioURI = mPrompt.getAudioText();
            String videoURI = mPrompt.getSpecialFormQuestionText("video");
            String inlineVideoUri = mPrompt.getSpecialFormQuestionText("video-inline");
            String qrCodeContent = mPrompt.getSpecialFormQuestionText("qrcode");
            // shown when image is clicked
            String bigImageURI = mPrompt.getSpecialFormQuestionText("big-image");
            String ttsText = mPrompt.getSpecialFormQuestionText("tts");
            if (MainConfigurablePreferences.isTTSEnabled() && ttsText == null) {
                ttsText = mPrompt.getLongText();
            }

            MediaLayout mediaLayout = MediaLayout.buildComprehensiveLayout(getContext(), mQuestionText, audioURI, imageURI, videoURI, bigImageURI, qrCodeContent, inlineVideoUri, ttsText, mPrompt.getIndex().hashCode());
            addView(mediaLayout, mLayout);
        }
    }

    protected void addToCompactLayout(View view) {
        if (compactLayout == null) {
            compactLayout = new LinearLayout(getContext());
            compactLayout.setOrientation(HORIZONTAL);
            compactLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            addView(compactLayout);
        }
        compactLayout.addView(view);
    }

    private int getQuestionTextLayout() {
        if (isInCompactMode()) {
            return R.layout.question_widget_text_compact;
        }
        return R.layout.question_widget_text;
    }

    /**
     * Display extra help, triggered by user request.
     */
    private void fireHelpText(final Runnable r) {
        if (!mPrompt.hasHelp()) {
            return;
        }

        // Depending on ODK setting, help may be displayed either as
        // a dialog or inline, underneath the question text

        if (showHelpWithDialog()) {
            AlertDialog mAlertDialog = new AlertDialog.Builder(this.getContext()).create();
            ScrollView scrollView = new ScrollView(this.getContext());
            scrollView.addView(createHelpLayout());
            mAlertDialog.setView(scrollView);

            DialogInterface.OnClickListener errorListener = (dialog, i) -> {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        dialog.cancel();
                        if (r != null) r.run();
                        break;
                }
            };
            mAlertDialog.setCancelable(true);
            mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                    StringUtils.getStringSpannableRobust(this.getContext(), R.string.ok),
                    errorListener);
            mAlertDialog.show();
        } else {

            if (helpPlaceholder.getVisibility() == View.GONE) {
                expand(helpPlaceholder);
            } else {
                collapse(helpPlaceholder);
            }
        }
    }

    private boolean showHelpWithDialog() {
        return !PreferenceManager.getDefaultSharedPreferences(this.getContext().getApplicationContext()).
                getBoolean(FormEntryPreferences.KEY_HELP_MODE_TRAY, false);
    }

    /**
     * Build MediaLayout for displaying any help associated with given FormEntryPrompt.
     */
    private MediaLayout createHelpLayout() {
        TextView text = new TextView(getContext());

        String markdownText = mPrompt.getHelpMultimedia(FormEntryCaption.TEXT_FORM_MARKDOWN);

        if (markdownText != null) {
            text.setText(forceMarkdown(markdownText));
            text.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            try {
                text.setText(mPrompt.getHelpText());
            } catch (XPathException exception) {
                new UserfacingErrorHandling<>().createErrorDialog((CommCareActivity)getContext(), exception.getLocalizedMessage(), true);
            }
        }
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontSize);
        int padding = (int)getResources().getDimension(R.dimen.help_text_padding);
        text.setPadding(0, 0, 0, 7);
        text.setId(38475483); // assign random id

        MediaLayout helpLayout = MediaLayout.buildAudioImageVisualLayout(getContext(), text,
                mPrompt.getHelpMultimedia(FormEntryCaption.TEXT_FORM_AUDIO),
                mPrompt.getHelpMultimedia(FormEntryCaption.TEXT_FORM_IMAGE),
                mPrompt.getHelpMultimedia(FormEntryCaption.TEXT_FORM_VIDEO),
                null);
        helpLayout.setPadding(padding, padding, padding, padding);

        return helpLayout;
    }

    private static void expand(final View v) {
        v.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? LayoutParams.WRAP_CONTENT
                        : (int)(targetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(targetHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    private static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    public void updateFrameSize(int height) {
        mFrameHeight = height;
    }

    public void updateHintHeight(int maxHintHeight) {
        if (mHintText != null) {
            mHintText.updateMaxHeight(maxHintHeight);
        }
        hintTextNeedsHeightSpec = false;
    }

    /**
     * Add a TextView containing the help text.
     */
    private void addHintText() {
        String s = null;
        try {
            s = mPrompt.getHintText();
        } catch (XPathException e) {
            new UserfacingErrorHandling<>().createErrorDialog((CommCareActivity)getContext(), e.getLocalizedMessage(), true);
        }
        if (s != null && !s.equals("")) {
            mHintText = new ShrinkingTextView(getContext(), this.getMaxHintHeight());
            mHintText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontSize - 3);
            mHintText.setPadding(0, -5, 0, 7);
            // wrap to the widget of view
            mHintText.setHorizontallyScrolling(false);
            mHintText.setText(s);
            mHintText.setTypeface(null, Typeface.ITALIC);

            addView(mHintText, mLayout);
        }
    }

    private int getMaxHintHeight() {
        if (mFrameHeight != -1) {
            return mFrameHeight / 4;
        } else {
            hintTextNeedsHeightSpec = true;
            return -1;
        }
    }

    /**
     * Every subclassed widget should override this, adding any views they may contain, and calling
     * super.cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        if (mQuestionText != null) {
            mQuestionText.cancelLongPress();
        }
        if (mHintText != null) {
            mHintText.cancelLongPress();
        }
    }

    protected IAnswerData getCurrentAnswer() {
        IAnswerData current = mPrompt.getAnswerValue();
        if (current == null) {
            return null;
        }
        return getTemplate().cast(current.uncast());
    }

    private IAnswerData getTemplate() {
        return AnswerDataFactory.template(mPrompt.getControlType(), mPrompt.getDataType());
    }

    public void hideHintText() {
        if (mHintText != null) {
            mHintText.setVisibility(View.GONE);
        }
    }

    public FormIndex getFormId() {
        return mPrompt.getIndex();
    }

    public void setChangedListeners(WidgetChangedListener wcl,
                                    BlockingActionsManager blockingActionsManager) {
        widgetChangedListener = wcl;
        this.blockingActionsManager = blockingActionsManager;
    }

    protected void fireDelayed(DelayedBlockingAction delayedBlockingAction) {
        if (this.blockingActionsManager != null) {
            blockingActionsManager.queue(delayedBlockingAction);
        }
    }

    protected void widgetEntryChangedDelayed() {
        fireDelayed(new DelayedBlockingAction(System.identityHashCode(this), 400) {
            @Override
            protected void runAction() {
                widgetEntryChanged();
            }
        });
    }


    public void unsetListeners() {
        setOnLongClickListener(null);
        setChangedListeners(null, null);
    }

    public void widgetEntryChanged() {
        clearWarningMessage();
        if (hasListener()) {
            widgetChangedListener.widgetEntryChanged(this);
        }
    }

    public void clearWarningMessage() {
        if (this.warningView != null) {
            this.warningView.setVisibility(View.GONE);
            ViewUtil.setBackgroundRetainPadding(this, null);
        }
    }

    public boolean hasListener() {
        return widgetChangedListener != null;
    }

    /**
     * @return True if file is too big to upload.
     */
    protected boolean checkFileSize(File file) {
        if (FileUtil.isFileTooLargeToUpload(file)) {
            String fileSize = FileUtil.getFileSizeInMegs(file) + "";
            showOversizedMediaWarning(fileSize);
            return true;
        } else if (FileUtil.isFileOversized(file) && !HiddenPreferences.isFileOversizeWarningDisabled()) {
            notifyWarning(StringUtils.getStringRobust(getContext(), R.string.attachment_oversized,
                    FileUtil.getFileSizeInMegs(file) + ""));
        }
        return false;
    }


    protected Vector<SelectChoice> getSelectChoices() {
        Vector<SelectChoice> selectChoices = mPrompt.getSelectChoices();
        if (!DeveloperPreferences.isSpaceAllowedInSelectChoices() && selectChoices != null) {
            for (int i = 0; i < selectChoices.size(); i++) {
                if (selectChoices.get(i).getValue().contains(" ")) {
                    throw new IllegalArgumentException("Select answer option cannot contain spaces in question '" +
                            mPrompt.getLongText() + "' with option '" + mPrompt.getSelectChoiceText(selectChoices.get(i)) + "'");
                }
            }
        }
        return selectChoices;
    }

    /*
     * Method to make localization and styling easier for devs
     * copied from CommCareActivity
     */

    protected Spannable forceMarkdown(String text) {
        return MarkupUtil.returnMarkdown(getContext(), text);
    }

    /**
     * Implemented by questions that read binary data from and external source,
     * such as the image chooser or a custom intent callout.
     *
     * @param answer generic object that individual implementations know how to
     *               process
     */
    public void setBinaryData(Object answer) {
        String instanceClass = this.getClass().getSimpleName();
        Logger.log(LogTypes.SOFT_ASSERT,
                "Calling empty implementation of " + instanceClass + ".setBinaryData");
    }

    protected void showToast(String stringkey) {
        Toast.makeText(getContext(),
                Localization.get(stringkey),
                Toast.LENGTH_LONG).show();
    }

    public boolean forcesPortrait() {
        return false;
    }

    /**
     * @return Whether this widget is to be shown in compact mode
     */
    protected boolean isInCompactMode() {
        return mCompact;
    }
}
