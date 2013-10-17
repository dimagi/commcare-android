/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.commcare.android.framework;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.javarosa.core.services.Logger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
@SuppressLint("NewApi")
public class FileServerFragment extends Fragment {

	protected static final int CHOOSE_FILE_RESULT_CODE = 20;
	private View mContentView = null;
	ProgressDialog progressDialog = null;

	private static CommCareWiFiDirectActivity mActivity;

	private TextView mStatusText;
	private View mView;

	public static String receiveZipDirectory;

	private FileServerAsyncTask mFileServer;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		try {
			mActivity = (CommCareWiFiDirectActivity) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement fileServerListener");
		}

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		mContentView = inflater.inflate(R.layout.file_server, null);

		mStatusText = (TextView)mContentView.findViewById(R.id.file_server_status_text);

		mView = (View)mContentView.findViewById(R.id.file_server_view);

		return mContentView;
	}


	public interface FileServerListener{
		public void onFormsCopied(String result);
	}


	public void startServer(String mReceiveZipDirectory){
		Log.d(CommCareWiFiDirectActivity.TAG, "File server starting...");
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "File Server starting...");

		mStatusText.setText("Starting server");

		mView.setVisibility(View.VISIBLE);

		if(mFileServer != null){
			mFileServer.cancel(true);
		}

		mFileServer = new FileServerAsyncTask(this);

		receiveZipDirectory = mReceiveZipDirectory;

		//Execute on a true multithreaded chain. We should probably replace all of our calls with this
		//but this is the big one for now.
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
			mFileServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
			mFileServer.execute();
		}
	}


	/**
	 * A simple server socket that accepts connection and writes some data on
	 * the stream.
	 */
	public static class FileServerAsyncTask extends AsyncTask<Void, String, String> {

		private TextView statusText;
		private FileServerFragment mListener;

		/**
		 * @param context
		 * @param statusText
		 */
		public FileServerAsyncTask(FileServerFragment mListener) {
			Log.d(CommCareWiFiDirectActivity.TAG, "new fileasync task");
			this.statusText = mListener.mStatusText;
			this.mListener = mListener;

		}

		@Override
		protected String doInBackground(Void... params) {

			Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "doing in background");

			try{
				ServerSocket serverSocket = new ServerSocket(8988);

				try {
					publishProgress("Ready to accept new file transfer.", null);

					Socket client = serverSocket.accept();

					Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Ready in wi-fi direct file server receive loop");

					long time = System.currentTimeMillis();

					String finalFileName = receiveZipDirectory + time + ".zip";

					Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + finalFileName);

					final File f = new File(finalFileName);

					File dirs = new File(f.getParent());
					if (!dirs.exists()){
						dirs.mkdirs();
					}
					f.createNewFile();

					Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + f.toString());
					InputStream inputstream = client.getInputStream();
					copyFile(inputstream, new FileOutputStream(f));
					serverSocket.close();
					publishProgress("copied files: " + f.getAbsolutePath(), f.getAbsolutePath());
					publishProgress("File Server Resetting", null);
					return f.getAbsolutePath();

				}catch (IOException e) {
					Log.e(CommCareWiFiDirectActivity.TAG, e.getMessage());
					publishProgress("File Server crashed with an IO Exception: " + e.getMessage());
					return null;
				}finally{
					serverSocket.close();
				}
			}catch(IOException ioe){
				publishProgress("Ready to accept new file transfer.", null);
				Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "couldn't open socket!");
				return "socket";
			}
		}

		/*
		 * (non-Javadoc)
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(String result) {
			Log.e(CommCareWiFiDirectActivity.TAG, "file server task post execute");
			
			if(result.equals("socket")){
				Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "socket busy, cancelling this thread cycle");
				return;
			}
			
			if(result != null){
				mActivity.onFormsCopied(result);
			}
			Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "file server post-execute, relaunching server");
			mListener.startServer(receiveZipDirectory);
		}

		/*
		 * (non-Javadoc)
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onPreExecute() {
			Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "pre-execute of file server launch");
			// statusText.setText("Opening a server socket");
		}

		/*
		 * (non-Javadoc)
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onProgressUpdate(String ... params){
			statusText.setText(params[0]);
		}

	}

	public static boolean copyFile(InputStream inputStream, OutputStream out) {
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "File server copying file");
		Log.d(CommCareWiFiDirectActivity.TAG, "Copying file");
		if(inputStream == null){
			Log.d(CommCareWiFiDirectActivity.TAG, "Input Null");
		}
		byte buf[] = new byte[1024];
		int len;
		try {
			while ((len = inputStream.read(buf)) != -1) {
				Log.d(CommCareWiFiDirectActivity.TAG, "Copying file : " + new String(buf));
				out.write(buf, 0, len);

			}
			out.close();
			inputStream.close();
		} catch (IOException e) {
			Log.d(CommCareWiFiDirectActivity.TAG, e.toString());
			Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Copy in File Server failed");
			return false;
		}
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Copy in File Server successful");
		return true;
	}

}
