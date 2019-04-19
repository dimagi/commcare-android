package org.commcare.activities.components;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.widget.Toast;

import org.commcare.activities.FormEntryActivity;
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
     * @return the image file that should be displayed on the device screen when this question
     * widget is in view
     */
    private static File moveAndScaleImage(File originalImage, boolean shouldScale,
                                          String instanceFolder,
                                          FormEntryActivity formEntryActivity) throws IOException {
        String extension = FileUtil.getExtension(originalImage.getAbsolutePath());
        String imageFilename = System.currentTimeMillis() + "." + extension;
        String finalFilePath = instanceFolder + imageFilename;

        boolean savedScaledImage = false;

        // Turning off scaling for now, since current code never actually ends up using the
        // final scaled image. We might wanna turn that later on again sometime so leaving the scaling
        // code as it is
//        shouldScale = false;

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
                originalImage.delete();
            } catch (Exception e) {
                throw new IOException("Failed to rename " + originalImage.getAbsolutePath() +
                        " to " + finalFile.getAbsolutePath());
            }
            deleteFileFromMediaStore(formEntryActivity.getContentResolver(), originalImage);
            return finalFile;
        } else {
            // Otherwise, relocate the original image to a raw/ folder, so that we still have access
            // to the unmodified version
            String rawDirPath = instanceFolder + "/raw";
            File rawDir = new File(rawDirPath);
            if (!rawDir.exists()) {
                rawDir.mkdir();
            }
            File rawImageFile = new File(rawDirPath + "/" + imageFilename);
            try {
                FileUtil.copyFile(originalImage, rawImageFile);
                originalImage.delete();
            } catch (Exception e) {
                throw new IOException("Failed to rename " + originalImage.getAbsolutePath() +
                        " to " + rawImageFile.getAbsolutePath());
            }
            deleteFileFromMediaStore(formEntryActivity.getContentResolver(), originalImage);
            return rawImageFile;
        }
    }

    public static void deleteFileFromMediaStore(final ContentResolver contentResolver, final File file) {
        // Set up the projection (we only need the ID)
        String[] projection = {MediaStore.Images.Media._ID};

        // Match on the file path
        String selection = MediaStore.Images.Media.DATA + " = ?";
        String[] selectionArgs = new String[]{file.getAbsolutePath()};

        // Query for the ID of the media matching the file path
        Uri queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(queryUri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                // We found the ID. Deleting the item via the content provider will also remove the file
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri deleteUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                contentResolver.delete(deleteUri, null, null);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
            File unscaledFinalImage = moveAndScaleImage(originalImage, isImage, instanceFolder, activity);
            activity.saveImageWidgetAnswer(buildImageFileContentValues(unscaledFinalImage));
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
                File unscaledFinalImage = moveAndScaleImage(originalImage, true, instanceFolder, activity);
                activity.saveImageWidgetAnswer(buildImageFileContentValues(unscaledFinalImage));
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

    private static ContentValues buildImageFileContentValues(File unscaledFinalImage) {
        // Add the new image to the Media content provider so that the viewing is fast in Android 2.0+
        ContentValues values = new ContentValues(6);
        values.put(Media.TITLE, unscaledFinalImage.getName());
        values.put(Media.DISPLAY_NAME, unscaledFinalImage.getName());
        values.put(Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(Media.MIME_TYPE, "image/jpeg");
        values.put(Media.DATA, unscaledFinalImage.getAbsolutePath());
        return values;
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
