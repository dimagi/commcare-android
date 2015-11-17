
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.util.MediaUtil;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.util.Vector;

/**
 * ListMultiWidget handles multiple selection fields using check boxes. The check boxes are aligned
 * horizontally. They are typically meant to be used in a field list, where multiple questions with
 * the same multiple choice answers can sit on top of each other and make a grid of buttons that is
 * easy to navigate quickly. Optionally, you can turn off the labels. This would be done if a label
 * widget was at the top of your field list to provide the labels. If audio or video are specified
 * in the select answers they are ignored. This class is almost identical to ListWidget, except it
 * uses checkboxes. It also did not require a custom clickListener class.
 *
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class ListMultiWidget extends QuestionWidget {
    private static final String TAG = ListMultiWidget.class.getSimpleName();

    private final int buttonIdBase;
    private final static int CHECKBOX_ID = 100;
    private final static int TEXTSIZE = 21;

    // Holds the entire question and answers. It is a horizontally aligned linear layout
    private LinearLayout questionLayout;


    private final boolean mCheckboxInit = true;
    private final Vector<SelectChoice> mItems;

    private final Vector<CheckBox> mCheckboxes;


    /**
     * @param displayLabel Option to keep labels blank
     */
    @SuppressWarnings("unchecked")
    public ListMultiWidget(Context context, FormEntryPrompt prompt, boolean displayLabel) {
        super(context, prompt);

        mItems = mPrompt.getSelectChoices();
        mCheckboxes = new Vector<>();

        // Layout holds the horizontal list of buttons
        LinearLayout buttonLayout = new LinearLayout(context);

        Vector<Selection> ve = new Vector<>();
        if (mPrompt.getAnswerValue() != null) {
            ve = (Vector<Selection>) getCurrentAnswer().getValue();
        }

        //Is this safe enough from collisions?
        buttonIdBase = Math.abs(mPrompt.getIndex().toString().hashCode());

        if (mPrompt.getSelectChoices() != null) {
            for (int i = 0; i < mItems.size(); i++) {
                CheckBox c = new CheckBox(getContext());

                // when clicked, check for readonly before toggling
                c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // XXX PLM: hmmm, the conditional below is always false...
                        if (!mCheckboxInit && mPrompt.isReadOnly()) {
                            if (buttonView.isChecked()) {
                                buttonView.setChecked(false);
                            } else {
                                buttonView.setChecked(true);
                            }
                        }
                        widgetEntryChanged();
                    }
                });

                c.setId(CHECKBOX_ID + i);
                c.setFocusable(!mPrompt.isReadOnly());
                c.setEnabled(!mPrompt.isReadOnly());
                for (int vi = 0; vi < ve.size(); vi++) {
                    // match based on value, not key
                    if (mItems.get(i).getValue().equals(ve.elementAt(vi).getValue())) {
                        c.setChecked(true);
                        break;
                    }

                }
                mCheckboxes.add(c);

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
                label.setText(mPrompt.getSelectChoiceText(mItems.get(i)));
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
                answer.addView(c);
                answer.setPadding(4, 0, 4, 0);

                // /Each button gets equal weight
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
        int j = mItems.size();
        for (int i = 0; i < j; i++) {

            // no checkbox group so find by id + offset
            CheckBox c = ((CheckBox) findViewById(CHECKBOX_ID + i));
            if (c.isChecked()) {
                c.setChecked(false);
            }
        }
    }


    @Override
    public IAnswerData getAnswer() {
        Vector<Selection> vc = new Vector<>();
        for (int i = 0; i < mItems.size(); i++) {
            CheckBox c = ((CheckBox) findViewById(CHECKBOX_ID + i));
            if (c.isChecked()) {
                vc.add(new Selection(mItems.get(i)));
            }

        }

        if (vc.size() == 0) {
            return null;
        } else {
            return new SelectMultiData(vc);
        }

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
        TextView questionText = new TextView(getContext());
        questionText.setText(mPrompt.getLongText());
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
        for (CheckBox c : mCheckboxes) {
            c.setOnLongClickListener(l);
        }
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        for (CheckBox c : mCheckboxes) {
            c.setOnLongClickListener(null);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        for (CheckBox c : mCheckboxes) {
            c.cancelLongPress();
        }
    }
}
