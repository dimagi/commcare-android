/**
 *
 */
package org.commcare.views;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.support.v4.util.Pair;
import android.support.v7.widget.GridLayout;
import android.support.v7.widget.Space;
import android.text.Spannable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.models.AsyncEntity;
import org.commcare.models.Entity;
import org.commcare.suite.model.Detail;
import org.commcare.util.GridCoordinate;
import org.commcare.util.GridStyle;
import org.commcare.utils.CachingAsyncImageLoader;
import org.commcare.utils.MarkupUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.views.media.AudioButton;
import org.commcare.views.media.ViewId;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathUnhandledException;

import java.util.Arrays;

/**
 * @author wspride
 *         This class defines an individual panel within an advanced case list.
 *         Each panel is defined by a Detail and an Entity
 *         Significant axis of configuration are NUMBER_ROWS, NUMBER_COLUMNS, AND CELL_HEIGHT_DIVISOR defined below
 */
public class EntityViewTile extends GridLayout {

    private String[] searchTerms;

    // All of the views that are being shown in this tile, one for each field of the entity's detail
    private View[] mFieldViews;

    private boolean mFuzzySearchEnabled = false;
    private boolean mIsAsynchronous = false;

    // load the screen-size-dependent font sizes
    private final float SMALL_FONT = getResources().getDimension(R.dimen.font_size_small);
    private final float MEDIUM_FONT = getResources().getDimension(R.dimen.font_size_medium);
    private final float LARGE_FONT = getResources().getDimension(R.dimen.font_size_large);
    private final float XLARGE_FONT = getResources().getDimension(R.dimen.font_size_xlarge);
    private final float DENSITY = getResources().getDisplayMetrics().density;

    private final int CELL_PADDING_HORIZONTAL = (int)getResources().getDimension(R.dimen.cell_padding_horizontal);
    private final int CELL_PADDING_VERTICAL = (int)getResources().getDimension(R.dimen.cell_padding_vertical);

    private final int DEFAULT_TILE_PADDING_HORIZONTAL =
            (int)getResources().getDimension(R.dimen.row_padding_horizontal);
    private final int DEFAULT_TILE_PADDING_VERTICAL =
            (int)getResources().getDimension(R.dimen.row_padding_vertical);

    // constants used to calibrate how many tiles should be shown on a screen
    private static final int DEFAULT_NUMBER_ROWS_PER_GRID = 6;
    private static final double DEFAULT_NUM_GRIDS_PER_SCREEN_PORTRAIT = 7;
    private static final double LANDSCAPE_TO_PORTRAIT_RATIO = .75;

    // this is fixed for all tiles
    private static final int NUMBER_COLUMNS_PER_GRID = 12;

    private final int numRowsPerTile;
    private final int numTilesPerRow;

    private double cellWidth;
    private double cellHeight;

    private final CachingAsyncImageLoader mImageLoader;

    /**
     * Used to create an entity view tile outside of a managed context (like
     * for an individual entity out of a search context).
     */
    public static EntityViewTile createTileForIndividualDisplay(Context context, Detail detail,
                                                                Entity entity) {
        return new EntityViewTile(context, detail, entity, new String[0],
                new CachingAsyncImageLoader(context), false, 1);
    }

    public static EntityViewTile createTileForListDisplay(Context context, Detail detail, Entity entity,
                                                          String[] searchTerms,
                                                          CachingAsyncImageLoader loader,
                                                          boolean fuzzySearchEnabled) {
        return new EntityViewTile(context, detail, entity, searchTerms, loader, fuzzySearchEnabled, 1);
    }

    public static EntityViewTile createTileForGridDisplay(Context context, Detail detail, Entity entity,
                                                          String[] searchTerms,
                                                          CachingAsyncImageLoader loader,
                                                          boolean fuzzySearchEnabled, int numRowsPerGrid) {
        return new EntityViewTile(context, detail, entity, searchTerms, loader,
                fuzzySearchEnabled, numRowsPerGrid);
    }

    /**
     * Constructor for an entity tile in a managed context, like a list of entities being displayed
     * all at once for searching.
     */
    private EntityViewTile(Context context, Detail detail, Entity entity, String[] searchTerms,
                          CachingAsyncImageLoader loader, boolean fuzzySearchEnabled, int numTilesPerRow) {
        super(context);
        this.searchTerms = searchTerms;
        this.mIsAsynchronous = entity instanceof AsyncEntity;
        this.mImageLoader = loader;
        this.mFuzzySearchEnabled = fuzzySearchEnabled;
        this.numRowsPerTile = getMaxRows(detail);
        this.numTilesPerRow = numTilesPerRow;

        setEssentialGridLayoutValues();
        setCellWidthAndHeight(context, detail);
        addFieldViews(context, detail, entity);
    }

