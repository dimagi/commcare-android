package org.commcare.android.tasks;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

/***
 * 
 * @author wspride
 *
 *  Asynchronous class for loading images into views outside of the main thread.
 *  Allows smooth scrolling in ListViews. 
 *
 */

@SuppressLint("NewApi")
public class DecodeTask extends AsyncTask<String, Void, Bitmap> {

	private static int MaxTextureSize = 2048; /* True for most devices. */

	private ImageView mImageView;

	public DecodeTask(ImageView iv) {
		mImageView = iv;
	}

	protected Bitmap doInBackground(String... params) {
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inPurgeable = true;
		opt.inPreferQualityOverSpeed = false;
		opt.inSampleSize = 1;

		Bitmap bitmap = null;
		if(isCancelled()) {
			return bitmap;
		}

		
		/*
		 * First, we set inJustDecodeBounds to true. This allows us to call decodeFile
		 * and get the resulting height, width, without allocating the pixels for the image
		 * After doing this, we continually query decodeFile, check to see if the resulting height or width
		 * is within our texture bounds and, if not, increase the sample size (reducing exponentially the size
		 * and number of pixels returned) and decode again until we are within the textureSize (so as to 
		 * not waste resolution). We then toggle inJustDecodeBounds to false, query the bitmap for real,
		 * and return the image.
		 */
		
		opt.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(params[0], opt);
		while(opt.outHeight > MaxTextureSize || opt.outWidth > MaxTextureSize) {
			opt.inSampleSize++;
			BitmapFactory.decodeFile(params[0], opt);
		}
		opt.inJustDecodeBounds = false;

		return BitmapFactory.decodeFile(params[0], opt);
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		if(mImageView != null) {
			mImageView.setImageBitmap(result);
		}
	}

}