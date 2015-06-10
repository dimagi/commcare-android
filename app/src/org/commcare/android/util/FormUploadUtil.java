package org.commcare.android.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.User;
import org.commcare.android.io.DataSubmissionEntity;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.mime.EncryptedFileBody;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.DataSubmissionListener;
import org.javarosa.core.io.StreamsUtil.InputIOException;
import org.javarosa.core.services.Logger;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

public class FormUploadUtil {
    
    /** Everything worked great! **/
    public static final long FULL_SUCCESS = 0;
    
    /** There was a problem with the server's response **/
    public static final long FAILURE = 2;
    
    /** There was a problem with the transport layer during transit **/
    public static final long TRANSPORT_FAILURE = 4;
    
    /** There is a problem with this record that prevented submission success **/
    public static final long RECORD_FAILURE = 8;
    
    public static final long SUBMISSION_BEGIN = 16;
    public static final long SUBMISSION_START = 32;
    public static final long SUBMISSION_NOTIFY = 64;
    public static final long SUBMISSION_DONE = 128;
    
    public static final long PROGRESS_LOGGED_OUT = 256;
    public static final long PROGRESS_SDCARD_REMOVED = 512;
    
    private static long MAX_BYTES = (5 * 1048576)-1024;
    private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", "jpeg", ".3gpp", ".3gp", ".3ga", ".3g2", ".mp3", ".wav", ".amr",".mp4", ".3gp2", ".mpg4", ".mpeg4", ".m4v", ".mpg", ".mpeg", ".qcp"};
        
    public static Cipher getDecryptCipher(SecretKeySpec key) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher;
            //TODO: Something smart here;
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    
    public static Cipher getDecryptCipher(byte[] key) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher;
            //TODO: Something smart here;
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    public static long sendInstance(int submissionNumber, File folder, String url, User user) throws FileNotFoundException {
        return FormUploadUtil.sendInstance(submissionNumber, folder, null, url, null, user);
    }
    
    
    public static long sendInstance(int submissionNumber, File folder, SecretKeySpec key, String url, AsyncTask listener, User user) throws FileNotFoundException {
        
        boolean hasListener = false;
        DataSubmissionListener myListener = null;
        
        if(listener instanceof DataSubmissionListener){
            hasListener = true;
            myListener = (DataSubmissionListener)listener;
        }
        
        File[] files = folder.listFiles();
        
        if(files == null) {
            //make sure external storage is available to begin with.
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                //If so, just bail as if the user had logged out.
                throw new UserStorageClosedException("External Storage Removed");
            } else {
                throw new FileNotFoundException("No directory found at: " + folder.getAbsoluteFile());
            }
        } 

        //If we're listening, figure out how much (roughly) we have to send
        long bytes = 0;
        for (int j = 0; j < files.length; j++) {
            //Make sure we'll be sending it
            boolean supported = false;
            for(String ext : SUPPORTED_FILE_EXTS) {
                if(files[j].getName().endsWith(ext)) {
                    supported = true;
                    break;
                }
            }
            if(!supported) { continue;}
            
            bytes += files[j].length();
            System.out.println("Added file: " + files[j].getName() +". Bytes to send: " + bytes);
        }
        
        if(hasListener){
            myListener.startSubmission(submissionNumber, bytes);
        }
        
        HttpRequestGenerator generator;
        if(user.getUserType().equals(User.TYPE_DEMO)) {
            generator = new HttpRequestGenerator(); 
        } else {
            generator = new HttpRequestGenerator(user);
        }
        
        String t = "p+a+s";
        
        if (files == null) {
            Log.e(t, "no files to upload");
            listener.cancel(true);
        }
        
        // mime post
        MultipartEntity entity = new DataSubmissionEntity(myListener, submissionNumber);
        
