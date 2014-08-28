/**
 * 
 */
package org.commcare.android.view;

import java.util.ArrayList;

import net.nightwhistler.htmlspanner.Stylizer;

import org.commcare.android.models.Entity;
import org.commcare.android.util.CachingAsyncImageLoader;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.EntitySelectActivity;
import org.commcare.suite.model.Detail;
import org.commcare.util.GridCoordinate;
import org.commcare.util.GridStyle;
import org.javarosa.core.services.Logger;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.AudioController;
import org.odk.collect.android.views.media.ViewId;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

/**
 * @author wspride
 * This class defines an individual panel within an advanced case list.
 * Each panel is defined by a Detail and an Entity
 * Significant axis of configuration are NUMBER_ROWS, NUMBER_COLUMNS, AND CELL_HEIGHT_DIVISOR defined below
 *
 */
@SuppressLint("NewApi")
public class AdvancedEntityView extends GridLayout {

	private String[] forms;
	private String[] searchTerms;
	private GridCoordinate[] coords;
	private GridStyle[] styles;
	String[] mRowData;
	
	public final int PADDING_VERTICAL = 1;														// vertical padding between each grid entity
	public final int PADDING_HORIZONTAL = 10;													// horizontal padding between each grid entry
	public final float SMALL_FONT = getResources().getDimension(R.dimen.font_size_small);		// load the screen-size dependent font sizes
	public final float MEDIUM_FONT = getResources().getDimension(R.dimen.font_size_medium);	
	public final float LARGE_FONT = getResources().getDimension(R.dimen.font_size_large);
	public final float XLARGE_FONT = getResources().getDimension(R.dimen.font_size_xlarge);
	public final float DENSITY = getResources().getDisplayMetrics().density;
	
	public final int NUMBER_ROWS = 6;															// number of rows per screen (absolute screen size)
	public final int NUMBER_COLUMNS = 12;														// number of columns each A.E.View is divided into
	public final double CELL_HEIGHT_DIVISOR_TALL = 6;													// number of rows each A.E.View is divided into
	public final double CELL_HEIGHT_DIVISOR_WIDE = 4;	
	
	public double densityRowMultiplier = 1;;
	
	public double cellWidth;
	public double cellHeight;
	
	public double screenWidth;
	public double screenHeight;
	public double rowHeight;
	public double rowWidth;
	private CachingAsyncImageLoader mImageLoader;															// image loader used for all asyncronous imageView loading
	private AudioController controller;
	
	public AdvancedEntityView(Context context, Detail detail, Entity entity, String[] searchTerms, CachingAsyncImageLoader mLoader, AudioController controller) {
		super(context);
		this.searchTerms = searchTerms;
		this.controller = controller;
		this.setColumnCount(NUMBER_COLUMNS);
		this.setRowCount(NUMBER_ROWS);
		
		// get cell dimensions
		Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int rotation = display.getRotation();
		
		// get density metrics
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		
		int densityDpi = metrics.densityDpi;
		
		if(densityDpi == DisplayMetrics.DENSITY_XHIGH){
			densityRowMultiplier = 2.0;
		} else if(densityDpi == DisplayMetrics.DENSITY_HIGH){
			densityRowMultiplier = 1.5;
		} else if(densityDpi == DisplayMetrics.DENSITY_MEDIUM){

		}
		
		//setup all the various dimensions we need
		Point size = new Point();
		((Activity)context).getWindowManager().getDefaultDisplay().getSize(size);
		
		screenWidth = size.x-1;
		screenHeight = size.y-1;
		
		// If screen is rotated, use width for cell height measurement
		if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
			
			if(((EntitySelectActivity)context).inAwesomeMode()){
				screenWidth = screenWidth/2;
			}
			
			rowHeight = screenHeight/(CELL_HEIGHT_DIVISOR_WIDE*densityRowMultiplier);
			rowWidth = screenWidth;
			
		} else{
			rowHeight = screenHeight/(CELL_HEIGHT_DIVISOR_TALL*densityRowMultiplier);
			rowWidth = screenWidth;
		}
		
