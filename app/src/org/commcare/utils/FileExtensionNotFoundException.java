package org.commcare.utils;

import java.io.IOException;

/**
 * @author $|-|!˅@M
 */
public class FileExtensionNotFoundException extends IOException {
    public FileExtensionNotFoundException(String message) {
        super(message);
    }
}
