package org.commcare.utils;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.CaptivePortalRedirectException;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.network.EncryptedFileBody;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.commcare.network.HttpUtils.parseUserVisibleError;

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
                                                @Nullable  SecretKeySpec key, String url,
                                                @Nullable DataSubmissionListener listener, User user)
            throws FileNotFoundException {

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

        if (listener != null) {
            listener.startSubmission(submissionNumber, bytes);
        }

        if (files.length == 0) {
            Log.e(TAG, "no files to upload");
            throw new FileNotFoundException("Folder at path " + folder.getAbsolutePath() + " had no files.");
        }

        List<MultipartBody.Part> parts = new ArrayList<>();

        if (!buildMultipartEntity(parts, key, files)) {
            return FormUploadResult.RECORD_FAILURE;
        }

        CommcareRequestGenerator generator = new CommcareRequestGenerator(user);
        return submitEntity(parts, url, generator);
    }

    /**
     * Submit multipart entity with plenty of logging
     *
     * @return submission status of multipart entity post
     */

    private static FormUploadResult submitEntity(List<MultipartBody.Part> parts, String url,
                                                 CommcareRequestGenerator generator) {
        Response<ResponseBody> response;

        try {
            response = generator.postMultipart(url, parts, new HashMap<>());
        } catch (InputIOException ioe) {
            // This implies that there was a problem with the _source_ of the
            // transmission, not the processing or receiving end.
            Logger.log(LogTypes.TYPE_ERROR_STORAGE,
                    "Internal error reading form record during submission: " +
                            ioe.getWrapped().getMessage());
            return FormUploadResult.RECORD_FAILURE;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                    "Client network issues during submission: " + e.getMessage());
            return FormUploadResult.TRANSPORT_FAILURE;
        } catch (AuthenticationInterceptor.PlainTextPasswordException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE,
                    "Encountered PlainTextPasswordException while submission: Sending password over HTTP");
            return FormUploadResult.AUTH_OVER_HTTP;
        } catch (CaptivePortalRedirectException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Captive portal detected while form submission");
            return FormUploadResult.CAPTIVE_PORTAL;
        } catch (IOException | IllegalStateException e) {
            Logger.exception("Error reading form during submission: " + e.getMessage(), e);
            return FormUploadResult.TRANSPORT_FAILURE;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            if (response.body() != null) {
                InputStream responseStream = response.body().byteStream();
                StreamsUtil.writeFromInputToOutputNew(responseStream, bos);
            }
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }

        String responseString = new String(bos.toByteArray());
        int responseCode = response.code();
        logResponse(responseCode, responseString);

        if (responseCode >= 200 && responseCode < 300) {
            return FormUploadResult.FULL_SUCCESS;
        } else if (responseCode == 401) {
            return FormUploadResult.AUTH_FAILURE;
        } else if (responseCode == 406) {
            return processActionableFaiure(response);
        } else if (responseCode == 422) {
            return handleProcessingFailure(response.errorBody().byteStream());
        } else if (responseCode == 503 || responseCode == 429) {
            return FormUploadResult.RATE_LIMITED;
        } else {
            return FormUploadResult.FAILURE;
        }
    }

    private static FormUploadResult processActionableFaiure(Response<ResponseBody> response) {
        String message = parseUserVisibleError(response);
        FormUploadResult result = FormUploadResult.ACTIONABLE_FAILURE;
        result.setErrorMessage(message);
        return result;
    }

    private static FormUploadResult handleProcessingFailure(InputStream responseStream) {
        FormUploadResult result = FormUploadResult.PROCESSING_FAILURE;
        try {
            result.setErrorMessage(parseProcessingFailureResponse(responseStream));
        } catch (IOException | InvalidStructureException | XmlPullParserException |
                UnfullfilledRequirementsException e) {
            // If we can't parse out the failure reason then we won't quarantine this form, because
            // we won't have any clear info about what happened
            result = FormUploadResult.FAILURE;
            Logger.exception("Form processing failed", e);
            e.printStackTrace();
        }
        return result;
    }

    private static void logResponse(int responseCode, String responseString) {
        String responseCodeMessage = "Response code to form submission attempt was: " + responseCode;
        Log.e(TAG, responseCodeMessage);
        Log.d(TAG, responseString);
        if (!(responseCode >= 200 && responseCode < 300)) {
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, responseCodeMessage);
            Logger.log(LogTypes.TYPE_FORM_SUBMISSION, responseCodeMessage);
            if (!responseString.startsWith("<html>")) {
                // Only log this if it's going to be useful
                Logger.log(LogTypes.TYPE_FORM_SUBMISSION,
                        "Response string to failed form submission attempt was: " + responseString);
            }
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
     * @param parts Add files to this
     * @param key   Used to encrypt xml files
     * @param files The files to be added to the entity,
     * @return false if invalid xml files are found; otherwise true.
     * @throws FileNotFoundException Is raised when an xml doesn't exist on the
     *                               file-system
     */
    private static boolean buildMultipartEntity(List<MultipartBody.Part> parts,
                                                @Nullable SecretKeySpec key,
                                                File[] files)
            throws FileNotFoundException {

        int numAttachmentsInInstanceFolder = 0;
        int numAttachmentsSuccessfullyAdded = 0;

        for (File f : files) {
            if (f.getName().endsWith(".xml")) {
                if (key != null) {
                    if (!validateSubmissionFile(f)) {
                        return false;
                    }
                    parts.add(createEncryptedFilePart("xml_submission_file", f, "text/xml", key));
                } else {
                    parts.add(createFilePart("xml_submission_file", f, "text/xml"));
                }
            } else {
                String contentType = getFileContentType(f);
                if (contentType != null) {
                    numAttachmentsInInstanceFolder++;
                    numAttachmentsSuccessfullyAdded += addPartToEntity(parts, f, contentType);
                } else if (isSupportedMultimediaFile(f.getName())) {
                    numAttachmentsInInstanceFolder++;
                    numAttachmentsSuccessfullyAdded += addPartToEntity(parts, f, "application/octet-stream");
                } else {
                    Logger.log(LogTypes.TYPE_FORM_SUBMISSION,
                            "Could not add unsupported file type to submission entity: " + f.getName());
                }
            }
        }
        Logger.log(LogTypes.TYPE_FORM_SUBMISSION, "Attempted to add "
                + numAttachmentsInInstanceFolder + " attachments to submission entity");
        Logger.log(LogTypes.TYPE_FORM_SUBMISSION, "Successfully added "
                + numAttachmentsSuccessfullyAdded + " attachments to submission entity");
        return true;
    }

    private static int addPartToEntity(List<MultipartBody.Part> parts, File f, String contentType) {
        if (f.length() <= MAX_BYTES) {
            parts.add(createFilePart(f.getName(), f, contentType));
            return 1;
        } else {
            Logger.log(LogTypes.TYPE_FORM_SUBMISSION,
                    "Failed to add attachment to submission entity (too large): " + f.getName());
            return 0;
        }
    }


    private static String getFileContentType(File f) {
        if (f.getName().endsWith(".xml")) {
            return "text/xml";
        } else if (f.getName().endsWith(".jpg")) {
            return "image/jpeg";
        } else if (f.getName().endsWith(".3gpp")) {
            return "audio/3gpp";
        } else if (f.getName().endsWith(".3gp")) {
            return "video/3gpp";
        }
        return null;
    }

    private static MultipartBody.Part createFilePart(String partName, File file, String contentType) {

        // create RequestBody instance from file
        RequestBody requestFile = RequestBody.create(
                MediaType.parse(contentType),
                file);

        // MultipartBody.Part is used to send also the actual file name
        return MultipartBody.Part.createFormData(partName, file.getName(), requestFile);
    }


    public static MultipartBody.Part createEncryptedFilePart(String partName, File file, String contentType, SecretKeySpec key) {

        // create RequestBody instance from file
        RequestBody requestFile = new EncryptedFileBody(
                MediaType.parse(contentType),
                file,
                FormUploadUtil.getDecryptCipher(key));

        // MultipartBody.Part is used to send also the actual file name
        return MultipartBody.Part.createFormData(partName, file.getName(), requestFile);
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

    public static String parseProcessingFailureResponse(InputStream responseStream)
            throws IOException, InvalidStructureException, UnfullfilledRequirementsException,
            XmlPullParserException {

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
                } else {
                    throw new UnfullfilledRequirementsException(
                            "<message> for 422 response did not contain expected content");
                }
            }
        };
        return responseParser.parse();
    }
}
