package org.odk.collect.android.activities.components;

import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.widgets.ImageWidget;

import java.io.File;
import java.io.IOException;

public class ImageCaptureProcessing {
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
    public static File moveAndScaleImage(File originalImage, boolean shouldScale,
                                         String instanceFolder,
                                         FormEntryActivity formEntryActivity) throws IOException {
        String extension = FileUtils.getExtension(originalImage.getAbsolutePath());
        String imageFilename = System.currentTimeMillis() + "." + extension;
        String finalFilePath = instanceFolder + imageFilename;

        boolean savedScaledImage = false;
        // TODO PLM: this scale flag should be decoupled such that getPendingWidget doesn't need to be called
        if (shouldScale) {
            ImageWidget currentWidget = (ImageWidget)formEntryActivity.getPendingWidget();
            int maxDimen = currentWidget.getMaxDimen();
            if (maxDimen != -1) {
                savedScaledImage = FileUtils.scaleImage(originalImage, finalFilePath, maxDimen);
            }
        }

        if (!savedScaledImage) {
            // If we didn't create a scaled image and save it to the final path, then relocate the
            // original image from the temp filepath to our final path
            File finalFile = new File(finalFilePath);
            if (!originalImage.renameTo(finalFile)) {
                throw new IOException("Failed to rename " + originalImage.getAbsolutePath() +
                        " to " + finalFile.getAbsolutePath());
            } else {
                return finalFile;
            }
        } else {
            // Otherwise, relocate the original image to a raw/ folder, so that we still have access
            // to the unmodified version
            String rawDirPath = instanceFolder + "/raw";
            File rawDir = new File(rawDirPath);
            if (!rawDir.exists()) {
                rawDir.mkdir();
            }
            File rawImageFile = new File(rawDirPath + "/" + imageFilename);
            if (!originalImage.renameTo(rawImageFile)) {
                throw new IOException("Failed to rename " + originalImage.getAbsolutePath() +
                        " to " + rawImageFile.getAbsolutePath());
            } else {
                return rawImageFile;
            }
        }
    }
}