        for (int j = 0; j < files.length; j++) {
            File f = files[j];
            ContentBody fb;
            
            boolean supported = false;
            for(String ext : SUPPORTED_FILE_EXTS) {
                if(f.getName().endsWith(ext)) {
                    supported = true;
                    break;
                }
            }
            
            //TODO: Match this with some reasonable library, rather than silly file lines
            if (f.getName().endsWith(".xml")) {
                
                //fb = new InputStreamBody(new CipherInputStream(new FileInputStream(f), getDecryptCipher(aesKey)), "text/xml", f.getName());
                
                if(key != null){
                    if(!validateSubmissionFile(f, FormUploadUtil.getDecryptCipher(key))) {
                        return RECORD_FAILURE;
                    }
                    fb = new EncryptedFileBody(f, FormUploadUtil.getDecryptCipher(key), "text/xml");
                }
                else{
                    fb = new FileBody(f, "text/xml");
                }
                
                entity.addPart("xml_submission_file", fb);
                
                //fb = new FileBody(f, "text/xml");
                //Don't know if we can ask for the content length on the input stream, so skip it.
//                if (fb.getContentLength() <= MAX_BYTES) {
//                    Log.i(t, "added xml file " + f.getName());
//                } else {
//                    Log.i(t, "file " + f.getName() + " is too big");
//                }
            } else if (f.getName().endsWith(".jpg")) {
                fb = new FileBody(f, "image/jpeg");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added image file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gpp")) {
                fb = new FileBody(f, "audio/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gp")) {
                fb = new FileBody(f, "video/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (supported) {
                 fb = new FileBody(f, "application/octet-stream");
                 if (fb.getContentLength() <= MAX_BYTES) {
                     entity.addPart(f.getName(), fb);
                     Log.i(t, "added unknown file " + f.getName());
                 } else {
                     Log.i(t, "file " + f.getName() + " is too big");
                 }
            } else {
                Log.w(t, "unsupported file type, not adding file: " + f.getName());
            }
        }
        // prepare response and return uploaded
        HttpResponse response = null;
        try {
            response = generator.postData(url, entity);
        } catch (InputIOException ioe ){
            //This implies that there was a problem with the _source_ of the 
            //transmission, not the processing or receiving end.
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Internal error reading form record during submission: " + ioe.getWrapped().getMessage());
            return RECORD_FAILURE;
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IOException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        }

        String serverLocation = null;
        Header[] h = response.getHeaders("Location");
        if (h != null && h.length > 0) {
            serverLocation = h[0].getValue();
        } else {
            // something should be done here...
            Log.e(t, "Location header was absent");
        }
        int responseCode = response.getStatusLine().getStatusCode();
        Log.e(t, "Response code:" + responseCode);
        //If this response code wasn't legit
        if(!(responseCode >= 200 && responseCode < 300)) {
            //Log that so we can figure out what's up!
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Response Code: " + responseCode);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        try {
            AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), bos);
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        String responseString = new String(bos.toByteArray());
        System.out.println(responseString);
        

        if(responseCode >= 200 && responseCode < 300) {
            return FULL_SUCCESS;
        } else {
            return FAILURE;
        }
    }


    /**
     * Validate the content body of the XML submission file.
     * 
     * TODO: this should really be the responsibility of the form record, not of the 
     * submission process, persay.
     * 
     * NOTE: this is a shallow validation (everything should be more or else constant time).
     * Throws an exception if the file is gone because that's a common issue that gets caught
     * to check if storage got removed 
     * 
     * @param f
     * @param decryptCipher
     * @return
     * @throws FileNotFoundException 
     */
    public static boolean validateSubmissionFile(File f, Cipher decryptCipher) throws FileNotFoundException {
        if(!f.exists()) {
            throw new FileNotFoundException("Submission file: " + f.getAbsolutePath());
        }
        //Gotta check f exists here since f.length returns 0 if the file isn't there for some reason.
        if(f.length() == 0 && f.exists()) {
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Submission body has no content at: " + f.getAbsolutePath());
            return false;
        }
        
        return true;
    }
}
