package org.commcare.utils;

import android.graphics.Bitmap;

public enum ImageType {
    JPEG(Bitmap.CompressFormat.JPEG),
    PNG(Bitmap.CompressFormat.PNG);

    private final Bitmap.CompressFormat format;

    ImageType(Bitmap.CompressFormat format) {
        this.format = format;
    }

    public Bitmap.CompressFormat getCompressFormat() {
        return this.format;
    }

    public static ImageType fromExtension(String extension) {
        switch (extension.toLowerCase()) {
            case "jpeg":
            case "jpg":
                return JPEG;
            case "png":
                return PNG;
            default:
                return null;
        }
    }
}
