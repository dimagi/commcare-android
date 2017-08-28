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
import org.commcare.network.DataSubmissionEntity;
import org.commcare.network.EncryptedFileBody;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.tasks.DataSubmissionListener;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.io.StreamsUtil.InputIOException;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class FormUploadUtil {
    private static final String TAG = FormUploadUtil.class.getSimpleName();

    /**
     * 15 MB size limit
     */
    public static final long MAX_BYTES = (15 * 1048576) - 1024;

    private static final String[] SUPPORTED_FILE_EXTS =
            {".xml", ".jpg", "jpeg", ".3gpp", ".3gp", ".3ga", ".3g2", ".mp3",
                    ".wav", ".amr", ".mp4", ".3gp2", ".mpg4", ".mpeg4",
                    ".m4v", ".mpg", ".mpeg", ".qcp", ".ogg"};

    private static final String mockRestoreResponseWithProcessingFailure =
            "<OpenRosaResponse xmlns=\"http://openrosa.org/http/response\"><message nature=" +
                    "\"processing_failure\">The submission's case ID was unknown</message>" +
                    "</OpenRosaResponse>";

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
    public static FormUploadResult sendInstance(int submissionNumber, File folder,
                                                String url, User user)
            throws FileNotFoundException {
        return sendInstance(submissionNumber, folder, null, url, null, user);
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
    public static FormUploadResult sendInstance(int submissionNumber, File folder,
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
                throw new SessionUnavailableException("External Storage Removed");
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
            throw new FileNotFoundException("Folder at path " + folder.getAbsolutePath() + " had no files.");
        }

        // mime post
        MultipartEntity entity =
                new DataSubmissionEntity(myListener, submissionNumber);
        if (!buildMultipartEntity(entity, key, files)) {
            return FormUploadResult.RECORD_FAILURE;
        }

        HttpRequestGenerator generator = new HttpRequestGenerator(user);
        return submitEntity(entity, url, generator);
    }

    /**
     * Submit multipart entity with plenty of logging
     *
     * @return submission status of multipart entity post
     */
    private static FormUploadResult submitEntity(MultipartEntity entity, String url,
                                                               HttpRequestGenerator generator) {
        HttpResponse response;

        try {
            response = generator.postData(url, entity);
        } catch (InputIOException ioe) {
            // This implies that there was a problem with the _source_ of the
            // transmission, not the processing or receiving end.
            Logger.log(LogTypes.TYPE_ERROR_STORAGE,
                    "Internal error reading form record during submission: " +
                            ioe.getWrapped().getMessage());
            return FormUploadResult.RECORD_FAILURE;
        } catch (ClientProtocolException | UnknownHostException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                    "Client network issues during submission: " + e.getMessage());
            return FormUploadResult.TRANSPORT_FAILURE;
        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_STORAGE,
                    "Error reading form during submission: " + e.getMessage());
            return FormUploadResult.TRANSPORT_FAILURE;
        }

        InputStream responseStream = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            responseStream = response.getEntity().getContent();
            StreamsUtil.writeFromInputToOutputNew(responseStream, bos);
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }

        String responseString = new String(bos.toByteArray());
        int responseCode = response.getStatusLine().getStatusCode();
        logResponse(responseCode, responseString);

        if (responseCode >= 200 && responseCode < 300) {
            return FormUploadResult.FULL_SUCCESS;
        } else if (responseCode == 401) {
            return FormUploadResult.AUTH_FAILURE;
        } else if (responseCode == 422) {
            return handleProcessingFailure(responseStream);
        } else {
            return FormUploadResult.FAILURE;
        }
    }

    private static FormUploadResult handleProcessingFailure(InputStream responseStream) {
        FormUploadResult result = FormUploadResult.PROCESSING_FAILURE;
        result.setProcessingFailureReason(parseProcessingFailureResponse(responseStream));
        return result;
    }

    private static void logResponse(int responseCode, String responseString) {
        String responseCodeMessage = "Response code to form submission attempt: " + responseCode;
        Log.e(TAG, responseCodeMessage);
        Log.d(TAG, responseString);
        if (!(responseCode >= 200 && responseCode < 300)) {
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, responseCodeMessage);
            Logger.log(LogTypes.TYPE_FORM_SUBMISSION, responseCodeMessage);
            Logger.log(LogTypes.TYPE_FORM_SUBMISSION,
                    "Response string to failed form submission attempt: " + responseString);
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
            Logger.log(LogTypes.TYPE_ERROR_STORAGE,
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
        int numAttachmentsInInstanceFolder = 0;
        int numAttachmentsSuccessfullyAdded = 0;
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
                numAttachmentsInInstanceFolder++;
                fb = new FileBody(f, ContentType.create("image/jpeg"), f.getName());
                numAttachmentsSuccessfullyAdded += addPartToEntity(entity, f, fb);
            } else if (f.getName().endsWith(".3gpp")) {
                numAttachmentsInInstanceFolder++;
                fb = new FileBody(f, ContentType.create("audio/3gpp"), f.getName());
                numAttachmentsSuccessfullyAdded += addPartToEntity(entity, f, fb);
            } else if (f.getName().endsWith(".3gp")) {
                numAttachmentsInInstanceFolder++;
                fb = new FileBody(f, ContentType.create("video/3gpp"), f.getName());
                numAttachmentsSuccessfullyAdded += addPartToEntity(entity, f, fb);
            } else if (isSupportedMultimediaFile(f.getName())) {
                numAttachmentsInInstanceFolder++;
                fb = new FileBody(f, ContentType.APPLICATION_OCTET_STREAM, f.getName());
                numAttachmentsSuccessfullyAdded += addPartToEntity(entity, f, fb);
            } else {
                Logger.log(LogTypes.TYPE_FORM_SUBMISSION,
                        "Could not add unsupported file type to submission entity: " + f.getName());
            }
        }
        Logger.log(LogTypes.TYPE_FORM_SUBMISSION, "Attempted to add "
                + numAttachmentsInInstanceFolder + " attachments to submission entity");
        Logger.log(LogTypes.TYPE_FORM_SUBMISSION, "Successfully added "
                + numAttachmentsSuccessfullyAdded + " attachments to submission entity");
        return true;
    }

    private static int addPartToEntity(MultipartEntity entity, File f, ContentBody fb) {
        if (fb.getContentLength() <= MAX_BYTES) {
            entity.addPart(f.getName(), fb);
            return 1;
        } else {
            Logger.log(LogTypes.TYPE_FORM_SUBMISSION,
                    "Failed to add attachment to submission entity (too large): " + f.getName());
            return 0;
        }
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

    public static String parseProcessingFailureResponse(InputStream responseStream) {
        try {
            KXmlParser baseParser = ElementParser.instantiateParser(responseStream);
            ElementParser<String> responseParser = new ElementParser<String>(baseParser) {
                @Override
                public String parse() throws InvalidStructureException, IOException,
                        XmlPullParserException, UnfullfilledRequirementsException {
                    checkNode("OpenRosaResponse");
                    nextTag("message");
                    String natureOfResponse = parser.getAttributeValue(null, "nature");
                    if ("processing_failure".equals(natureOfResponse)) {
                        return parser.nextText();
                    }
                    return "";
                }
            };
            return responseParser.parse();
        } catch (IOException | InvalidStructureException | XmlPullParserException |
                UnfullfilledRequirementsException e) {
            Log.e(TAG, "Error while parsing form upload response");
            e.printStackTrace();
        }
        return "";
    }
}
