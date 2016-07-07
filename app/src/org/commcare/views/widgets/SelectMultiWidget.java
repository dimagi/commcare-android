package org.commcare.views.widgets;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.commcare.dalvik.R;
import org.commcare.views.media.MediaLayout;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Vector;

/**
 * SelctMultiWidget handles multiple selection fields using checkboxes.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SelectMultiWidget extends QuestionWidget {
    private boolean mCheckboxInit = true;
    private final Vector<SelectChoice> mItems;
    private final int buttonIdBase;

    private final Vector<CheckBox> mCheckboxes;

    @SuppressWarnings("unchecked")
    public SelectMultiWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);
        mCheckboxes = new Vector<>();
        mItems = mPrompt.getSelectChoices();

        setOrientation(LinearLayout.VERTICAL);

        Vector<Selection> ve = new Vector<>();
        if (mPrompt.getAnswerValue() != null) {
            ve = (Vector<Selection>)getCurrentAnswer().getValue();
        }

        //Is this safe enough from collisions?
        buttonIdBase = Math.abs(mPrompt.getIndex().hashCode());

        if (mPrompt.getSelectChoices() != null) {
            for (int i = 0; i < mItems.size(); i++) {
                // no checkbox group so id by answer + offset
                final CheckBox c = new CheckBox(getContext());

                c.setId(buttonIdBase + i);
                String markdownText = prompt.getSelectItemMarkdownText(mItems.get(i));
                if (markdownText != null) {
                    c.setText(forceMarkdown(markdownText));
                } else {
                    c.setText(prompt.getSelectChoiceText(mItems.get(i)));
                }
                c.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
                c.setFocusable(!mPrompt.isReadOnly());
                c.setEnabled(!mPrompt.isReadOnly());

                int padding = (int)Math.floor(context.getResources().getDimension(R.dimen.select_padding));

                c.setPadding(c.getPaddingLeft(), 0, padding, 0);
                for (int vi = 0; vi < ve.size(); vi++) {
                    // match based on value, not key
                    if (mItems.get(i).getValue().equals(ve.elementAt(vi).getValue())) {
                        c.setChecked(true);
                        break;
                    }
                }

                //Note: This gets fired during setup as well, so this listener should only
                //be added after everything about the checkbox is set up

                // when clicked, check for readonly before toggling
                c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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

                mCheckboxes.add(c);

                String audioURI =
                        mPrompt.getSpecialFormSelectChoiceText(mItems.get(i),
                                FormEntryCaption.TEXT_FORM_AUDIO);

                String imageURI =
                        mPrompt.getSpecialFormSelectChoiceText(mItems.get(i),
                                FormEntryCaption.TEXT_FORM_IMAGE);

                String videoURI = mPrompt.getSpecialFormSelectChoiceText(mItems.get(i), "video");

                String bigImageURI = mPrompt.getSpecialFormSelectChoiceText(mItems.get(i), "big-image");

                MediaLayout mediaLayout = new MediaLayout(getContext());
                mediaLayout.setAVT(c, audioURI, imageURI, videoURI, bigImageURI);
                addView(mediaLayout);

                mediaLayout.setPadding(0, padding, 0, padding);

                mediaLayout.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        c.performClick();
                    }
                });

                // Last, add the dividing line between elements (except for the last element)
                ImageView divider = new ImageView(getContext());
                divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
                if (i != mItems.size() - 1) {
                    addView(divider);
                }
            }
        }

        mCheckboxInit = false;
    }

    @Override
    public void clearAnswer() {
        int j = mItems.size();
        for (int i = 0; i < j; i++) {

            // no checkbox group so find by id + offset
            CheckBox c = ((CheckBox)findViewById(buttonIdBase + i));
            if (c.isChecked()) {
                c.setChecked(false);
            }
        }
    }

    @Override
    public IAnswerData getAnswer() {
        Vector<Selection> vc = new Vector<>();
        for (int i = 0; i < mItems.size(); i++) {
            CheckBox c = ((CheckBox)findViewById(buttonIdBase + i));
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
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
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