    /**
     * @return the maximum height of the grid view for the given detail
     */
    private static int getMaxRows(Detail detail) {
        GridCoordinate[] coordinates = detail.getGridCoordinates();
        int currentMaxHeight = 0;
        for (GridCoordinate coordinate : coordinates) {
            int yCoordinate = coordinate.getY();
            int height = coordinate.getHeight();
            int maxHeight = yCoordinate + height;
            if (maxHeight > currentMaxHeight) {
                currentMaxHeight = maxHeight;
            }
        }
        return currentMaxHeight;
    }

    private void setEssentialGridLayoutValues() {
        setColumnCount(NUMBER_COLUMNS_PER_GRID);
        setRowCount(numRowsPerTile);
        setPaddingIfNotInGridView();
    }

    private void setCellWidthAndHeight(Context context, Detail detail) {
        Pair<Integer, Integer> tileWidthAndHeight = computeTileWidthAndHeight(context);
        cellWidth = tileWidthAndHeight.first / (double)NUMBER_COLUMNS_PER_GRID;
        if (detail.useUniformUnitsInCaseTile()) {
            cellHeight = cellWidth;
        } else {
            cellHeight = tileWidthAndHeight.second / (double)numRowsPerTile;
        }
    }

    /**
     * Compute what the width and height of a single tile should be, based upon the available
     * screen space, how many columns there should be, and how many rows we want to show at a time.
     * Round up to the nearest integer since the GridView's width and height will ultimately be
     * computed indirectly from these values, and those values need to be integers, and we don't
     * want to end up cutting things off
     */
    private Pair<Integer, Integer> computeTileWidthAndHeight(Context context) {
        double screenWidth, screenHeight;
        Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            display.getSize(size);
            screenWidth = size.x;
            screenHeight = size.y;
        } else {
            screenWidth = display.getWidth();
            screenHeight = display.getHeight();
        }

        if (!tileBeingShownInGridView()) {
            // If we added padding, subtract that space since we can't use it
            screenWidth = screenWidth - DEFAULT_TILE_PADDING_HORIZONTAL * 2;
        }

        // Calibrate the number of tiles that appear on the screen, based on how many rows are in
        // each tile
        double numTilesPerScreenPortrait = DEFAULT_NUM_GRIDS_PER_SCREEN_PORTRAIT *
                (DEFAULT_NUMBER_ROWS_PER_GRID / (float) numRowsPerTile);
        double numTilesPerScreenLandscape = numTilesPerScreenPortrait * LANDSCAPE_TO_PORTRAIT_RATIO;
        double densityRowMultiplier = getDensityRowMultiplier();

