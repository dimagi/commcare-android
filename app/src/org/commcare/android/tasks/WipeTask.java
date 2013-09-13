/**
 * 
 */
package org.commcare.android.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.ReflectionUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.StorageFullException;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public abstract class WipeTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity>{

	Context c;
	Long[] results;
	File dumpFolder;
	
	public static final int WIPE_TASK_ID = 7213435;
	
	DataSubmissionListener formSubmissionListener;
	CommCarePlatform platform;
	FormRecord[] records;
	SqlStorage<FormRecord> storage;
	TextView outputTextView;
	
	private static long MAX_BYTES = (5 * 1048576)-1024; // 5MB less 1KB overhead
	
	public WipeTask(Context c, CommCarePlatform platform, TextView outputTextView, FormRecord[] records) throws SessionUnavailableException{
		this.c = c;
		storage =  CommCareApplication._().getUserStorage(FormRecord.class);
		this.outputTextView = outputTextView;
		taskId = WIPE_TASK_ID;
		this.records = records;
		platform = this.platform;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	protected void onProgressUpdate(String... values) {
		super.onProgressUpdate(values);
	}
	
	public void setListeners(DataSubmissionListener submissionListener) {
		this.formSubmissionListener = submissionListener;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);
		//These will never get Zero'd otherwise
		c = null;
		results = null;
	}
	
	@Override
	protected Boolean doTaskBackground(String... params) {
		
		Log.d(CommCareWiFiDirectActivity.TAG, "doing wipe task in background");
		for(int i = 0 ; i < records.length ; ++i) {
			FormRecord record = records[i];
			FormRecordCleanupTask.wipeRecord(c, platform, record);
		}
		return true;
    }

}