		mImageLoader = mLoader;
		cellWidth = rowWidth/NUMBER_COLUMNS;
		cellHeight = rowHeight / NUMBER_ROWS;
		
		// now ready to setup all these views
		setViews(context, detail, entity);

	}
	/**
	 * Add Spaces to this GridLayout to strictly enforce that Grid columns and rows stay the width/height we want
	 * Android GridLayout tries to be very "smart" about moving entries placed arbitrarily within the grid so that
	 * they look "ordered" even though this often ends up looking not how we want it. It will only make these adjustments
	 * to rows/columns that end up empty or near empty, so we solve this by adding spaces to every row and column.
	 * We just add a space of width cellWidth and height 1 to every column of the first row, and likewise a sapce of height 
	 * cellHeight and width 1 to every row of the first column. These are then written on top of if need be.
	 * @param context
	 */
	public void addBuffers(Context context){
		
		for(int i=0; i<NUMBER_ROWS;i++){
			
			Spec rowSpec = GridLayout.spec(i);
			Spec colSpec = GridLayout.spec(0);
			
			GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, colSpec);
			mGridParams.width = 1;
			mGridParams.height = (int)cellHeight;
			
			Space mSpace = new Space(context);
			mSpace.setLayoutParams(mGridParams);
			this.addView(mSpace, mGridParams);
		}
		
		for(int i=0; i<NUMBER_COLUMNS;i++){
			
			Spec rowSpec = GridLayout.spec(0);
			Spec colSpec = GridLayout.spec(i);
			
			GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, colSpec);
			mGridParams.width = (int)cellWidth;
			mGridParams.height = 1;
			
			Space mSpace = new Space(context);
			mSpace.setLayoutParams(mGridParams);
			this.addView(mSpace, mGridParams);
		}
		
	}

	/**
	 * Set all the views to be displayed in this pane
	 * @param context
	 * @param detail - the Detail describing how to display each entry
	 * @param entity - the Entity describing the actual data of each entry
	 */
	public void setViews(Context context, Detail detail, Entity entity){
		
		// clear all previous entries in this grid
		this.removeAllViews();
		
		// add spacers to enforce regularized column and row size
		addBuffers(context);
		
		// extract UI information from detail and entity
		forms = detail.getTemplateForms();
		coords = detail.getGridCoordinates();
		styles = detail.getGridStyles();
		mRowData = entity.getData();

		// iterate through every entity to be inserted in this view
		for(int i=0; i<mRowData.length; i++){
			
			String multimediaType = forms[i];
			GridStyle mStyle = styles[i];
			GridCoordinate currentCoordinate = coords[i];

			// if X and Y coordinates haven't been set, skip this row
			// if span exceeds allotted dimensions, skip this row and log 
			if(currentCoordinate.getX()<0 || currentCoordinate.getY() <0){
			
				if(currentCoordinate.getX() + currentCoordinate.getWidth() > NUMBER_COLUMNS || 
						currentCoordinate.getY() + currentCoordinate.getHeight() > NUMBER_ROWS){
					Logger.log("e", "Grid entry dimensions exceed allotted sizes");
				}
				continue;
			}
			
			View mView;
			
			// these Specs set our span across rows and columns; first arg is root, second is span
			Spec columnSpec = GridLayout.spec(currentCoordinate.getX(), currentCoordinate.getWidth());
			Spec rowSpec = GridLayout.spec(currentCoordinate.getY(), currentCoordinate.getHeight());
			
			ViewId uniqueId = new ViewId(currentCoordinate.getX(), currentCoordinate.getY(), false);

			// setup our layout parameters
			GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, columnSpec);
			mGridParams.width = (int)cellWidth * currentCoordinate.getWidth();
			mGridParams.height = (int)cellHeight * currentCoordinate.getHeight();
			
			// get style attributes
			String horzAlign = mStyle.getHorzAlign();
			String vertAlign = mStyle.getVertAlign();
			String textsize = mStyle.getFontSize();
			String CssID = mStyle.getCssID();
			
			mView = getView(context, multimediaType, mGridParams, horzAlign, vertAlign, textsize, mRowData[i], uniqueId, CssID);

			mView.setLayoutParams(mGridParams);
		
			this.addView(mView, mGridParams);
		}
        
	}
	
	/**
	 * Get the correct View for this particular activity.
	 * @param context
	 * @param multimediaType either "image", "audio", or default text. Describes how this XPath result should be displayed.
	 * @param width the width, in number of cells, this entity should occupy in the grid
	 * @param height the height, in number of cells, this entity should occupy in the grid
	 * @param horzAlign How the text should be aligned horizontally - left, center, or right ONE OF horzAlign or vertAlign
	 * @param vertAlign How the text should be aligned vertically - top, center, or bottom ONE OF horzAlign or vertAlign
	 * @param textsize The font size, scaled for screen size. small, medium, large, xlarge accepted.
	 * @param rowData The actual data to display, either an XPath to media or a String to display
	 * @return
	 */
	private View getView(Context context, String multimediaType, GridLayout.LayoutParams mGridParams,  String horzAlign, String vertAlign, String textsize, String rowData, ViewId uniqueId, String cssid) {
		View retVal;
		int height = mGridParams.height;
		int width = mGridParams.width;
		if(multimediaType.equals(EntityView.FORM_IMAGE)){
			retVal = new ImageView(context);
			// image loading is handled asyncronously by the TCImageLoader class to allow smooth scrolling
			if(rowData != null && !rowData.equals("")){
				mImageLoader.display(rowData, ((ImageView)retVal), R.drawable.info_bubble);
			}
		} 
		else if(multimediaType.equals(EntityView.FORM_AUDIO)){
    		if (rowData != null & rowData.length() > 0) {
    			retVal = new AudioButton(context, rowData, uniqueId, controller, true);
    		}
    		else {
    			retVal = new AudioButton(context, rowData, uniqueId, controller, false);
    		}
		} else{
			retVal = new TextView(context);
			((TextView)retVal).setText(rowData);
			// handle horizontal alignment
			if(horzAlign.equals("center")){
				((TextView)retVal).setGravity(Gravity.CENTER_HORIZONTAL);
			} else if(horzAlign.equals("left")) {
				((TextView)retVal).setGravity(Gravity.TOP);
			} else if(horzAlign.equals("right")) {
				((TextView)retVal).setGravity(Gravity.RIGHT);
			}
			// handle vertical alignment
			if(vertAlign.equals("center")){
				((TextView)retVal).setGravity(Gravity.CENTER_VERTICAL);
			} else if(vertAlign.equals("top")) {
				((TextView)retVal).setGravity(Gravity.TOP);
			} else if(vertAlign.equals("bottom")) {
				((TextView)retVal).setGravity(Gravity.BOTTOM);
			}
			
			// handle text resizing
			if(textsize.equals("large")){
				((TextView)retVal).setTextSize(LARGE_FONT/DENSITY);
			}
			else if(textsize.equals("small")){
				((TextView)retVal).setTextSize(SMALL_FONT/DENSITY);
			} else if(textsize.equals("medium")){
				((TextView)retVal).setTextSize(MEDIUM_FONT/DENSITY);
			} else if(textsize.equals("xlarge")){
				((TextView)retVal).setTextSize(XLARGE_FONT/DENSITY);
			} 
			
			if(cssid != null && !cssid.equals("none")){
				((TextView)retVal).setText(Stylizer.getStyleSpannable(cssid,rowData));
			}
		}
		
		return retVal;
	}
	
	public void setSearchTerms(String[] terms) {
		this.searchTerms = terms;
	}
	
	public AdvancedEntityView(Context context, Detail d, String[] headerText) {
		super(context);
	}
}
