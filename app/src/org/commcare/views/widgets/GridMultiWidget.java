package org.commcare.views.widgets;

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

import org.commcare.adapters.ImageAdapter;
import org.commcare.utils.MediaUtil;
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
 * GridWidget handles multiple selection fields using a grid of icons. The user clicks the desired
 * icon and the background changes from black to orange. If text, audio or video are specified in
 * the select answers they are ignored. This is almost identical to GridWidget, except multiple
 * icons can be selected simultaneously.
 *
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class GridMultiWidget extends QuestionWidget {
    private final Vector<SelectChoice> mItems;

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

    /**
     * @param numColumns The number of columns in the grid, can be user defined
     */
    @SuppressWarnings("unchecked")
    public GridMultiWidget(Context context, FormEntryPrompt prompt, int numColumns) {
        super(context, prompt);
        mItems = mPrompt.getSelectChoices();

        selected = new boolean[mItems.size()];
        String[] choices = new String[mItems.size()];
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
                    prompt.getSpecialFormSelectChoiceText(sc, FormEntryCaption.TEXT_FORM_IMAGE);

            if (imageURI != null) {
                choices[i] = imageURI;

                String imageFilename;
                try {
                    imageFilename = ReferenceManager._().DeriveReference(imageURI).getLocalURI();
                    final File imageFile = new File(imageFilename);
                    if (imageFile.exists()) {
                        Display display =
                                ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
                                        .getDefaultDisplay();
                        int screenWidth = display.getWidth();
                        int screenHeight = display.getHeight();
                        Bitmap b = MediaUtil
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
            } else {
                choices[i] = prompt.getSelectChoiceText(sc);
            }
        }

        // Use the custom image adapter and initialize the grid view
        ImageAdapter ia = new ImageAdapter(getContext(), choices, imageViews);
        gridview.setAdapter(ia);
        gridview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                if (selected[position]) {
                    selected[position] = false;
                    imageViews[position].setBackgroundColor(Color.WHITE);
                } else {
                    selected[position] = true;
                    imageViews[position].setBackgroundColor(Color.rgb(orangeRedVal, orangeGreenVal,
                            orangeBlueVal));
                }

                widgetEntryChanged();
            }
        });

        // Read the screen dimensions and fit the grid view to them. It is important that the grid
        // view
        // knows how far out it can stretch.
        Display display =
                ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
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
        IAnswerData answer = prompt.getAnswerValue();
        Vector<Selection> ve;
        if ((answer == null) || (answer.getValue() == null)) {
            ve = new Vector<>();
        } else {
            ve = (Vector<Selection>)answer.getValue();
        }

        for (int i = 0; i < choices.length; ++i) {

            String value = mItems.get(i).getValue();
            boolean found = false;
            for (Selection s : ve) {
                if (value.equals(s.getValue())) {
                    found = true;
                    break;
                }
            }

            selected[i] = found;
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
        Vector<Selection> vc = new Vector<>();
        for (int i = 0; i < mItems.size(); i++) {
            if (selected[i]) {
                SelectChoice sc = mItems.get(i);
                vc.add(new Selection(sc));
            }
        }

        if (vc.size() == 0) {
            return null;
        } else {
            return new SelectMultiData(vc);
        }
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
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);

    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        gridview.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        gridview.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        gridview.cancelLongPress();
    }
}
