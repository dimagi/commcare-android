
package org.odk.collect.android.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.util.MediaUtil;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.util.Vector;

/**
 * The Label Widget does not return an answer. The purpose of this widget is to be the top entry in
 * a field-list with a bunch of list widgets below. This widget provides the labels, so that the
 * list widgets can hide their labels and reduce the screen clutter. This class is essentially
 * ListWidget with all the answer generating code removed.
 * 
 * @author Jeff Beorse
 */
public class LabelWidget extends QuestionWidget {
    private static final String TAG = LabelWidget.class.getSimpleName();
    private static final int RANDOM_BUTTON_ID = 4853487;
    private final static int TEXTSIZE = 21;

    private LinearLayout questionLayout;

    private TextView mQuestionText;
    private TextView mMissingImage;
    private ImageView mImageView;
    private TextView label;

    public LabelWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        Vector<SelectChoice> mItems = mPrompt.getSelectChoices();

        LinearLayout buttonLayout = new LinearLayout(context);

        if (mPrompt.getSelectChoices() != null) {
            for (int i = 0; i < mItems.size(); i++) {

                String imageURI =
                        mPrompt.getSpecialFormSelectChoiceText(mItems.get(i),
                                FormEntryCaption.TEXT_FORM_IMAGE);

                // build image view (if an image is provided)
                mImageView = null;
                mMissingImage = null;

                // Now set up the image view
                String errorMsg = null;
                if (imageURI != null) {
                    try {
                        String imageFilename =
                            ReferenceManager._().DeriveReference(imageURI).getLocalURI();
                        final File imageFile = new File(imageFilename);
                        if (imageFile.exists()) {
                            Bitmap b = null;
                            try {
                                Display display =
                                    ((WindowManager) getContext().getSystemService(
                                        Context.WINDOW_SERVICE)).getDefaultDisplay();
                                int screenWidth = display.getWidth();
                                int screenHeight = display.getHeight();
                                b = MediaUtil.getBitmapScaledToContainer(imageFile, screenHeight,
                                            screenWidth);
                            } catch (OutOfMemoryError e) {
                                errorMsg = "ERROR: " + e.getMessage();
                            }

                            if (b != null) {
                                mImageView = new ImageView(getContext());
                                mImageView.setPadding(2, 2, 2, 2);
                                mImageView.setAdjustViewBounds(true);
                                mImageView.setImageBitmap(b);
                                mImageView.setId(23423534);
                            } else if (errorMsg == null) {
                                // An error hasn't been logged and loading the image failed, so it's
                                // likely
                                // a bad file.
                                errorMsg = StringUtils.getStringRobust(getContext(), R.string.file_invalid, imageFile.toString());

                            }
                        } else {
                            errorMsg = StringUtils.getStringRobust(getContext(), R.string.file_missing, imageFile.toString());
                        }

                        if (errorMsg != null) {
                            // errorMsg is only set when an error has occured
                            Log.e(TAG, errorMsg);
                            mMissingImage = new TextView(getContext());
                            mMissingImage.setText(errorMsg);

                            mMissingImage.setPadding(2, 2, 2, 2);
                            mMissingImage.setId(234873453);
                        }
                    } catch (InvalidReferenceException e) {
                        Log.e(TAG, "image invalid reference exception");
                        e.printStackTrace();
                    }
                }

                // build text label. Don't assign the text to the built in label to he
                // button because it aligns horizontally, and we want the label on top
                label = new TextView(getContext());
                setChoiceText(label, mPrompt, i);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXTSIZE);

                // answer layout holds the label text/image on top and the radio button on bottom
                LinearLayout answer = new LinearLayout(getContext());
                answer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.TOP;
                answer.setLayoutParams(params);

                if (mImageView != null) {
                    answer.addView(mImageView);
                } else if (mMissingImage != null) {
                    answer.addView(mMissingImage);
                } else {
                    answer.addView(label);
                }
                answer.setPadding(4, 0, 4, 0);

                // Each button gets equal weight
                LinearLayout.LayoutParams answerParams =
                    new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                            LayoutParams.WRAP_CONTENT);
                answerParams.weight = 1;

                buttonLayout.addView(answer, answerParams);
            }
        }

        // Align the buttons so that they appear horizonally and are right justified
        // buttonLayout.setGravity(Gravity.RIGHT);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        // LinearLayout.LayoutParams params = new
        // LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        // buttonLayout.setLayoutParams(params);

        // The buttons take up the right half of the screen
        LinearLayout.LayoutParams buttonParams =
            new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        buttonParams.weight = 1;

        questionLayout.addView(buttonLayout, buttonParams);
        addView(questionLayout);
    }

    @Override
    public void clearAnswer() {
        // Do nothing, no answers to clear
    }

    @Override
    public IAnswerData getAnswer() {
        return null;
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    protected void addQuestionText() {
        // Add the text view. Textview always exists, regardless of whether there's text.
        mQuestionText = new TextView(getContext());
        setQuestionText(mQuestionText, mPrompt);
        mQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXTSIZE);
        mQuestionText.setTypeface(null, Typeface.BOLD);
        mQuestionText.setPadding(0, 0, 0, 7);
        mQuestionText.setId(RANDOM_BUTTON_ID); // assign random id

        // Wrap to the size of the parent view
        mQuestionText.setHorizontallyScrolling(false);

        if (mPrompt.getLongText() == null) {
            mQuestionText.setVisibility(GONE);
        }

        // Put the question text on the left half of the screen
        LinearLayout.LayoutParams labelParams =
            new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.weight = 1;

        questionLayout = new LinearLayout(getContext());
        questionLayout.setOrientation(LinearLayout.HORIZONTAL);

        questionLayout.addView(mQuestionText, labelParams);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mQuestionText.cancelLongPress();

        if (mMissingImage != null) {
            mMissingImage.cancelLongPress();
        }
        if (mImageView != null) {
            mImageView.cancelLongPress();
        }
        if (label != null) {
            label.cancelLongPress();
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mQuestionText.setOnLongClickListener(l);
        if (mMissingImage != null) {
            mMissingImage.setOnLongClickListener(l);
        }
        if (mImageView != null) {
            mImageView.setOnLongClickListener(l);
        }
        if (label != null) {
            label.setOnLongClickListener(l);
        }
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mQuestionText.setOnLongClickListener(null);
        if (mMissingImage != null) {
            mMissingImage.setOnLongClickListener(null);
        }
        if (mImageView != null) {
            mImageView.setOnLongClickListener(null);
        }
        if (label != null) {
            label.setOnLongClickListener(null);
        }
    }
}
