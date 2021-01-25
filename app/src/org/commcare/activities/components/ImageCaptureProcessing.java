package org.commcare.activities.components;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.commcare.activities.FormEntryActivity;
import org.commcare.modern.util.Pair;
import org.commcare.utils.FileUtil;
import org.commcare.utils.UriToFilePath;
import org.commcare.views.widgets.ImageWidget;
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
     * @return A pair containing raw image and scaled imagePath. The first entry is the raw image while
     * the second one is path to scaled image.
     */
    private static Pair<File, String> moveAndScaleImage(File originalImage, boolean shouldScale,
                                                      String instanceFolder,
                                                      FormEntryActivity formEntryActivity) throws IOException {
        String extension = FileUtil.getExtension(originalImage.getAbsolutePath());
        String imageFilename = System.currentTimeMillis() + "." + extension;
        String finalFilePath = instanceFolder + imageFilename;

        boolean savedScaledImage = false;
        if (shouldScale) {
            ImageWidget currentWidget = (ImageWidget)formEntryActivity.getPendingWidget();
            if (currentWidget != null) {
                int maxDimen = currentWidget.getMaxDimen();
                if (maxDimen != -1) {
                    savedScaledImage = FileUtil.scaleAndSaveImage(originalImage, finalFilePath, maxDimen);
                }
            }
        }
        if (!savedScaledImage) {
            // If we didn't create a scaled image and save it to the final path, then relocate the
            // original image from the temp filepath to our final path
            File finalFile = new File(finalFilePath);

            try {
                FileUtil.copyFile(originalImage, finalFile);
            } catch (Exception e) {
                throw new IOException("Failed to rename " + originalImage.getAbsolutePath() +
                        " to " + finalFile.getAbsolutePath());
            }
            return new Pair<>(finalFile, finalFilePath);
        } else {
            // Otherwise, relocate the original image to a raw/ folder, so that we still have access
            // to the unmodified version
            String rawDirPath = getRawDirectoryPath(instanceFolder);
            File rawDir = new File(rawDirPath);
            if (!rawDir.exists()) {
                rawDir.mkdir();
            }
            File rawImageFile = new File(rawDirPath + "/" + imageFilename);
            try {
                FileUtil.copyFile(originalImage, rawImageFile);
            } catch (Exception e) {
                throw new IOException("Failed to rename " + originalImage.getAbsolutePath() +
                        " to " + rawImageFile.getAbsolutePath());
            }
            return new Pair<>(rawImageFile, finalFilePath);
        }
    }

    // Returns path for the raw folder used to store the original images for a form
    public static String getRawDirectoryPath(String instanceFolderPath){
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
            Pair<File, String> rawImageAndScaledPath = moveAndScaleImage(originalImage, isImage, instanceFolder, activity);
            if (FileUtil.isFileTooLargeToUpload(new File(rawImageAndScaledPath.second))) {
                activity.showFileOversizedError();
                return false;
            }
            activity.saveImageWidgetAnswer(rawImageAndScaledPath.first.getAbsolutePath());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
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

        try {
            String imagePath = UriToFilePath.getPathFromUri(activity, selectedImage);
            processImageGivenFilePath(activity, instanceFolder, imagePath);
        } catch (UriToFilePath.NoDataColumnForUriException e) {
            // Can't get file path from Uri, so need to work with uri instead
            processImageGivenFileUri(activity, instanceFolder, selectedImage);
        }
    }

    private static void processImageGivenFileUri(FormEntryActivity activity, String instanceFolder, Uri imageUri) {
        InputStream inputStream;
        try {
            inputStream = activity.getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            showInvalidImageMessage(activity);
            return;
        }

        // First make a copy of the image to operate on and then pass it to the File function
        String extension = FileUtil.getExtension(imageUri.getPath());
        String imageFilename = "tempfile" + "." + extension;
        String finalFilePath = instanceFolder + imageFilename;

        File finalFile = new File(finalFilePath);
        try {
            FileUtil.copyFile(inputStream, finalFile);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(activity, Localization.get("image.selection.not.saved"), Toast.LENGTH_LONG).show();
            return;
        }
        processImageGivenFilePath(activity, instanceFolder, finalFilePath);
    }

    private static void processImageGivenFilePath(FormEntryActivity activity, String instanceFolder, String imagePath) {
        if (imagePath == null) {
            showInvalidImageMessage(activity);
            return;
        }

        File originalImage = new File(imagePath);

        if (originalImage.exists()) {
            try {
                Pair<File, String> rawImageAndScaledPath = moveAndScaleImage(originalImage, true, instanceFolder, activity);
                if (FileUtil.isFileTooLargeToUpload(new File(rawImageAndScaledPath.second))) {
                    activity.showFileOversizedError();
                    return;
                }
                activity.saveImageWidgetAnswer(rawImageAndScaledPath.first.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(activity, Localization.get("image.selection.not.saved"), Toast.LENGTH_LONG).show();
            }
        } else {
            // The user has managed to select a file from the image browser that doesn't actually
            // exist on the file system anymore
            Toast.makeText(activity, Localization.get("invalid.image.selection"), Toast.LENGTH_LONG).show();
        }
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
