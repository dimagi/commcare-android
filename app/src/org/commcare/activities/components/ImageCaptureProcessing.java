package org.commcare.activities.components;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.FileExtensionNotFoundException;
import org.commcare.utils.FileUtil;
import org.commcare.views.widgets.ImageWidget;
import org.commcare.views.widgets.MediaWidget;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ImageCaptureProcessing {

    // for selecting image from calabash tests
    private static String sCustomImagePath;

    /**
     * Performs any necessary relocating and scaling of an image coming from either a
     * SignatureWidget or ImageWidget (capture or choose)
     *
     * @param originalImage the image file returned by the image capture or chooser intent
     * @param shouldScale   if false, indicates that the image is from a signature capture, so should
     *                      not attempt to scale
     * @return A pair containing raw image and scaled imagePath. The first entry is the raw image
     * while the second one is path to scaled image.
     */
    private static Pair<File, String> moveAndScaleImage(File originalImage, boolean shouldScale,
                                                        String instanceFolder,
                                                        FormEntryActivity formEntryActivity) throws IOException {
        String extension = FileUtil.getExtension(originalImage.getAbsolutePath());
        String imageFilename = System.currentTimeMillis() + "." + extension;
        String tempFilePathForScaledImage = CommCareApplication.instance().getAndroidFsTemp() + imageFilename;

        // clear any existing file at the temp path
        FileUtil.deleteFileOrDir(tempFilePathForScaledImage);

        // Create a raw copy of original image to be displayed on the question view
        File rawImageFile = makeRawCopy(originalImage, instanceFolder, imageFilename);

        // Scale image if required and save it to tempFilePathForScaledImage
        boolean savedScaledImage = false;
        if (shouldScale) {
            ImageWidget currentWidget = (ImageWidget)formEntryActivity.getPendingWidget();
            if (currentWidget != null) {
                int maxDimen = currentWidget.getMaxDimen();
                if (maxDimen != -1) {
                    File tempFile = new File(tempFilePathForScaledImage);
                    savedScaledImage = FileUtil.scaleAndSaveImageWithExif(originalImage, tempFile, maxDimen);
                }
            }
        }
        String sourcePath = savedScaledImage ? tempFilePathForScaledImage : originalImage.getAbsolutePath();
        String finalFilePath = instanceFolder + imageFilename;

        // Encrypt the scaled or original image to final path
        if (HiddenPreferences.isMediaCaptureEncryptionEnabled()) {
            finalFilePath = finalFilePath + MediaWidget.AES_EXTENSION;
            File sourceFile = new File(sourcePath);
            File destFile = new File(finalFilePath);
            
            try {
                // Extract EXIF data before encryption
                ExifInterface sourceExif = null;
                String mimeType = FileUtil.getMimeType(sourcePath);
                if (mimeType != null && mimeType.startsWith("image/")) {
                    sourceExif = new ExifInterface(sourcePath);
                }
                
                // Perform encryption
                EncryptionIO.encryptFile(sourcePath, finalFilePath, formEntryActivity.getSymetricKey());
                
                // If we had EXIF data, store it in a companion file
                if (sourceExif != null) {
                    String exifPath = finalFilePath + ".exif";
                    ExifInterface destExif = new ExifInterface(exifPath);
                    
                    // Copy all EXIF tags
                    String[] tagsToPreserve = {
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_GPS_TIMESTAMP,
                        ExifInterface.TAG_GPS_DATESTAMP,
                        ExifInterface.TAG_GPS_ALTITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifInterface.TAG_GPS_AREA_INFORMATION,
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_DATETIME_DIGITIZED,
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        ExifInterface.TAG_OFFSET_TIME,
                        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                        ExifInterface.TAG_COPYRIGHT,
                        ExifInterface.TAG_IMAGE_DESCRIPTION,
                        ExifInterface.TAG_EXIF_VERSION,
                        ExifInterface.TAG_ORIENTATION
                    };
                    
                    for (String tag : tagsToPreserve) {
                        String value = sourceExif.getAttribute(tag);
                        if (value != null) {
                            destExif.setAttribute(tag, value);
                        }
                    }
                    destExif.saveAttributes();
                    
                    // Encrypt the EXIF data file as well
                    EncryptionIO.encryptFile(exifPath, exifPath + MediaWidget.AES_EXTENSION, 
                            formEntryActivity.getSymetricKey());
                    // Delete the unencrypted EXIF file
                    new File(exifPath).delete();
                }
            } catch (Exception e) {
                throw new IOException("Failed to encrypt image and preserve EXIF data from " + 
                        sourcePath + " to " + finalFilePath, e);
            }
        } else {
            try {
                FileUtil.copyFileWithExifData(new File(sourcePath), new File(finalFilePath));
            } catch (Exception e) {
                throw new IOException("Failed to copy image with EXIF data from " + 
                        sourcePath + " to " + finalFilePath);
            }
        }

        return new Pair<>(rawImageFile, finalFilePath);
    }

    private static File makeRawCopy(File originalImage, String instanceFolder, String imageFilename)
            throws IOException {
        String rawDirPath = getRawDirectoryPath(instanceFolder);
        File rawDir = new File(rawDirPath);
        if (!rawDir.exists()) {
            rawDir.mkdir();
        }
        File rawImageFile = new File(rawDirPath + "/" + imageFilename);
        try {
            FileUtil.copyFileWithExifData(originalImage, rawImageFile);
        } catch (Exception e) {
            throw new IOException("Failed to copy image with EXIF data from " + 
                    originalImage.getAbsolutePath() + " to " + rawImageFile.getAbsolutePath());
        }
        return rawImageFile;
    }

    // Returns path for the raw folder used to store the original images for a form
    public static String getRawDirectoryPath(String instanceFolderPath) {
        return instanceFolderPath + "/raw";
    }

    /**
     * Processes the return from an image capture intent, launched by either an ImageWidget or
     * SignatureWidget
     *
     * @param isImage true if this was from an ImageWidget, false if it was a SignatureWidget
     * @return if saving the captured image was successful
     */
    public static boolean processCaptureResponse(FormEntryActivity activity,
                                                 String instanceFolder,
                                                 boolean isImage) {
        /* We saved the image to the tempfile_path, but we really want it to be in:
         * /sdcard/odk/instances/[current instance]/something.[jpg/png/etc] so we move it there
         * before inserting it into the content provider. Once the android image capture bug gets
         * fixed, (read, we move on from Android 1.6) we want to handle images the audio and
         * video
         */

        // The intent is empty, but we know we saved the image to the temp file
        File originalImage = ImageWidget.getTempFileForImageCapture();
        try {
            return scaleAndSaveImage(originalImage, isImage, instanceFolder, activity);
        } catch (IOException e) {
            Logger.exception("Error while trying to save captured image", e);
            Toast.makeText(activity, Localization.get("image.capture.not.saved"), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static void processImageChooserResponse(FormEntryActivity activity,
                                                   String instanceFolder,
                                                   Intent intent) {
        /* We have a saved image somewhere, but we really want it to be in:
         * /sdcard/odk/instances/[current instance]/something.[jpg/png/etc] so we move it there
         * before inserting it into the content provider. Once the android image capture bug gets
         * fixed, (read, we move on from Android 1.6) we want to handle images the audio and
         * video
         */

        // get gp of chosen file
        Uri selectedImage = intent.getData();

        if (selectedImage == null) {
            showInvalidImageMessage(activity);
            return;
        }
        if (!FileUtil.isSupportedMultiMediaFile(activity, selectedImage)) {
            Toast.makeText(activity,
                    Localization.get("form.attachment.invalid"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        processImageGivenFileUri(activity, instanceFolder, selectedImage);
    }

    private static void processImageGivenFileUri(FormEntryActivity activity, String instanceFolder, Uri imageUri) {
        InputStream inputStream;
        try {
            inputStream = activity.getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            showInvalidImageMessage(activity);
            return;
        }

        try {
            // First make a copy of the image to operate on and then pass it to the File function
            File finalFile = new File(CommCareApplication.instance().
                    getExternalTempPath(FileUtil.getFileName(activity, imageUri)));
            FileUtil.copyFile(inputStream, finalFile);
            processImageGivenFilePath(activity, instanceFolder, finalFile.getAbsolutePath());
        } catch (FileExtensionNotFoundException e) {
            Logger.exception("Error while processing chosen image ", e);
            Toast.makeText(activity, Localization.get("image.selection.invalid.extension"), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Logger.exception("Error while processing chosen image ", e);
            Toast.makeText(activity, Localization.get("image.selection.not.saved"), Toast.LENGTH_LONG).show();
        }
    }

    private static void processImageGivenFilePath(FormEntryActivity activity, String instanceFolder, String imagePath) {
        if (imagePath == null) {
            showInvalidImageMessage(activity);
            return;
        }

        File originalImage = new File(imagePath);

        if (originalImage.exists()) {
            try {
                scaleAndSaveImage(originalImage, true, instanceFolder, activity);
            } catch (IOException e) {
                Logger.exception("Error while saving chosen image ", e);
                Toast.makeText(activity, Localization.get("image.selection.not.saved"), Toast.LENGTH_LONG).show();
            }
        } else {
            // The user has managed to select a file from the image browser that doesn't actually
            // exist on the file system anymore
            Toast.makeText(activity, Localization.get("invalid.image.selection"), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean scaleAndSaveImage(File originalImage, boolean shouldScale,
                                             String instanceFolder, FormEntryActivity activity) throws IOException {
        Pair<File, String> rawImageAndScaledPath = moveAndScaleImage(originalImage, shouldScale, instanceFolder, activity);
        File fileToBeUploaded = new File(rawImageAndScaledPath.second);
        if (FileUtil.isFileTooLargeToUpload(fileToBeUploaded)) {
            fileToBeUploaded.delete();
            activity.showFileOversizeError();
            return false;
        }
        activity.saveImageWidgetAnswer(rawImageAndScaledPath.first.getAbsolutePath());
        return true;
    }

    private static void showInvalidImageMessage(FormEntryActivity activity) {
        Toast.makeText(activity, Localization.get("invalid.image.selection"), Toast.LENGTH_LONG).show();
    }

    public static void processImageFromBroadcast(FormEntryActivity activity, String instanceFolder) {
        processImageGivenFilePath(activity, instanceFolder, sCustomImagePath);
    }

    public static void setCustomImagePath(String filePath) {
        sCustomImagePath = filePath;
    }

    public static String getCustomImagePath() {
        return sCustomImagePath;
    }
}
