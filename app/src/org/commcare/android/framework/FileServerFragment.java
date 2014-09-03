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
import org.commcare.android.util.FileUtil;
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

    private static TextView mStatusText;
    private View mView;

    public static String receiveZipDirectory;

    private FileServerAsyncTask mFileServer;

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onAttach(android.app.Activity)
     */
    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        try {
            mActivity = (CommCareWiFiDirectActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement fileServerListener");
        }

    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
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
        Logger.log(CommCareWiFiDirectActivity.TAG, "File Server starting...");

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

        private FileServerFragment mListener;
        private boolean socketOccupied;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(FileServerFragment mListener) {
            Log.d(CommCareWiFiDirectActivity.TAG, "new fileasync task");
            this.mListener = mListener;

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(java.lang.Object[])
         */
        @Override
        protected String doInBackground(Void... params) {

            Logger.log(CommCareWiFiDirectActivity.TAG, "doing in background");
            socketOccupied = false;

            try{
                ServerSocket serverSocket = new ServerSocket(8988);
                long time = System.currentTimeMillis();
                String finalFileName = receiveZipDirectory + time + ".zip";

                try {
                    publishProgress("Ready to accept new file transfer.", null);

                    Socket client = serverSocket.accept();

                    Logger.log(CommCareWiFiDirectActivity.TAG, "Ready in wi-fi direct file server receive loop");

                    Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + finalFileName);

                    final File f = new File(finalFileName);

                    File dirs = new File(f.getParent());
                    
                    dirs.mkdirs();
                    
                    f.createNewFile();

                    Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + f.toString());
                    InputStream inputstream = client.getInputStream();
                    CommCareWiFiDirectActivity.copyFile(inputstream, new FileOutputStream(f));
                    serverSocket.close();
                    publishProgress("copied files: " + f.getAbsolutePath(), f.getAbsolutePath());
                    publishProgress("File Server Resetting", null);
                    return f.getAbsolutePath();

                }catch (IOException e) {
                    Log.e(CommCareWiFiDirectActivity.TAG, e.getMessage());
                    final File f = new File(finalFileName);
                    if(f.exists()){
                        FileUtil.deleteFile(f);
                    }
                    publishProgress("File Server crashed with an IO Exception: " + e.getMessage());
                    return null;
                }finally{
                    serverSocket.close();
                }
            }catch(IOException ioe){
                publishProgress("Ready to accept new file transfer.", null);
                Logger.log(CommCareWiFiDirectActivity.TAG, "couldn't open socket!");
                socketOccupied = true;
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            Log.e(CommCareWiFiDirectActivity.TAG, "file server task post execute");
            
            if(socketOccupied){
                Logger.log(CommCareWiFiDirectActivity.TAG, "socket busy, cancelling this thread cycle");
                return;
            }
            
            if(result != null){
                mActivity.onFormsCopied(result);
            }
            Logger.log(CommCareWiFiDirectActivity.TAG, "file server post-execute, relaunching server");
            mListener.startServer(receiveZipDirectory);
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            Logger.log(CommCareWiFiDirectActivity.TAG, "pre-execute of file server launch");
            // statusText.setText("Opening a server socket");
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onProgressUpdate(String ... params){
            mStatusText.setText(params[0]);
        }

    }

}
