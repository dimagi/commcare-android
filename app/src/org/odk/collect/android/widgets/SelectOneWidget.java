/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.RadioButton;

import org.commcare.dalvik.R;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.views.media.MediaLayout;

import java.util.Vector;

/**
 * SelectOneWidgets handles select-one fields using radio buttons.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SelectOneWidget extends QuestionWidget implements OnCheckedChangeListener {

    Vector<SelectChoice> mItems;
    int buttonIdBase;

    Vector<RadioButton> buttons;

    public SelectOneWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        int padding = (int)Math.floor(context.getResources().getDimension(R.dimen.select_padding));
       
        mItems = prompt.getSelectChoices();
        buttons = new Vector<RadioButton>();

        String s = null;
        if (prompt.getAnswerValue() != null) {
            s = prompt.getAnswerValue().uncast().getString();
        }
        
        //Is this safe enough from collisions?
        buttonIdBase = Math.abs(prompt.getIndex().toString().hashCode());

        if (prompt.getSelectChoices() != null) {
            for (int i = 0; i < mItems.size(); i++) {
                final RadioButton rb = new RadioButton(getContext());
                String markdownText = prompt.getSelectItemMarkdownText(mItems.get(i));
                if(markdownText != null){
                    rb.setText(forceMarkdown(markdownText));
                } else{
                    rb.setText(prompt.getSelectChoiceText(mItems.get(i)));
                }
                rb.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
                rb.setId(i + buttonIdBase);
                rb.setEnabled(!prompt.isReadOnly());
                rb.setFocusable(!prompt.isReadOnly());

                rb.setBackgroundResource(R.drawable.selector_button_press);
                
                buttons.add(rb);

                if (mItems.get(i).getValue().equals(s)) {
                    rb.setChecked(true);
                }
                
                //Move to be below the above setters. Not sure if that will cause
                //problems, but I don't think it should.
                rb.setOnCheckedChangeListener(this);

                String audioURI = null;
                audioURI =
                    prompt.getSpecialFormSelectChoiceText(mItems.get(i),
                        FormEntryCaption.TEXT_FORM_AUDIO);

                String imageURI = null;
                imageURI =
                    prompt.getSpecialFormSelectChoiceText(mItems.get(i),
                        FormEntryCaption.TEXT_FORM_IMAGE);

                String videoURI = null;
                videoURI = prompt.getSpecialFormSelectChoiceText(mItems.get(i), "video");

                String bigImageURI = null;
                bigImageURI = prompt.getSpecialFormSelectChoiceText(mItems.get(i), "big-image");

                MediaLayout mediaLayout = new MediaLayout(getContext());
                mediaLayout.setAVT(rb, audioURI, imageURI, videoURI, bigImageURI);
                mediaLayout.setPadding(0, padding, 0, padding);
                
                mediaLayout.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        rb.performClick();
                    }
                });
                addView(mediaLayout);


                // Last, add the dividing line (except for the last element)
                ImageView divider = new ImageView(getContext());
                divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
                if (i != mItems.size() - 1) {
                    addView(divider);
                }
            }
        }
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
    
    private void onUserInteracton() {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) this.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    @Override
    public void setFocus(Context context) {
        onUserInteracton();
    }


    public int getCheckedId() {
        for (RadioButton button : this.buttons) {
            if (button.isChecked()) {
                return button.getId();
            }
        }
        return -1;
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        onUserInteracton();
        if (isChecked) {
            for (RadioButton button : this.buttons) {
                if (button.isChecked() && !(buttonView == button)) {
                    button.setChecked(false);
                }
            }
        }
        widgetEntryChanged();
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

        for (RadioButton button : this.buttons) {
            button.setOnCheckedChangeListener(null);
            button.setOnLongClickListener(null);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        for (RadioButton button : this.buttons) {
            button.cancelLongPress();
        }
    }
}
