package org.odk.collect.android.widgets;

import java.io.File;

import org.commcare.android.util.MarkupUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.R.color;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.WidgetChangedListener;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.views.ShrinkingTextView;
import org.odk.collect.android.views.media.MediaLayout;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public abstract class QuestionWidget extends LinearLayout {

    @SuppressWarnings("unused")
    private final static String t = "QuestionWidget";

    private LinearLayout.LayoutParams mLayout;
    protected FormEntryPrompt mPrompt;

    protected final int mQuestionFontsize;
    protected final int mAnswerFontsize;
    protected final static String ACQUIREFIELD = "acquire";
    
    //the height of the "Frame" available to this widget. The frame
    //is the size of the parent that is available (it is roughly
    //the window without the keyboard/top bars/etc.)
    //Note that this value is only populated after the widget is
    //drawn for now.
    protected int mFrameHeight = -1;

    private TextView mQuestionText;
    private FrameLayout helpPlaceholder;
    private ShrinkingTextView mHelpText;
    protected boolean hasListener;
    private View toastView;
    
    //Whether this question widget needs to request focus on
    //its next draw, due to a new element having been added (which couldn't have
    //requested focus yet due to having not been layed out)
    protected boolean focusPending = false;
    
    protected WidgetChangedListener widgetChangedListener;


    public QuestionWidget(Context context, FormEntryPrompt p) {
        this(context, p, null);
    }
    
    public QuestionWidget(Context context, FormEntryPrompt p, WidgetChangedListener w){
        super(context);
        
        if(w!=null){
            hasListener = false;
            widgetChangedListener = w;
        }
        this.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                QuestionWidget.this.acceptFocus();
            }
            
        });
        
        hasListener = (w != null);
        
        
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            String question_font =
                settings.getString(PreferencesActivity.KEY_FONT_SIZE, Collect.DEFAULT_FONTSIZE);
                mQuestionFontsize = new Integer(question_font).intValue();
            mAnswerFontsize = mQuestionFontsize + 2;

            mPrompt = p;

            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.TOP);
            setPadding(0, 7, 0, 0);

            mLayout =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
            mLayout.setMargins(10, 0, 10, 0);

            addQuestionText(p);
            addHelpText(p);
            
            addHelpPlaceholder(p);
    }
    

    protected void acceptFocus() {
        
    }


    private void addHelpPlaceholder(FormEntryPrompt p) {
        helpPlaceholder = new FrameLayout(this.getContext());
        helpPlaceholder.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        
        if("help".equals(p.getSpecialFormQuestionText("help"))) {
            String specialHelpText = p.getSpecialFormQuestionText("help-text");
            
            String specialHelpImage = p.getSpecialFormQuestionText("help-image");
            String specialHelpVideo = p.getSpecialFormQuestionText("help-video");
            
            TextView helpText = new TextView(getContext());
            helpText.setText(specialHelpText);
            helpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize);
            helpText.setPadding(0, 0, 0, 7);
            helpText.setId(38475483); // assign random id
    
            
            MediaLayout helpLayout = new MediaLayout(getContext());
            helpLayout.setAVT(helpText, null, specialHelpImage, specialHelpVideo, null);
            helpLayout.setPadding(15, 15, 15, 15);
            
            helpLayout.setBackgroundResource(color.very_light_blue);
            helpPlaceholder.addView(helpLayout);
        }

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


    public abstract void setOnLongClickListener(OnLongClickListener l);
    
    
    private class URLSpanNoUnderline extends URLSpan {
        public URLSpanNoUnderline(String url) {
            super(url);
        }
        /*
         * (non-Javadoc)
         * @see android.text.style.ClickableSpan#updateDrawState(android.text.TextPaint)
         */
        @Override public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }
    
    public void notifyOnScreen(String text, boolean strong){
        if(strong){
            this.setBackgroundDrawable(this.getContext().getResources().getDrawable(R.drawable.bubble_invalid));
        } else{
            this.setBackgroundDrawable(this.getContext().getResources().getDrawable(R.drawable.bubble_warn));
        }
        
        if(this.toastView == null) {
            this.toastView = View.inflate(this.getContext(), R.layout.toast_view, this).findViewById(R.id.toast_view_root);
            focusPending = true;
        } else {
            if(this.toastView.getVisibility() != View.VISIBLE) {
                this.toastView.setVisibility(View.VISIBLE);
                focusPending = true;
            }
        }
        TextView messageView = (TextView)this.toastView.findViewById(R.id.message);
        messageView.setText(text);
        
        //If the toastView already exists, we can just scroll to it right now
        //if not, we actually have to do it later, when we lay this all back out
        if(!focusPending) {
            requestChildViewOnScreen(messageView);
        }
    }
    
    public void notifyWarning(String text) {
        notifyOnScreen(text, false);
    }
    
    public void notifyInvalid(String text) {
        notifyOnScreen(text, true);
    }
    
    /*
     * Use to signal that there's a portion of this view that wants to be 
     * visible to the user on the screen. This method will place the sub 
     * view on the screen, and will also place as much of this view as possible
     * on the screen. If this view is smaller than the viewable area available, it
     * will be fully visible in addition to the subview.
     */
    private void requestChildViewOnScreen(View child) {
        
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
        if(mFrameHeight == -1){
            child.requestRectangleOnScreen(vitalPortion);
            return;
        }        
        
        //If the whole view fits, just request that we display the whole thing.
        if(wholeView.height() < mFrameHeight) {
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
        if(wholeView.contains(vitalPortion)) {
            this.requestRectangleOnScreen(wholeView);
            return;
        }
        
        //otherwise, move to the requested frame to be at the bottom of this view
        wholeView.offsetTo(wholeView.left, bottomY - wholeView.height());
        
        //now see if the transformed view contains the vital portion
        if(wholeView.contains(vitalPortion)) {
            this.requestRectangleOnScreen(wholeView);
            return;
        }

        //Otherwise the child is hidden in the frame, so it won't matter which
        //we choose.
        child.requestRectangleOnScreen(vitalPortionSaved);
    }
    
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        
        //If we're coming back in after we just laid out adding a new element that needs
        //focus, we can now scroll to it, since it's actually had its spacing declared.
        if(changed && focusPending) {
            focusPending = false;
            if(this.toastView == null) {
                //NOTE: This shouldn't be possible, but if it doesn't happen
                //we don't wanna crash. Look here if focus isn't getting grabbed
                //for some reason (there's no other negative consequence)
            } else {
                TextView messageView = (TextView)this.toastView.findViewById(R.id.message);
                requestChildViewOnScreen(messageView);
            }
        }
    }
    
    private void stripUnderlines(TextView textView) {
        Spannable s = (Spannable)textView.getText();
        URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
        for (URLSpan span: spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            s.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL());
            s.setSpan(span, start, end, 0);
        }
        textView.setText(s);
    }    


    /**
     * Add a Views containing the question text, audio (if applicable), and image (if applicable).
     * To satisfy the RelativeLayout constraints, we add the audio first if it exists, then the
     * TextView to fit the rest of the space, then the image if applicable.
     */
    protected void addQuestionText(final FormEntryPrompt p) {
        String markdownText = p.getMarkdownText();
        
        String imageURI = p.getImageText();
        String audioURI = p.getAudioText();
        String videoURI = p.getSpecialFormQuestionText("video");
        String qrCodeContent = p.getSpecialFormQuestionText("qrcode");

        // shown when image is clicked
        String bigImageURI = p.getSpecialFormQuestionText("big-image");

        // Add the text view. Textview always exists, regardless of whether there's text.
        mQuestionText = new TextView(getContext());
        mQuestionText.setId(38475483); // assign random id
        
        // if we have markdown, use that. 
        if(markdownText != null){
            mQuestionText.setText(MarkupUtil.styleSpannable(getContext(), markdownText));
            mQuestionText.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            mQuestionText.setText(p.getLongText());
            mQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize);
            mQuestionText.setTypeface(null, Typeface.BOLD);
            mQuestionText.setPadding(0, 0, 0, 7);
        }
        
        // Wrap to the size of the parent view
        mQuestionText.setHorizontallyScrolling(false);

        if(p.getLongText()!= null){
            if(p.getLongText().contains("\u260E")){
                if(Linkify.addLinks(mQuestionText,Linkify.PHONE_NUMBERS)){
                    stripUnderlines(mQuestionText);
                }
                else{
                    System.out.println("this should be an error I'm thinking?");
                }
            }
        }

        if (p.getLongText() == null) {
            mQuestionText.setVisibility(GONE);
        }

        // Create the layout for audio, image, text
        MediaLayout mediaLayout = new MediaLayout(getContext()) {
            protected void onHelpPressed() {
                fireHelpText(p);
            }

        };
        
        String helpText = p.getSpecialFormQuestionText("help");
        if("help".equals(helpText)) {
            videoURI = helpText;
        }
        
        
        mediaLayout.setAVT(mQuestionText, audioURI, imageURI, videoURI, bigImageURI, qrCodeContent);

        addView(mediaLayout, mLayout);
    }
    
    private void fireHelpText(FormEntryPrompt prompt) {
        

        if(!PreferenceManager.getDefaultSharedPreferences(this.getContext().getApplicationContext()).
                getBoolean(PreferencesActivity.KEY_HELP_MODE_TRAY, false)) {
            
            AlertDialog mAlertDialog = new AlertDialog.Builder(this.getContext()).create();
            mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
            mAlertDialog.setTitle("");
            
            String specialHelpText = prompt.getSpecialFormQuestionText("help-text");
            
            String specialHelpImage = prompt.getSpecialFormQuestionText("help-image");
            String specialHelpVideo = prompt.getSpecialFormQuestionText("help-video");
            
            ScrollView scrollView = new ScrollView(this.getContext());
            TextView helpText = new TextView(getContext());
            helpText.setText(specialHelpText);
            helpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize);
            helpText.setPadding(0, 0, 0, 7);
            helpText.setId(38475483); // assign random id
    
            
            MediaLayout helpLayout = new MediaLayout(getContext());
            helpLayout.setAVT(helpText, null, specialHelpImage, specialHelpVideo, null);
            helpLayout.setPadding(15, 15, 15, 15);
            
            scrollView.addView(helpLayout);
            mAlertDialog.setView(scrollView);
            
            //mAlertDialog.setMessage();
            DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    switch (i) {
                        case DialogInterface.BUTTON1:
                            dialog.cancel();
                            break;
                    }
                }
            };
            mAlertDialog.setCancelable(true);
            mAlertDialog.setButton(MarkupUtil.localizeStyleSpannable(getContext(), "odk_ok"), errorListener);
            mAlertDialog.show();
        } else {
        
            if(helpPlaceholder.getVisibility() == View.GONE) {
                expand(helpPlaceholder);
            } else {
                collapse(helpPlaceholder);
            }
        }
    }
    
    public static void expand(final View v) {
        v.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation()
        {
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

    public static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if(interpolatedTime == 1){
                    v.setVisibility(View.GONE);
                }else{
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
    
    public void updateFrameSize(int width, int height) {
        int maxHelpHeight = height / 4;
        if(mHelpText != null) {
            mHelpText.updateMaxHeight(maxHelpHeight);
        }
        mFrameHeight = height;
    }

    /**
     * Add a TextView containing the help text.
     */
    private void addHelpText(FormEntryPrompt p) {

        String s = p.getHelpText();

        if (s != null && !s.equals("")) {
            mHelpText = new ShrinkingTextView(getContext(),this.getMaxHintHeight());
            mHelpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize - 3);
            mHelpText.setPadding(0, -5, 0, 7);
            // wrap to the widget of view
            mHelpText.setHorizontallyScrolling(false);
            mHelpText.setText(s);
            mHelpText.setTypeface(null, Typeface.ITALIC);

            addView(mHelpText, mLayout);
        }
    }

    protected int getMaxHintHeight() {
        return -1;
    }


    /**
     * Every subclassed widget should override this, adding any views they may contain, and calling
     * super.cancelLongPress()
     */
    public void cancelLongPress() {
        super.cancelLongPress();
        if (mQuestionText != null) {
            mQuestionText.cancelLongPress();
        }
        if (mHelpText != null) {
            mHelpText.cancelLongPress();
        }
    }
    
    protected IAnswerData getCurrentAnswer() {
        IAnswerData current = mPrompt.getAnswerValue();
        if(current == null) { return null; }
        return getTemplate().cast(current.uncast());
    }
    
    protected IAnswerData getTemplate() {
        return AnswerDataFactory.template(mPrompt.getControlType(), mPrompt.getDataType());
    }


    public void hideHintText() {
        mHelpText.setVisibility(View.GONE);
    }
    
    public FormIndex getFormId(){
        return mPrompt.getIndex();
    }
    
    public void setChangedListener(WidgetChangedListener wcl){
        widgetChangedListener = wcl;
        hasListener = true;
    }
    
    public void widgetEntryChanged(){
        if(this.toastView != null) {
            this.toastView.setVisibility(View.GONE);
            this.setBackgroundDrawable(null);
        }
        if(hasListener){
            widgetChangedListener.widgetEntryChanged();
        }
    }
    
    public void checkFileSize(File file){
        if(FileUtils.isFileOversized(file)){
            this.notifyWarning(Localization.get("odk_attachment_oversized", FileUtils.getFileSize(file)+""));
        }
    }
    
    public void checkFileSize(String filepath){
        checkFileSize(new File(filepath));
    }
}