        int tileHeight;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (context.getString(R.string.panes).equals("two")) {
                // if in awesome mode, split available width in half
                screenWidth = screenWidth / 2;
            }
            tileHeight = (int)Math.ceil(screenHeight / (numTilesPerScreenLandscape * densityRowMultiplier));
        } else {
            tileHeight = (int)Math.ceil(screenHeight / (numTilesPerScreenPortrait * densityRowMultiplier));
        }

        int tileWidth = (int)Math.ceil(screenWidth / numTilesPerRow);

        return new Pair<>(tileWidth, tileHeight);
    }

    private double getDensityRowMultiplier() {
        // Get density metrics
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int densityDpi = metrics.densityDpi;
        int defaultDensityDpi = DisplayMetrics.DENSITY_MEDIUM;

        // For every additional 160dpi, show one more grid view on the screen
        double extraDensity = (int) ((densityDpi - defaultDensityDpi) / 80) * 0.5;
        return 1 + extraDensity;
    }

    /**
     * Add Spaces to this GridLayout to strictly enforce that Grid columns and rows stay the width/height we want
     * Android GridLayout tries to be very "smart" about moving entries placed arbitrarily within the grid so that
     * they look "ordered" even though this often ends up looking not how we want it. It will only make these adjustments
     * to rows/columns that end up empty or near empty, so we solve this by adding spaces to every row and column.
     * We just add a space of width cellWidth and height 1 to every column of the first row, and likewise a sapce of height
     * cellHeight and width 1 to every row of the first column. These are then written on top of if need be.
     */
    private void addBuffers(Context context) {

        for (int i = 0; i < numRowsPerTile; i++) {

            Spec rowSpec = GridLayout.spec(i);
            Spec colSpec = GridLayout.spec(0);

            GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, colSpec);
            mGridParams.width = 1;
            mGridParams.height = (int) cellHeight;

            Space mSpace = new Space(context);
            mSpace.setLayoutParams(mGridParams);
            this.addView(mSpace, mGridParams);
        }

        for (int i = 0; i < NUMBER_COLUMNS_PER_GRID; i++) {

            Spec rowSpec = GridLayout.spec(0);
            Spec colSpec = GridLayout.spec(i);

            GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, colSpec);
            mGridParams.width = (int) cellWidth;
            mGridParams.height = 1;

            Space mSpace = new Space(context);
            mSpace.setLayoutParams(mGridParams);
            this.addView(mSpace, mGridParams);
        }

    }

    /**
     * Add the view for each field in the detail
     *
     * @param detail  - the Detail describing how to display each entry
     * @param entity  - the Entity describing the actual data of each entry
     */
    public void addFieldViews(Context context, Detail detail, Entity entity) {
        this.removeAllViews();
        addBuffers(context);  // add spacers to enforce regularized column and row size

        GridCoordinate[] coordinatesOfEachField = detail.getGridCoordinates();
        String[] typesOfEachField = detail.getTemplateForms();
        GridStyle[] stylesOfEachField = detail.getGridStyles();

        Log.v("TempForms", "Template: " + Arrays.toString(typesOfEachField) +
                " | Coords: " + Arrays.toString(coordinatesOfEachField) +
                " | Styles: " + Arrays.toString(stylesOfEachField));

        setPaddingIfNotInGridView();

        if (tileBeingShownInGridView()) {
            // We are faking dividers between each square in the grid view by using contrasting
            // background colors for the grid view as a whole and each element in the grid view
            setBackgroundColor(Color.parseColor("#ffffff"));
        }

        mFieldViews = new View[coordinatesOfEachField.length];
        for (int i = 0; i < mFieldViews.length; i++) {
            addFieldView(context, typesOfEachField[i], stylesOfEachField[i],
                    coordinatesOfEachField[i], entity.getFieldString(i), entity.getSortField(i), i);
        }
    }

    private void addFieldView(Context context, String form,
                              GridStyle style, GridCoordinate coordinateData, String fieldString,
                              String sortField, int index) {
        if (coordinatesInvalid(coordinateData)) {
            return;
        }

        ViewId uniqueId = new ViewId(coordinateData.getX(), coordinateData.getY(), false);
        GridLayout.LayoutParams gridParams = getLayoutParamsForField(coordinateData);

        View view = getView(context, style, form, fieldString, uniqueId, sortField,
                gridParams.width, gridParams.height);

        if (!(view instanceof ImageView)) {
            gridParams.height = LayoutParams.WRAP_CONTENT;
        }

        view.setLayoutParams(gridParams);
        mFieldViews[index] = view;
        this.addView(view, gridParams);
    }

    private GridLayout.LayoutParams getLayoutParamsForField(GridCoordinate coordinateData) {
        Spec columnSpec = GridLayout.spec(coordinateData.getX(), coordinateData.getWidth());
        Spec rowSpec = GridLayout.spec(coordinateData.getY(), coordinateData.getHeight());

        GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams(rowSpec, columnSpec);
        gridParams.width = (int)Math.ceil(cellWidth * coordinateData.getWidth());
        gridParams.height = (int)Math.ceil(cellHeight * coordinateData.getHeight());
                // we need to account for any padding that wouldn't be in these rows if the entity didn't overwrite
                //+ (2 * CELL_PADDING_VERTICAL * (coordinateData.getHeight() - 1)));

        return gridParams;
    }

    private boolean coordinatesInvalid(GridCoordinate coordinate) {
        if (coordinate.getX() + coordinate.getWidth() > NUMBER_COLUMNS_PER_GRID ||
                coordinate.getY() + coordinate.getHeight() > numRowsPerTile) {
            Logger.log("e", "Grid entry dimensions exceed allotted sizes");
            throw new XPathUnhandledException("grid coordinates out of bounds: " +
                    coordinate.getX() + " " + coordinate.getWidth() + ", " +
                    coordinate.getY() + " " + coordinate.getHeight());
        }
        return (coordinate.getX() < 0 || coordinate.getY() < 0);
    }

    /**
     * Get the correct View for this particular activity.
     *
     * @param multimediaType either "image", "audio", or default text. Describes how this XPath result should be displayed.
     * @param rowData        The actual data to display, either an XPath to media or a String to display.
     */
    private View getView(Context context, GridStyle style, String multimediaType, String rowData,
                         ViewId uniqueId, String searchField, int maxWidth, int maxHeight) {

        // How the text should be aligned horizontally - left, center, or right
        String horzAlign = style.getHorzAlign();
        // How the text should be aligned vertically - top, center, or bottom
        String vertAlign = style.getVertAlign();

        View retVal;
        switch (multimediaType) {
            case EntityView.FORM_BORDER:
                retVal = new ImageView(context);
                retVal.setBackgroundColor(getResources().getColor(R.color.black));
                setScaleType((ImageView)retVal, horzAlign);
                break;
            case EntityView.FORM_IMAGE:
                retVal = new ImageView(context);
                setScaleType((ImageView)retVal, horzAlign);
                //retVal.setPadding(CELL_PADDING_HORIZONTAL, CELL_PADDING_VERTICAL, CELL_PADDING_HORIZONTAL, CELL_PADDING_VERTICAL);
                if (rowData != null && !rowData.equals("")) {
                    if (mImageLoader != null) {
                        mImageLoader.display(rowData, ((ImageView) retVal), R.drawable.info_bubble,
                                maxWidth, maxHeight);
                    } else {
                        Bitmap b = MediaUtil.inflateDisplayImage(getContext(), rowData,
                                maxWidth, maxHeight);
                        ((ImageView) retVal).setImageBitmap(b);
                    }
                }
                break;
            case EntityView.FORM_AUDIO:
                if (rowData != null & rowData.length() > 0) {
                    retVal = new AudioButton(context, rowData, uniqueId, true);
                } else {
                    retVal = new AudioButton(context, rowData, uniqueId, false);
                }
                break;
            default:
                retVal = new TextView(context);

                //the html spanner currently does weird stuff like converts "a  a" into "a a"
                //so we've gotta mirror that for the search text. Booooo. I dunno if there's any
                //other other side effects (newlines? nbsp?)

                String htmlIfiedSearchField = searchField == null ? searchField : MarkupUtil.getSpannable(searchField).toString();

                String cssid = style.getCssID();
                if (cssid != null && !cssid.equals("none")) {
                    // user defined a style we want to use
                    Spannable mSpannable = MarkupUtil.getCustomSpannable(cssid, rowData);
                    EntityView.highlightSearches(searchTerms, mSpannable, htmlIfiedSearchField, mFuzzySearchEnabled, mIsAsynchronous);
                    ((TextView) retVal).setText(mSpannable);
                } else {
                    // just process inline markup
                    Spannable mSpannable = MarkupUtil.returnCSS(rowData);
                    EntityView.highlightSearches(searchTerms, mSpannable, htmlIfiedSearchField, mFuzzySearchEnabled, mIsAsynchronous);
                    ((TextView) retVal).setText(mSpannable);
                }

                int gravity = 0;

                // handle horizontal alignments
                switch (horzAlign) {
                    case "center":
                        gravity |= Gravity.CENTER_HORIZONTAL;
                        break;
                    case "left":
                        gravity |= Gravity.LEFT;
                        break;
                    case "right":
                        gravity |= Gravity.RIGHT;
                        break;
                }
                // handle vertical alignment
                switch (vertAlign) {
                    case "center":
                        gravity |= Gravity.CENTER_VERTICAL;
                        break;
                    case "top":
                        gravity |= Gravity.TOP;
                        break;
                    case "bottom":
                        gravity |= Gravity.BOTTOM;
                        break;
                }

                if(gravity != 0) {
                    ((TextView) retVal).setGravity(gravity);
                }

                // handle text resizing
                switch (style.getFontSize()) {
                    case "large":
                        ((TextView) retVal).setTextSize(LARGE_FONT / DENSITY);
                        break;
                    case "small":
                        ((TextView) retVal).setTextSize(SMALL_FONT / DENSITY);
                        break;
                    case "medium":
                        ((TextView) retVal).setTextSize(MEDIUM_FONT / DENSITY);
                        break;
                    case "xlarge":
                        ((TextView) retVal).setTextSize(XLARGE_FONT / DENSITY);
                        break;
                }
        }
        return retVal;
    }

    private static void setScaleType(ImageView imageView, String horizontalAlignment) {
        switch (horizontalAlignment) {
            case "center":
                imageView.setScaleType(ScaleType.CENTER_INSIDE);
                break;
            case "left":
                imageView.setScaleType(ScaleType.FIT_START);
                break;
            case "right":
                imageView.setScaleType(ScaleType.FIT_END);
                break;
        }
    }

    public void setSearchTerms(String[] currentSearchTerms) {
        this.searchTerms = currentSearchTerms;
    }

    public void setTextColor(int color) {
        for (View rowView : mFieldViews) {
            if (rowView instanceof TextView) {
                ((TextView)rowView).setTextColor(color);
            }
        }
    }

    public void setTitleTextColor(int color) {
        for (View rowView : mFieldViews) {
            if (rowView instanceof TextView) {
                ((TextView)rowView).setTextColor(color);
                return;
            }
        }
    }

    private boolean tileBeingShownInGridView() {
        return numTilesPerRow > 1;
    }

    private void setPaddingIfNotInGridView() {
        if (!tileBeingShownInGridView()) {
            setPadding(DEFAULT_TILE_PADDING_HORIZONTAL, DEFAULT_TILE_PADDING_VERTICAL,
                    DEFAULT_TILE_PADDING_HORIZONTAL, DEFAULT_TILE_PADDING_VERTICAL);
        }
    }
}
