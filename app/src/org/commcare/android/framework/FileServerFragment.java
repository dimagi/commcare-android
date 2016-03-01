package org.commcare.android.framework;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
@SuppressLint("NewApi")
public class FileServerFragment extends Fragment {
    private static final String TAG = FileServerFragment.class.getSimpleName();

    private static CommCareWiFiDirectActivity mActivity;

    private static TextView mStatusText;
    private View mView;

    private static String receiveZipDirectory;

    private FileServerAsyncTask mFileServer;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mActivity = (CommCareWiFiDirectActivity)context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement fileServerListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView;
        contentView = inflater.inflate(R.layout.file_server, null);

        mStatusText = (TextView)contentView.findViewById(R.id.file_server_status_text);

        mView = contentView.findViewById(R.id.file_server_view);

        return contentView;
    }


    public interface FileServerListener {
        void onFormsCopied(String result);
    }

    public void startServer(String mReceiveZipDirectory) {
        Logger.log(TAG, "File Server starting...");

        mStatusText.setText("Starting server");

        mView.setVisibility(View.VISIBLE);

        if (mFileServer != null) {
            mFileServer.cancel(true);
        }

        mFileServer = new FileServerAsyncTask(this);

        receiveZipDirectory = mReceiveZipDirectory;

        //Execute on a true multithreaded chain. We should probably replace all of our calls with this
        //but this is the big one for now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
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

        private final FileServerFragment mListener;
        private boolean socketOccupied;

        public FileServerAsyncTask(FileServerFragment mListener) {
            Log.d(TAG, "new fileasync task");
            this.mListener = mListener;

        }

        @Override
        protected String doInBackground(Void... params) {

            Logger.log(TAG, "doing in background");
            socketOccupied = false;

            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                long time = System.currentTimeMillis();
                String finalFileName = receiveZipDirectory + time + ".zip";

                try {
                    publishProgress("Ready to accept new file transfer.", null);

                    Socket client = serverSocket.accept();

                    Logger.log(TAG, "Ready in wi-fi direct file server receive loop");

                    Log.d(TAG, "server: copying files " + finalFileName);

                    final File f = new File(finalFileName);

                    File dirs = new File(f.getParent());

                    dirs.mkdirs();

                    f.createNewFile();

                    Log.d(TAG, "server: copying files " + f.toString());
                    InputStream inputstream = client.getInputStream();
                    CommCareWiFiDirectActivity.copyFile(inputstream, new FileOutputStream(f));
                    serverSocket.close();
                    publishProgress("copied files: " + f.getAbsolutePath(), f.getAbsolutePath());
                    publishProgress("File Server Resetting", null);
                    return f.getAbsolutePath();

                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    final File f = new File(finalFileName);
                    if (f.exists()) {
                        FileUtil.deleteFileOrDir(f);
                    }
                    publishProgress("File Server crashed with an IO Exception: " + e.getMessage());
                    return null;
                } finally {
                    serverSocket.close();
                }
            } catch (IOException ioe) {
                publishProgress("Ready to accept new file transfer.", null);
                Logger.log(TAG, "couldn't open socket!");
                socketOccupied = true;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.e(TAG, "file server task post execute");

            if (socketOccupied) {
                Logger.log(TAG, "socket busy, cancelling this thread cycle");
                return;
            }

            if (result != null) {
                mActivity.onFormsCopied(result);
            }
            Logger.log(TAG, "file server post-execute, relaunching server");
            mListener.startServer(receiveZipDirectory);
        }

        @Override
        protected void onPreExecute() {
            Logger.log(TAG, "pre-execute of file server launch");
            // statusText.setText("Opening a server socket");
        }

        @Override
        protected void onProgressUpdate(String... params) {
            mStatusText.setText(params[0]);
        }

    }

}
