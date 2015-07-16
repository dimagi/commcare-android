package org.commcare.android.tasks;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.commcare.android.util.TemplatePrinterEncryptedStream;
import org.commcare.android.util.TemplatePrinterUtils;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * Asynchronous task for populating a document with data.
 *
 * @author Richard Lu
 * @author amstone
 */
public class TemplatePrinterTask extends AsyncTask<Void, Void, Boolean> {
    
    public enum DocTypeEnum {

        DOCX("word/document.xml"),
        HTML(""),
        ODT("content.xml");

        public static DocTypeEnum getDocTypeFromExtension(String extension) {
            for (DocTypeEnum docType : DocTypeEnum.values()) {
                if (docType.toString().equalsIgnoreCase(extension)) {
                    return docType;
                }
            }
            return null;
        }

        public static boolean isSupportedExtension(String extension) {
            return getDocTypeFromExtension(extension) != null;
        }

        public final String CONTENT_FILE_NAME;

        private DocTypeEnum(String contentFileName) {
            this.CONTENT_FILE_NAME = contentFileName;
        }

    }

    public interface PopulateListener {

        public void onError(String message);
        public void onFinished();

    }

    private static final int BUFFER_SIZE = 1024;

    private final File input;
    private final Bundle values;
    private final PopulateListener listener;
    /**
     * An encrypted stream for writing to and reading back from the filled-in template to be printed
     */
    private TemplatePrinterEncryptedStream eStream;

    private String errorMessage;

    public TemplatePrinterTask(File input, TemplatePrinterEncryptedStream eStream, Bundle values,
                               PopulateListener listener) {
        this.input = input;
        this.eStream = eStream;
        this.values = values;
        this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        boolean success = false;

        try {
            success = populateHtml(input, values);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        return success;
    }

    @Override
    protected void onPostExecute(Boolean success) {

        if (success) {
            listener.onFinished();
        } else {
            listener.onError(errorMessage);
        }

    }

    private boolean populateHtml(File input, Bundle values) throws Exception {
        String fileText = "";
        try {
            BufferedReader in = new BufferedReader(new FileReader(input));
            String str;
            while ((str = in.readLine()) != null) {
                fileText +=str;
            }
            fileText = replace(fileText, values);
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(CommCareApplication._().getTempFilePath() + ".html"));
            out.write(fileText);

            in.close();
            out.close();
            return true;
        } catch (IOException e) {
            throw new Exception("Failed to read/populate html");
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
     * @return if the population was successful
     * @throws IOException
     */
    /*private boolean populate(File input, Bundle values) throws IOException {

        String inputExtension = TemplatePrinterUtils.getExtension(input.getName());

        if (DocTypeEnum.isSupportedExtension(inputExtension)) {

            DocTypeEnum docType = DocTypeEnum.getDocTypeFromExtension(inputExtension);
            if (docType.equals(DocTypeEnum.HTML)) {
                return populateHtml(input, values);
            }

            ZipFile file = new ZipFile(input);
            Enumeration entries = file.entries();

            // Get an encrypted output stream for writing the filled-in template to the temp
            // filepath in .docx format
            //ZipOutputStream outputStream = eStream.getHtmlOutputStream();

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

        return true;
    }*/

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
                    Log.i("token: ", tokenSplit);

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

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '{') {
                if (isBetweenMustaches) {
                    Log.i("1", "breaks here");
                    isWellFormed = false;
                    break;
                } else {
                    i++;
                    if (input.charAt(i) != '{') {
                        //Log.i("2", "breaks here");
                        //isWellFormed = false;
                        isBetweenMustaches = false;
                        //break;
                    } else {
                        isBetweenMustaches = true;
                    }
                }
            } else if (c == '}') {
                if (isBetweenMustaches) {
                    i++;
                    if (input.charAt(i) != '}') {
                        Log.i("3", "breaks here");
                        isWellFormed = false;
                        break;
                    } else {
                        isBetweenMustaches = false;
                    }
                } else {
                    //Log.i("4", "breaks here");
                    //isWellFormed = false;
                    //break;
                }
            } else if (c == '<') {
                if (isBetweenChevrons) {
                    Log.i("5", "breaks here");
                    isWellFormed = false;
                    break;
                } else {
                    isBetweenChevrons = true;
                }
            } else if (c == '>') {
                if (isBetweenChevrons) {
                    isBetweenChevrons = false;
                } else {
                    Log.i("6", "breaks here");
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
