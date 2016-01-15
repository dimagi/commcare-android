package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;
import org.commcare.android.adapters.WiFiDirectAdapter;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.DeviceDetailFragment;
import org.commcare.android.framework.DeviceListFragment;
import org.commcare.android.framework.DeviceListFragment.DeviceActionListener;
import org.commcare.android.framework.FileServerFragment;
import org.commcare.android.framework.FileServerFragment.FileServerListener;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.WiFiDirectManagementFragment;
import org.commcare.android.framework.WiFiDirectManagementFragment.WifiDirectManagerListener;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.FormTransferTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.UnzipTask;
import org.commcare.android.tasks.WipeTask;
import org.commcare.android.tasks.ZipTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.AlertDialogFactory;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.services.WiFiDirectBroadcastReceiver;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CommCareWiFiDirectActivity extends SessionAwareCommCareActivity<CommCareWiFiDirectActivity> implements DeviceActionListener, FileServerListener, WifiDirectManagerListener {

    private static final String TAG = CommCareWiFiDirectActivity.class.getSimpleName();

    public static final String KEY_NUMBER_DUMPED ="wd_num_dumped";

    private WifiP2pManager mManager;
    private Channel mChannel;
    private WiFiDirectBroadcastReceiver mReceiver;

    private IntentFilter mIntentFilter;

    public enum wdState{send,receive,submit}

    private WiFiDirectAdapter adapter;

    public static String baseDirectory;
    public static String sourceDirectory;
    public static String sourceZipDirectory;
    private static String receiveDirectory;
    private static String receiveZipDirectory;
    private static String writeDirectory;

    private TextView myStatusText;
    private TextView formCountText;
    private TextView stateHeaderText;
    private TextView stateStatusText;

    private static final int FILE_SERVER_TASK_ID = 129123;

    public wdState mState = wdState.send;

    private FormRecord[] cachedRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        setContentView(R.layout.wifi_direct_main);
        adapter = new WiFiDirectAdapter(this);

        myStatusText = (TextView)this.findViewById(R.id.my_status_text);

        formCountText = (TextView)this.findViewById(R.id.form_count_text);

        stateStatusText = (TextView)this.findViewById(R.id.wifi_state_status);

        stateHeaderText = (TextView)this.findViewById(R.id.wifi_state_header);

        String baseDir = this.getFilesDir().getAbsolutePath();
        
        baseDirectory = baseDir + "/" + Localization.get("wifi.direct.base.folder");
        sourceDirectory = baseDirectory + "/source";
        sourceZipDirectory = baseDirectory + "/zipSource.zip";
        receiveDirectory = baseDirectory + "/receive";
        receiveZipDirectory = receiveDirectory + "/zipDest";
        writeDirectory = baseDirectory + "/write";

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        setupGridView();
        changeState();
        if(savedInstanceState == null){
            showChangeStateDialog();
        }
    }

    public void showChangeStateDialog(){
        showDialog(this, localize("wifi.direct.change.state.title").toString(),
                localize("wifi.direct.change.state.text").toString());
    }

    private void setupGridView() {
        final RecyclerView grid = (RecyclerView)findViewById(R.id.wifi_direct_gridview_buttons);
        grid.setHasFixedSize(true);

        StaggeredGridLayoutManager gridView =
                new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL);
        grid.setLayoutManager(gridView);
        grid.setItemAnimator(null);
        grid.setAdapter(adapter);

        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    grid.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }

                grid.requestLayout();
                adapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * register the broadcast receiver
     */
    protected void onResume() {
        super.onResume();

        Logger.log(TAG, "resuming wi-fi direct activity");

        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        mReceiver = new WiFiDirectBroadcastReceiver(mManager, fragment);
        registerReceiver(mReceiver, mIntentFilter);

        fragment.startReceiver(mManager, mChannel);

        updateStatusText();
    }

    /**
     * unregister the broadcast receiver
     */
    @Override
    protected void onPause() {
        super.onPause();
        Logger.log(TAG, "Pausing wi-fi direct activity");
        unregisterReceiver(mReceiver);
    }

    private void hostGroup(){
        Logger.log(TAG, "Hosting Wi-fi direct group");

        final FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);
        fsFragment.startServer(receiveZipDirectory);

        WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        if(!fragment.isWifiP2pEnabled()){
            Logger.log(TAG, "returning because Wi-fi direct is not available");
            Toast.makeText(CommCareWiFiDirectActivity.this, localize("wifi.direct.wifi.direct.off"),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mManager.createGroup(mChannel, fragment);
    }

    private void changeState(){
        adapter.updateDisplayData();
        adapter.notifyDataSetChanged();
    }

    private void showDialog(Activity activity, String title, String message) {
        AlertDialogFactory factory = new AlertDialogFactory(activity, title, message);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which) {
                    case AlertDialog.BUTTON_POSITIVE:
                        beSubmitter();
                        break;
                    case AlertDialog.BUTTON_NEUTRAL:
                        beReceiver();
                        break;
                    case AlertDialog.BUTTON_NEGATIVE:
                        beSender();
                        break;
                }
                dialog.dismiss();
            }
        };
        factory.setNeutralButton(localize("wifi.direct.receive.forms"), listener);
        factory.setNegativeButton(localize("wifi.direct.transfer.forms"), listener);
        factory.setPositiveButton(localize("wifi.direct.submit.forms"), listener);
        showAlertDialog(factory);
    }

    private void beSender(){

        myStatusText.setText(localize("wifi.direct.enter.send.mode"));

        WiFiDirectManagementFragment wifiFragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);

        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);

        FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);

        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();

        tr.show(wifiFragment);
        tr.show(fragmentList);
        tr.show(fragmentDetails);
        tr.hide(fsFragment);
        tr.commit();

        wifiFragment.setIsHost(false);
        wifiFragment.resetConnectionGroup();


        Logger.log(TAG, "Device designated as sender");
        resetData();
        mState = wdState.send;
        updateStatusText();
        adapter.notifyDataSetChanged();
    }

    private void beReceiver(){

        Logger.log(AndroidLogger.TYPE_FORM_DUMP, "Became receiver");
        myStatusText.setText(localize("wifi.direct.enter.receive.mode"));

        WiFiDirectManagementFragment wifiFragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);

        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);

        FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);

        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();

        tr.show(wifiFragment);
        tr.show(fragmentList);
        tr.show(fragmentDetails);
        tr.show(fsFragment);
        tr.commit();

        wifiFragment.setIsHost(true);
        wifiFragment.resetConnectionGroup();

        Logger.log(TAG, "Device designated as receiver");
        resetData();
        hostGroup();

        mState = wdState.receive;
        updateStatusText();
        adapter.notifyDataSetChanged();
    }

    private void beSubmitter(){

        Logger.log(AndroidLogger.TYPE_FORM_DUMP, "Became submitter");
        unzipFilesHelper();
        myStatusText.setText(localize("wifi.direct.enter.submit.mode"));

        WiFiDirectManagementFragment wifiFragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);

        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);

        FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);

        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();

        tr.hide(fsFragment);
        tr.hide(wifiFragment);
        tr.hide(fragmentList);
        tr.hide(fragmentDetails);
        tr.commit();

        wifiFragment.setIsHost(false);
        wifiFragment.resetConnectionGroup();

        mState = wdState.submit;
        updateStatusText();
        adapter.notifyDataSetChanged();
    }

    private void cleanPostSend(){

        Logger.log(TAG, "cleaning forms after successful Wi-fi direct transfer");

        // remove Forms from CC

        WipeTask mWipeTask = new WipeTask(getApplicationContext(), this.cachedRecords){
            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver,
                    Boolean result) {
                receiver.onCleanSuccessful();
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                receiver.updateProgress(update[0], WipeTask.WIPE_TASK_ID);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                receiver.myStatusText.setText(localize("wifi.direct.error.wiping.forms", e.getMessage()));
                receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            }
        };

        mWipeTask.connect(CommCareWiFiDirectActivity.this);
        mWipeTask.execute();

        FileUtil.deleteFileOrDir(new File(sourceDirectory));
        FileUtil.deleteFileOrDir(new File(sourceZipDirectory));

        Logger.log(TAG, "Deleting dirs " + sourceDirectory + " and " + sourceZipDirectory);

        this.cachedRecords = null;

    }

    private void onCleanSuccessful() {
        Logger.log(TAG, "clean successful");
        updateStatusText();
    }

    public void submitFiles(){

        Logger.log(TAG, "submitting forms in Wi-fi direct activity");

        unzipFilesHelper();

        final String url = this.getString(R.string.PostURL);

        File receiveFolder = new File(writeDirectory);

        if(!receiveFolder.isDirectory() || !receiveFolder.exists()){
            myStatusText.setText(Localization.get("wifi.direct.submit.missing", new String[] {receiveFolder.getPath()}));
        }

        File[] files = receiveFolder.listFiles();

        if (files == null){
            myStatusText.setText(localize("wifi.direct.error.no.forms"));
            transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            return;
        }

        final int formsOnSD = files.length;

        //if there're no forms to dump, just return
        if(formsOnSD == 0){
            myStatusText.setText(Localization.get("bulk.form.no.unsynced"));
            transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            return;
        }

        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        SendTask<CommCareWiFiDirectActivity> mSendTask = new SendTask<CommCareWiFiDirectActivity>(
                settings.getString("PostURL", url), receiveFolder){

            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver, Boolean result) {
                if(result == Boolean.TRUE){
                    Intent i = new Intent(getIntent());
                    i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
                    receiver.setResult(BULK_SEND_ID, i);
                    Logger.log(TAG, "Sucessfully dumped " + formsOnSD);
                    receiver.finish();
                } else {
                    //assume that we've already set the error message, but make it look scary
                    receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
                }
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                receiver.updateProgress(update[0], BULK_SEND_ID);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                Logger.log(TAG, "Error submitting forms in wi-fi direct with exception" + e.getMessage());
                receiver.myStatusText.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
                receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            }
        };
        mSendTask.connect(CommCareWiFiDirectActivity.this);
        mSendTask.execute();
    }

    private boolean unzipFilesHelper(){
        File receiveZipDir = new File(receiveDirectory);
        if(!receiveZipDir.exists() || !(receiveZipDir.isDirectory())){
            return false;
        }

        File[] zipDirContents = receiveZipDir.listFiles();
        if(zipDirContents.length < 1){
            return false;
        }

        myStatusText.setText(localize("wifi.direct.zip.unzipping"));

        for (File zipDirContent : zipDirContents) {
            unzipFiles(zipDirContent.getAbsolutePath());
        }

        return true;
    }

    private void unzipFiles(String fn){
        Logger.log(TAG, "Unzipping files in Wi-fi direct");
        UnzipTask<CommCareWiFiDirectActivity> mUnzipTask = new UnzipTask<CommCareWiFiDirectActivity>() {
            @Override
            protected void deliverResult( CommCareWiFiDirectActivity receiver, Integer result) {
                Log.d(TAG, "delivering unzip result");
                if(result > 0){
                    receiver.onUnzipSuccessful(result);
                } else {
                    //assume that we've already set the error message, but make it look scary
                    receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
                }
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                Log.d(TAG, "delivering unzip upate");
                receiver.updateProgress(update[0], CommCareTask.GENERIC_TASK_ID);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                Log.d(TAG, "unzip deliver error: " + e.getMessage());
                receiver.myStatusText.setText(Localization.get("mult.install.error", new String[] {e.getMessage()}));
                receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            }
        };

        mUnzipTask.connect(CommCareWiFiDirectActivity.this);
        Logger.log(TAG, "executing task with: " + fn + " , " + writeDirectory);
        mUnzipTask.execute(fn, writeDirectory);
    }

    /* if successful, broadcasts WIFI_P2P_Peers_CHANGED_ACTION intent with list of peers
     * received in WiFiDirectBroadcastReceiver class
     */
    public void discoverPeers() {
        Logger.log(TAG, "Discovering Wi-fi direct peers");

        WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        if(!fragment.isWifiP2pEnabled()){
            Toast.makeText(CommCareWiFiDirectActivity.this, localize("wifi.direct.wifi.direct.off"),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final DeviceListFragment dlFragment = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        dlFragment.onInitiateDiscovery();

        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(CommCareWiFiDirectActivity.this,
                        localize("wifi.direct.discovery.start"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {

                Logger.log(TAG, "Discovery of Wi-fi peers failed");

                if (reasonCode == 0 || reasonCode == 2) {
                    Toast.makeText(CommCareWiFiDirectActivity.this,
                            localize("wifi.direct.discovery.failed.generic"),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CommCareWiFiDirectActivity.this,
                            localize("wifi.direct.discovery.failed.specific", "" + reasonCode),
                            Toast.LENGTH_SHORT).show();
                }

            }
        });
    }

    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public void connect(WifiP2pConfig config) {
        Logger.log(TAG, "connecting to wi-fi peer");

        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Logger.log(TAG,"Connection to peer failed");
                Toast.makeText(CommCareWiFiDirectActivity.this,
                        localize("wifi.direct.connect.failed"),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void deleteIfExists(String filePath){
        File toDelete = new File(filePath);
        if(toDelete.exists()){
            toDelete.delete();
        }
    }

    public void prepareFileTransfer(){
        Logger.log(TAG, "Preparing File Transfer");

        CommCareWiFiDirectActivity.deleteIfExists(sourceZipDirectory);

        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        if(!fragment.getDeviceConnected()){
            myStatusText.setText(localize("wifi.direct.no.group"));
            return;
        }

        zipFiles();
    }

    private void onZipSuccesful(FormRecord[] records){
        Logger.log(TAG, "Successfully zipped files of size: " + records.length);
        myStatusText.setText(localize("wifi.direct.zip.successful"));
        this.cachedRecords = records;
        updateStatusText();
        sendFiles();
    }

    private void onZipError(){
        FileUtil.deleteFileOrDir(new File(sourceDirectory));
        Log.d(CommCareWiFiDirectActivity.TAG, "Zip unsuccesful");
    }

    private void onUnzipSuccessful(Integer result){
        Logger.log(TAG, "Successfully unzipped " + result.toString() +  " files.");
        myStatusText.setText(localize("wifi.direct.receive.successful", result.toString()));
        if(!FileUtil.deleteFileOrDir(new File(receiveDirectory))){
            Log.d(TAG, "source zip not succesfully deleted");
        }
        updateStatusText();
    }

    private void zipFiles(){
        Logger.log(TAG, "Zipping Files");
        ZipTask mZipTask = new ZipTask(this) {
            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                receiver.updateProgress(update[0], taskId);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                receiver.myStatusText.setText(localize("wifi.direct.zip.unsuccessful", e.getMessage()));
                receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
            }

            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver, FormRecord[] result) {
                if(result != null){
                    receiver.onZipSuccesful(result);
                } else {
                    receiver.onZipError();
                    receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
                }
            }
        };
        mZipTask.connect(CommCareWiFiDirectActivity.this);
        mZipTask.execute();
    }

    private void sendFiles(){
        Logger.log(TAG, "Sending Files via Wi-fi Direct");
        TextView statusText = myStatusText;
        statusText.setText(localize("wifi.direct.send.forms"));
        Log.d(CommCareWiFiDirectActivity.TAG, "Starting form transfer task" );

        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        String address = fragment.getHostAddress();

        FormTransferTask mTransferTask = new FormTransferTask(address,sourceZipDirectory,8988){

            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver,
                    Boolean result) {
                if(result == Boolean.TRUE){
                    receiver.onSendSuccessful();
                } else {
                    receiver.onSendFail();
                    receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
                }
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver,
                    String... update) {
                receiver.updateProgress(update[0], taskId);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver,
                    Exception e) {
                receiver.myStatusText.setText(localize("wifi.direct.send.unsuccessful"));
                receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);

            }

        };

        mTransferTask.connect(CommCareWiFiDirectActivity.this);
        mTransferTask.execute();

        Log.d(CommCareWiFiDirectActivity.TAG, "Task started");
    }

    private void onSendSuccessful(){
        Logger.log(TAG, "File Send Successful");
        Toast.makeText(CommCareWiFiDirectActivity.this, localize("wifi.direct.send.successful"),
                Toast.LENGTH_SHORT).show();

        updateStatusText();
        myStatusText.setText(localize("wifi.direct.send.successful"));
        this.cleanPostSend();
    }

    private void onSendFail(){
        Logger.log(TAG, "Error Sending Files");
    }

    private void updateStatusText(){
        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        //Get all forms which are either unsent or unprocessed
        Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
        ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));

        int numUnsyncedForms = ids.size();

        int numUnsubmittedForms = 0;

        File wDirectory = new File(writeDirectory);

        if(!wDirectory.exists() || !wDirectory.isDirectory()){
            numUnsubmittedForms = 0;
        }
        else{
            numUnsubmittedForms = wDirectory.listFiles().length;
        }

        if(mState.equals(wdState.send)){
            stateHeaderText.setText(localize("wifi.direct.status.transfer.header"));
            formCountText.setText(localize("wifi.direct.status.transfer.count", ""+numUnsyncedForms));
            stateStatusText.setText(localize("wifi.direct.status.transfer.message"));
        } else if(mState.equals(wdState.receive)){
            stateHeaderText.setText(localize("wifi.direct.status.receive.header"));
            formCountText.setText(localize("wifi.direct.status.receive.count", ""+numUnsubmittedForms));
            stateStatusText.setText(localize("wifi.direct.status.receive.message"));
        } else{
            stateHeaderText.setText(localize("wifi.direct.status.submit.header"));
            formCountText.setText(localize("wifi.direct.status.submit.count", ""+numUnsubmittedForms));
            stateStatusText.setText(localize("wifi.direct.status.submit.message"));
        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        Logger.log(TAG, "File server copying file");
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
            Logger.log(TAG, "Copy in File Server failed");
            return false;
        }
        Logger.log(TAG, "Copy in File Server successful");
        return true;
    }

    @Override
    public void onFormsCopied(String result) {
        Logger.log(TAG, "Copied files successfully");
        Log.d(CommCareWiFiDirectActivity.TAG, "onCopySuccess");
        this.unzipFiles(result);
    }

    @Override
    public void updatePeers() {
        Logger.log(TAG, "Wi-Fi direct peers updating");
        mManager.requestPeers(mChannel, (PeerListListener) this.getSupportFragmentManager()
                .findFragmentById(R.id.frag_list));

    }
    
    @Override
    public void updateDeviceStatus(WifiP2pDevice mDevice) {
        Logger.log(TAG, "Wi-fi direct status updating");
        DeviceListFragment fragment = (DeviceListFragment) this.getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);

        fragment.updateThisDevice(mDevice);

    }
    
    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
        case ZipTask.ZIP_TASK_ID:
            title = localize("wifi.direct.zip.task.title").toString();
            message = localize("wifi.direct.zip.task.message").toString();
            break;
        case UnzipTask.UNZIP_TASK_ID:
            title = localize("wifi.direct.unzip.task.title").toString();
            message = localize("wifi.direct.unzip.task.message").toString();
            break;
        case SendTask.BULK_SEND_ID:
            title = localize("wifi.direct.submit.task.title").toString();
            message = localize("wifi.direct.submit.task.message").toString();
            break;
        case FormTransferTask.BULK_TRANSFER_ID:
            title = localize("wifi.direct.transfer.task.title").toString();
            message = localize("wifi.direct.transfer.task.message").toString();
            break;
        case FILE_SERVER_TASK_ID:
            title = localize("wifi.direct.receive.task.title").toString();
            message = localize("wifi.direct.receive.task.message").toString();
            break;
        case WipeTask.WIPE_TASK_ID:
            title = localize("wifi.direct.wipe.task.title").toString();
            message = localize("wifi.direct.wipe.task.message").toString();
            break;
        default:
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareWifiDirectActivity");
            return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }
}
