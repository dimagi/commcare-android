package org.commcare.utils;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.commcare.android.io.DataSubmissionEntity;
import org.commcare.android.logging.AndroidLogger;
import org.commcare.android.tasks.DataSubmissionListener;
import org.commcare.models.database.UserStorageClosedException;
import org.commcare.network.EncryptedFileBody;
import org.commcare.network.HttpRequestGenerator;
import org.javarosa.core.io.StreamsUtil.InputIOException;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class FormUploadUtil {
    private static final String TAG = FormUploadUtil.class.getSimpleName();

    /**
     * Everything worked great!
     */
    public static final long FULL_SUCCESS = 0;

    /**
     * There was a problem with the server's response
     */
    public static final long FAILURE = 2;

    /**
     * There was a problem with the transport layer during transit
     */
    public static final long TRANSPORT_FAILURE = 4;

    /**
     * There is a problem with this record that prevented submission success
     */
    public static final long RECORD_FAILURE = 8;

    private static final long MAX_BYTES = (5 * 1048576) - 1024;
    private static final String[] SUPPORTED_FILE_EXTS =
            {".xml", ".jpg", "jpeg", ".3gpp", ".3gp", ".3ga", ".3g2", ".mp3",
                    ".wav", ".amr", ".mp4", ".3gp2", ".mpg4", ".mpeg4",
                    ".m4v", ".mpg", ".mpeg", ".qcp", ".ogg"};

    public static Cipher getDecryptCipher(SecretKeySpec key) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher;
            //TODO: Something smart here;
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Send unencrypted data to the server without user facing progress
     * reporting.
     *
     * @param submissionNumber For progress reporting
     * @param folder           All supported files in this folder will be
     *                         attached to the submission
     * @param url              Submission server url
     * @param user             Used to build the http post
     * @return Submission status code
     * @throws FileNotFoundException Is raised if xml file isn't found on the
     *                               file-system
     */
    public static long sendInstance(int submissionNumber, File folder,
                                    String url, User user)
            throws FileNotFoundException {
        return FormUploadUtil.sendInstance(submissionNumber, folder, null,
                url, null, user);
    }

    /**
     * Send data to the server, encrypting xml files and reporting progress
     * along the way.
     *
     * @param submissionNumber For progress reporting
     * @param folder           All supported files in this folder will be
     *                         attached to the submission
     * @param key              For encrypting xml files
     * @param url              Submission server url
     * @param listener         Used to report progress to the calling task
     * @param user             Used to build the http post
     * @return Submission status code
     * @throws FileNotFoundException Is raised if xml file isn't found on the
     *                               file-system
     */
    public static long sendInstance(int submissionNumber, File folder,
                                    SecretKeySpec key, String url,
                                    AsyncTask listener, User user)
            throws FileNotFoundException {
        boolean hasListener = false;
        DataSubmissionListener myListener = null;

        if (listener instanceof DataSubmissionListener) {
            hasListener = true;
            myListener = (DataSubmissionListener)listener;
        }

        File[] files = folder.listFiles();

        if (files == null) {
            // make sure external storage is available to begin with.
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                // If so, just bail as if the user had logged out.
                throw new UserStorageClosedException("External Storage Removed");
            } else {
                throw new FileNotFoundException("No directory found at: " +
                        folder.getAbsoluteFile());
            }
        }

        // If we're listening, figure out how much (roughly) we have to send
        long bytes = estimateUploadBytes(files);

        if (hasListener) {
            myListener.startSubmission(submissionNumber, bytes);
        }

        if (files.length == 0) {
            Log.e(TAG, "no files to upload");
            listener.cancel(true);
        }

        // mime post
        MultipartEntity entity =
                new DataSubmissionEntity(myListener, submissionNumber);
        if (!buildMultipartEntity(entity, key, files)) {
            return RECORD_FAILURE;
        }

        HttpRequestGenerator generator;
        if (user.getUserType().equals(User.TYPE_DEMO)) {
            generator = new HttpRequestGenerator();
        } else {
            generator = new HttpRequestGenerator(user);
        }
        return submitEntity(entity, url, generator);
    }

    /**
     * Submit multipart entity with plenty of logging
     *
     * @return submission status of multipart entity post
     */
    private static long submitEntity(MultipartEntity entity, String url,
                                     HttpRequestGenerator generator) {
        HttpResponse response;

        try {
            response = generator.postData(url, entity);
        } catch (InputIOException ioe) {
            // This implies that there was a problem with the _source_ of the
            // transmission, not the processing or receiving end.
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE,
                    "Internal error reading form record during submission: " +
                            ioe.getWrapped().getMessage());
            return RECORD_FAILURE;
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        }

        int responseCode = response.getStatusLine().getStatusCode();
        Log.e(TAG, "Response code:" + responseCode);

        if (!(responseCode >= 200 && responseCode < 300)) {
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK,
                    "Response Code: " + responseCode);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), bos);
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }

        String responseString = new String(bos.toByteArray());
        Log.d(TAG, responseString);

        if (responseCode >= 200 && responseCode < 300) {
            return FULL_SUCCESS;
        } else {
            return FAILURE;
        }
    }

    /**
     * Validate the content body of the XML submission file.
     *
     * TODO: this should really be the responsibility of the form record, not
     * of the submission process, persay.
     *
     * NOTE: this is a shallow validation (everything should be more or else
     * constant time).  Throws an exception if the file is gone because that's
     * a common issue that gets caught to check if storage got removed
     *
     * @param f xml file to check
     * @return false if the file is empty; otherwise true
     * @throws FileNotFoundException file in question isn't found on the
     *                               file-system
     */
    private static boolean validateSubmissionFile(File f)
            throws FileNotFoundException {
        if (!f.exists()) {
            throw new FileNotFoundException("Submission file: " +
                    f.getAbsolutePath());
        }
        // Gotta check f exists here since f.length returns 0 if the file isn't
        // there for some reason.
        if (f.length() == 0 && f.exists()) {
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE,
                    "Submission body has no content at: " + f.getAbsolutePath());
            return false;
        }

        return true;
    }

    /**
     * @return The aggregated size in bytes the files of supported extension
     * type.
     */
    private static long estimateUploadBytes(File[] files) {
        long bytes = 0;
        for (File file : files) {
            // Make sure we'll be sending it
            if (!isSupportedMultimediaFile(file.getName())) {
                continue;
            }

            bytes += file.length();
            Log.d(TAG, "Added file: " + file.getName() +
                    ". Bytes to send: " + bytes);
        }
        return bytes;
    }

    /**
     * Add files of supported type to the multipart entity, encrypting xml
     * files.
     *
     * @param entity Add files to this
     * @param key    Used to encrypt xml files
     * @param files  The files to be added to the entity,
     * @return false if invalid xml files are found; otherwise true.
     * @throws FileNotFoundException Is raised when an xml doesn't exist on the
     *                               file-system
     */
    private static boolean buildMultipartEntity(MultipartEntity entity,
                                                SecretKeySpec key,
                                                File[] files)
            throws FileNotFoundException {
        for (File f : files) {
            ContentBody fb;

            if (f.getName().endsWith(".xml")) {
                if (key != null) {
                    if (!validateSubmissionFile(f)) {
                        return false;
                    }
                    fb = new EncryptedFileBody(f, FormUploadUtil.getDecryptCipher(key),
                            ContentType.TEXT_XML);
                } else {
                    fb = new FileBody(f, ContentType.TEXT_XML, f.getName());
                }
                entity.addPart("xml_submission_file", fb);
            } else if (f.getName().endsWith(".jpg")) {
                fb = new FileBody(f, "image/jpeg");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(TAG, "added image file " + f.getName());
                } else {
                    Log.i(TAG, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gpp")) {
                fb = new FileBody(f, "audio/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(TAG, "added audio file " + f.getName());
                } else {
                    Log.i(TAG, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gp")) {
                fb = new FileBody(f, "video/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(TAG, "added video file " + f.getName());
                } else {
                    Log.i(TAG, "file " + f.getName() + " is too big");
                }
            } else if (isSupportedMultimediaFile(f.getName())) {
                fb = new FileBody(f, "application/octet-stream");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(TAG, "added unknown file " + f.getName());
                } else {
                    Log.i(TAG, "file " + f.getName() + " is too big");
                }
            } else {
                Log.w(TAG, "unsupported file type, not adding file: " +
                        f.getName());
            }
        }
        return true;
    }

    /**
     * @return Is the filename's extension in the hard-coded list of supported
     * files or have a media mimetype?
     */
    public static boolean isSupportedMultimediaFile(String filename) {
        for (String ext : SUPPORTED_FILE_EXTS) {
            if (filename.endsWith(ext)) {
                return true;
            }
        }
        return isAudioVisualMimeType(filename);
    }

    /**
     * Use the file's extension to determine if it has an audio,
     * video, or image mimetype.
     *
     * @return true if the file has an audio, image, or video mimetype
     */
    private static boolean isAudioVisualMimeType(String filename) {
        MimeTypeMap mtm = MimeTypeMap.getSingleton();
        String[] filenameSegments = filename.split("\\.");
        if (filenameSegments.length > 1) {
            // use the file extension to determine the mimetype
            String ext = filenameSegments[filenameSegments.length - 1];
            String mimeType = mtm.getMimeTypeFromExtension(ext);

            return (mimeType != null) &&
                    (mimeType.startsWith("audio") ||
                            mimeType.startsWith("image") ||
                            mimeType.startsWith("video"));
        }

        return false;
    }
}
