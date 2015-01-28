package org.commcare.android.tasks;

import android.os.AsyncTask;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.commcare.android.util.TemplatePrinterUtils;

/**
 * Asynchronous task for populating a document with data.
 *
 * @author Richard Lu
 */
public class TemplatePrinterTask extends AsyncTask<Void, Void, File> {
    
    public enum DocTypeEnum {

        DOCX("word/document.xml"),
        ODT("content.xml");

        public static DocTypeEnum getFromExtension(String extension) {
            for (DocTypeEnum docType : DocTypeEnum.values()) {
                if (docType.toString().equalsIgnoreCase(extension)) {
                    return docType;
                }
            }
            return null;
        }

        public static boolean isSupportedExtension(String extension) {
            return getFromExtension(extension) != null;
        }

        public final String CONTENT_FILE_NAME;

        private DocTypeEnum(String contentFileName) {
            this.CONTENT_FILE_NAME = contentFileName;
        }

    }

    public interface PopulateListener {

        public void onError(String message);
        public void onFinished(File result);

    }

    private static final int BUFFER_SIZE = 1024;

    private final File input;
    private final File outputFolder;
    private final Bundle values;
    private final PopulateListener listener;

    private String errorMessage;

    public TemplatePrinterTask(File input, File outputFolder, Bundle values, PopulateListener listener) {

        this.input = input;
        this.outputFolder = outputFolder;
        this.values = values;
        this.listener = listener;

    }

    @Override
    protected File doInBackground(Void... params) {

        File result = null;

        try {
            result = populate(input, values);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        return result;
    }

    @Override
    protected void onPostExecute(File result) {

        if (result != null) {
            listener.onFinished(result);
        } else {
            listener.onError(errorMessage);
        }

    }

    /**
     * Populate an input document with attribute keys formatted as {{ attr_key }}
     * with attribute values.
     *
     * Sources:
     * http://isip-blog.blogspot.com/2010/04/extracting-xml-files-from-odt-file-in.html
     * http://stackoverflow.com/questions/11502260/modifying-a-text-file-in-a-zip-archive-in-java
     *
     * @param input Document (ODT/DOCX) file
     * @param values Bundle of String attribute key-value mappings
     * @return Populated document
     * @throws IOException
     */
    private File populate(File input, Bundle values) throws IOException {

        // Sans file extension
        String inputName = input.getName().substring(0, input.getName().lastIndexOf('.'));
        String inputExtension = TemplatePrinterUtils.getExtension(input.getName());

        File output;

        if (DocTypeEnum.isSupportedExtension(inputExtension)) {

            DocTypeEnum docType = DocTypeEnum.getFromExtension(inputExtension);

            // Append suffice and file extension to output file name
            output = new File(outputFolder, inputName + "-out." + inputExtension);

            ZipFile file = new ZipFile(input);
            Enumeration entries = file.entries();

            ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(output));

            while (entries.hasMoreElements()) {

                ZipEntry entry = (ZipEntry) entries.nextElement();

                InputStream inputStream = file.getInputStream(entry);

                byte[] byteBuffer = new byte[BUFFER_SIZE];
                int numBytesRead;

                outputStream.putNextEntry(new ZipEntry(entry.getName()));

                if (entry.getName().equals(docType.CONTENT_FILE_NAME)) {

                    // Read entire file as String
                    String fileText = "";

                    while ((numBytesRead = inputStream.read(byteBuffer)) > 0) {
                        fileText += new String(TemplatePrinterUtils.copyOfArray(byteBuffer, numBytesRead));
                    }

                    // Populate String
                    if (fileText.contains("{{")) {
                        fileText = replace(fileText, values);
                    }

                    byte[] fileTextBytes = fileText.getBytes();

                    outputStream.write(fileTextBytes, 0, fileTextBytes.length);

                } else {

                    // Straight up copy file
                    while ((numBytesRead = inputStream.read(byteBuffer)) > 0) {
                        outputStream.write(byteBuffer, 0, numBytesRead);
                    }

                }

                outputStream.closeEntry();
                inputStream.close();

            }

            outputStream.close();
            file.close();

        } else {
            throw new RuntimeException("Not a supported file format");
        }

        return output;
    }

    /**
     * Populate an input string with attribute keys formatted as {{ attr_key }}
     * with attribute values.
     * @param input String input
     * @param values Bundle of String attribute key-value mappings
     * @return The populated String
     */
    private static String replace(String input, Bundle values) {

        validateStringOrThrowException(input);

        // Split input into tokens bounded by {{ and }}
        String[] tokens = TemplatePrinterUtils.splitKeepDelimiter(input, "\\{{2}", "\\}{2}");

        for (int i=0; i<tokens.length; i++) {
            String token = tokens[i];

            // Every 2nd token is a attribute enclosed in {{ }}
            if (i % 2 == 1) {

                // Split token into tokenSplits bounded by < and >
                String[] tokenSplits = TemplatePrinterUtils.splitKeepDelimiter(token, "<|(\\}{2})", ">|(\\{{2})");

                // First and last tokenSplits are {{ and }}
                for (int j=1; j<tokenSplits.length-1; j++) {
                    String tokenSplit = tokenSplits[j];

                    // tokenSplit is key or whitespace
                    if (!tokenSplit.startsWith("<")) {

                        // Remove whitespace from key
                        String key = TemplatePrinterUtils.remove(tokenSplit, " ");

                        if (values.containsKey(key) && (key = values.getString(key)) != null) {
                            // Populate with value
                            tokenSplits[j] = key;
                        } else {
                            // Empty if not found
                            tokenSplits[j] = "";
                        }
                    }

                }

                // Remove {{ and }}
                tokenSplits[0] = "";
                tokenSplits[tokenSplits.length-1] = "";

                // Reconstruct token
                tokens[i] = TemplatePrinterUtils.join(tokenSplits);
            }
        }

        // Reconstruct input
        String result = TemplatePrinterUtils.join(tokens);

        return result;
    }

    /**
     * Validates the input string for well-formed {{ }} and < > pairs.
     * If malformed, throws a RuntimeException which will be caught by
     * doInBackground() and passed to the attached PopulateListener.onError()
     * 
     * @param input String to validate
     */
    private static void validateStringOrThrowException(String input) {

        boolean isBetweenMustaches = false;
        boolean isBetweenChevrons = false;
        boolean isWellFormed = true;

        for (int i=0; i<input.length(); i++) {
            char c = input.charAt(i);

            if (c == '{') {
                if (isBetweenMustaches) {
                    isWellFormed = false;
                    break;
                } else {
                    i++;
                    if (input.charAt(i) != '{') {
                        isWellFormed = false;
                        break;
                    } else {
                        isBetweenMustaches = true;
                    }
                }
            } else if (c == '}') {
                if (isBetweenMustaches) {
                    i++;
                    if (input.charAt(i) != '}') {
                        isWellFormed = false;
                        break;
                    } else {
                        isBetweenMustaches = false;
                    }
                } else {
                    isWellFormed = false;
                    break;
                }
            } else if (c == '<') {
                if (isBetweenChevrons) {
                    isWellFormed = false;
                    break;
                } else {
                    isBetweenChevrons = true;
                }
            } else if (c == '>') {
                if (isBetweenChevrons) {
                    isBetweenChevrons = false;
                } else {
                    isWellFormed = false;
                    break;
                }
            }
        }

        if (!isWellFormed) {
            throw new RuntimeException("Ill-formed input string: " + input);
        }

    }
}
