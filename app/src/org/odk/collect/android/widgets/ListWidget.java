
package org.odk.collect.android.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.commcare.android.util.MediaUtil;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.util.Vector;

/**
 * ListWidget handles select-one fields using radio buttons. The radio buttons are aligned
 * horizontally. They are typically meant to be used in a field list, where multiple questions with
 * the same multiple choice answers can sit on top of each other and make a grid of buttons that is
 * easy to navigate quickly. Optionally, you can turn off the labels. This would be done if a label
 * widget was at the top of your field list to provide the labels. If audio or video are specified
 * in the select answers they are ignored.
 * 
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class ListWidget extends QuestionWidget implements OnCheckedChangeListener {
    private static final String TAG = ListWidget.class.getSimpleName();

    private final int buttonIdBase;
    private final static int TEXTSIZE = 21;

    // Holds the entire question and answers. It is a horizontally aligned linear layout
    private LinearLayout questionLayout;

    private final Vector<SelectChoice> mItems;
    private final Vector<RadioButton> buttons;

    /**
     * @param displayLabel Option to keep labels blank
     */
    public ListWidget(Context context, FormEntryPrompt prompt, boolean displayLabel) {
        super(context, prompt);

        mItems = mPrompt.getSelectChoices();
        buttons = new Vector<>();

        // Layout holds the horizontal list of buttons
        LinearLayout buttonLayout = new LinearLayout(context);

        String s = null;
        if (getCurrentAnswer() != null) {
            s = ((Selection) getCurrentAnswer().getValue()).getValue();
        }
        
        //Is this safe enough from collisions?
        buttonIdBase = Math.abs(mPrompt.getIndex().toString().hashCode());

        if (mPrompt.getSelectChoices() != null) {
            for (int i = 0; i < mItems.size(); i++) {
                RadioButton r = new RadioButton(getContext());

                r.setOnCheckedChangeListener(this);
                r.setId(i + buttonIdBase);
                r.setEnabled(!mPrompt.isReadOnly());
                r.setFocusable(!mPrompt.isReadOnly());

                buttons.add(r);

                if (mItems.get(i).getValue().equals(s)) {
                    r.setChecked(true);
                }

                String imageURI =
                        mPrompt.getSpecialFormSelectChoiceText(mItems.get(i),
                                FormEntryCaption.TEXT_FORM_IMAGE);

                // build image view (if an image is provided)
                ImageView mImageView = null;
                TextView mMissingImage = null;

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
                TextView label = new TextView(getContext());
                setChoiceText(label, mItems, i);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXTSIZE);
                if (!displayLabel) {
                    label.setVisibility(View.GONE);
                }

                // answer layout holds the label text/image on top and the radio button on bottom
                LinearLayout answer = new LinearLayout(getContext());
                answer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.TOP;
                answer.setLayoutParams(params);

                if (mImageView != null) {
                    if (!displayLabel) {
                        mImageView.setVisibility(View.GONE);
                    }
                    answer.addView(mImageView);
                } else if (mMissingImage != null) {
                    answer.addView(mMissingImage);
                } else {
                    if (displayLabel) {
                        answer.addView(label);
                    }

                }
                answer.addView(r);
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
        // LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
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
        for (RadioButton button : this.buttons) {
            if (button.isChecked()) {
                button.setChecked(false);
                return;
            }
        }
    }

    @Override
    public IAnswerData getAnswer() {
        int i = getCheckedId();
        if (i == -1) {
            return null;
        } else {
            SelectChoice sc = mItems.elementAt(i - buttonIdBase);
            return new SelectOneData(new Selection(sc));
        }
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    private int getCheckedId() {
        for (RadioButton button : this.buttons) {
            if (button.isChecked()) {
                return button.getId();
            }
        }
        return -1;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (!isChecked) {
            // If it got unchecked, we don't care.
            return;
        }

        for (RadioButton button : this.buttons) {
            if (button.isChecked() && !(buttonView == button)) {
                button.setChecked(false);
            }
        }
        
        widgetEntryChanged();
    }

    @Override
    protected void addQuestionText() {
        // Add the text view. Textview always exists, regardless of whether there's text.
        TextView questionText = new TextView(getContext());
        setQuestionText(questionText, mPrompt);
        questionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXTSIZE);
        questionText.setTypeface(null, Typeface.BOLD);
        questionText.setPadding(0, 0, 0, 7);
        questionText.setId(buttonIdBase); // assign random id

        // Wrap to the size of the parent view
        questionText.setHorizontallyScrolling(false);

        if (mPrompt.getLongText() == null) {
            questionText.setVisibility(GONE);
        }

        // Put the question text on the left half of the screen
        LinearLayout.LayoutParams labelParams =
            new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.weight = 1;

        questionLayout = new LinearLayout(getContext());
        questionLayout.setOrientation(LinearLayout.HORIZONTAL);
        questionLayout.addView(questionText, labelParams);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        for (RadioButton r : buttons) {
            r.setOnLongClickListener(l);
        }
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        for (RadioButton r : buttons) {
            r.setOnLongClickListener(null);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        for (RadioButton r : buttons) {
            r.cancelLongPress();
        }
    }
}
