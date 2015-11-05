
package org.odk.collect.android.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;

import org.commcare.android.util.MediaUtil;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.adapters.ImageAdapter;
import org.odk.collect.android.listeners.AdvanceToNextListener;

import java.io.File;
import java.util.Vector;

/**
 * GridWidget handles select-one fields using a grid of icons. The user clicks the desired icon and
 * the background changes from black to orange. If text, audio, or video are specified in the select
 * answers they are ignored.
 * 
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class GridWidget extends QuestionWidget {
    private final Vector<SelectChoice> mItems;

    // The possible select choices
    private final String[] choices;

    // The Gridview that will hol the icons
    private final GridView gridview;

    // Defines which icon is selected
    private final boolean[] selected;

    // The image views for each of the icons
    private final ImageView[] imageViews;

    // The RGB value for the orange background
    private static final int orangeRedVal = 255;
    private static final int orangeGreenVal = 140;
    private static final int orangeBlueVal = 0;

    private final AdvanceToNextListener listener;


    /**
     * @param numColumns The number of columns in the grid, can be user defined
     * @param quickAdvance Whether to advance immediately after the image is clicked
     */
    public GridWidget(Context context, FormEntryPrompt prompt,
                      int numColumns, final boolean quickAdvance) {
        super(context, prompt);
        mItems = mPrompt.getSelectChoices();
        listener = (AdvanceToNextListener) context;

        selected = new boolean[mItems.size()];
        choices = new String[mItems.size()];
        gridview = new GridView(context);
        imageViews = new ImageView[mItems.size()];

        // The max width of an icon in a given column. Used to line
        // up the columns and automatically fit the columns in when
        // they are chosen automatically
        int maxColumnWidth = -1;

        for (int i = 0; i < mItems.size(); i++) {
            imageViews[i] = new ImageView(getContext());
        }

        // Build view
        for (int i = 0; i < mItems.size(); i++) {
            SelectChoice sc = mItems.get(i);
            // Read the image sizes and set maxColumnWidth. This allows us to make sure all of our
            // columns are going to fit
            String imageURI =
                mPrompt.getSpecialFormSelectChoiceText(sc, FormEntryCaption.TEXT_FORM_IMAGE);

            if (imageURI != null) {
                choices[i] = imageURI;

                String imageFilename;
                try {
                    imageFilename = ReferenceManager._().DeriveReference(imageURI).getLocalURI();
                    final File imageFile = new File(imageFilename);
                    if (imageFile.exists()) {
                        Display display =
                            ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                                    .getDefaultDisplay();
                        int screenWidth = display.getWidth();
                        int screenHeight = display.getHeight();
                        Bitmap b =
                                MediaUtil
                                    .getBitmapScaledToContainer(imageFile, screenHeight, screenWidth);
                        if (b != null) {

                            if (b.getWidth() > maxColumnWidth) {
                                maxColumnWidth = b.getWidth();
                            }

                        }
                    }
                } catch (InvalidReferenceException e) {
                    Log.e("GridWidget", "image invalid reference exception");
                    e.printStackTrace();
                }

            }
        }

        // Use the custom image adapter and initialize the grid view
        ImageAdapter ia = new ImageAdapter(getContext(), choices, imageViews);
        gridview.setAdapter(ia);
        gridview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                // Imitate the behavior of a radio button. Clear all buttons
                // and then check the one clicked by the user. Update the
                // background color accordingly
                for (int i = 0; i < selected.length; i++) {
                    selected[i] = false;
                    if (imageViews[i] != null) {
                        imageViews[i].setBackgroundColor(Color.WHITE);
                    }
                }
                selected[position] = true;
                imageViews[position].setBackgroundColor(Color.rgb(orangeRedVal, orangeGreenVal,
                        orangeBlueVal));
                if (quickAdvance) {
                    listener.advance();
                }
            }
        });

        // Read the screen dimensions and fit the grid view to them. It is important that the grid
        // view
        // knows how far out it can stretch.
        Display display =
            ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();
        GridView.LayoutParams params = new GridView.LayoutParams(screenWidth - 5, screenHeight - 5);
        gridview.setLayoutParams(params);

        // Use the user's choice for num columns, otherwise automatically decide.
        if (numColumns > 0) {
            gridview.setNumColumns(numColumns);
        } else {
            gridview.setNumColumns(GridView.AUTO_FIT);
        }

        gridview.setColumnWidth(maxColumnWidth);
        gridview.setHorizontalSpacing(2);
        gridview.setVerticalSpacing(2);
        gridview.setGravity(Gravity.LEFT);
        gridview.setStretchMode(GridView.NO_STRETCH);

        // Fill in answer
        String s = null;
        if (mPrompt.getAnswerValue() != null) {
            s = ((Selection) mPrompt.getAnswerValue().getValue()).getValue();
        }

        for (int i = 0; i < mItems.size(); ++i) {
            String sMatch = mItems.get(i).getValue();

            selected[i] = sMatch.equals(s);
            if (selected[i]) {
                imageViews[i].setBackgroundColor(Color.rgb(orangeRedVal, orangeGreenVal,
                    orangeBlueVal));
            } else {
                imageViews[i].setBackgroundColor(Color.WHITE);
            }
        }

        addView(gridview);
    }
    
    @Override
    public IAnswerData getAnswer() {
        for (int i = 0; i < choices.length; ++i) {
            if (selected[i]) {
                SelectChoice sc = mItems.elementAt(i);
                return new SelectOneData(new Selection(sc));
            }
        }
        return null;
    }

    @Override
    public void clearAnswer() {
        for (int i = 0; i < mItems.size(); ++i) {
            selected[i] = false;
            imageViews[i].setBackgroundColor(Color.WHITE);
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
    public void setOnLongClickListener(OnLongClickListener l) {
        gridview.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        gridview.cancelLongPress();
    }
}
