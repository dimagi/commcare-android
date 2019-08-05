package org.commcare.tasks;

import org.commcare.utils.FileUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.locale.Localization;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MultimediaInflaterTask<R> extends UnzipTask {

    @Override
    protected String getInvalidZipFileErrorMessage() {
        return Localization.get("mult.install.bad");
    }
}
