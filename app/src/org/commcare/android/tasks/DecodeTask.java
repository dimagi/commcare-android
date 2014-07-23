package org.commcare.android.tasks;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

@SuppressLint("NewApi")
public class DecodeTask extends AsyncTask<String, Void, Bitmap> {

	private static int MaxTextureSize = 2048; /* True for most devices. */

	public ImageView v;

	public DecodeTask(ImageView iv) {
		v = iv;
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

		opt.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(params[0], opt);
		while(opt.outHeight > MaxTextureSize || opt.outWidth > MaxTextureSize) {
			opt.inSampleSize++;
			BitmapFactory.decodeFile(params[0], opt);
		}
		opt.inJustDecodeBounds = false;

		bitmap = BitmapFactory.decodeFile(params[0], opt);
		return bitmap;
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		if(v != null) {
			v.setImageBitmap(result);
		}
	}

}