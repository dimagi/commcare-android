package org.commcare.android.tasks;

import android.os.AsyncTask;
import android.os.Bundle;

import java.io.File;

import org.commcare.android.util.TemplatePrinterIOUtil;
import org.commcare.android.util.TemplatePrinterUtils;


/**
 * Asynchronous task for populating an html document with data.
 *
 * @author Richard Lu
 * @author amstone
 */
public class TemplatePrinterTask extends AsyncTask<Void, Void, Boolean> {

    private static final int BUFFER_SIZE = 1024;

    private File input;
    private String outputPath;
    private final Bundle values;
    private final PopulateListener listener;

    private String errorMessage;

    public TemplatePrinterTask(File input, String outputPath, Bundle values,
                               PopulateListener listener) {
        this.input = input;
        this.outputPath = outputPath;
        this.values = values;
        this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            populateHtml(input, values);
            return true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return false;
        }
    }

    /**
     * Receives the return value from doInBackground and proceeds accordingly
     */
    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            listener.onFinished();
        } else {
            listener.onError(errorMessage);
        }
    }

    /**
     * Populates an html print template based on the given set of key-value pairings
     * and save the newly-populated template to a temp location
     *
     * @param input the html print template
     * @param values the mapping of keywords to case property values
     * @throws Exception
     */
    private void populateHtml(File input, Bundle values) throws Exception {
        // Read from input file
        String fileText = TemplatePrinterUtils.docToString(input);

        // Check if the <body></body> section of the html string is properly formed
        int startBodyIndex = fileText.indexOf("<body");
        validateStringOrThrowException(fileText.substring(startBodyIndex));

        // Swap out place-holder keywords for case property values
        fileText = replace(fileText, values);

        // Write the new HTML to the desired  temp file location
        TemplatePrinterIOUtil.writeStringToFile(fileText, outputPath);
    }

    /**
     * Populate an input string with attribute keys formatted as {{ attr_key }}
     * with attribute values.
     * @param input String input
     * @param values Bundle of String attribute key-value mappings
     * @return The populated String
     */
    private static String replace(String input, Bundle values) {
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

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '{') {
                if (isBetweenMustaches) {
                    isWellFormed = false;
                    break;
                } else {
                    i++;
                    if (input.charAt(i) != '{') {
                        isBetweenMustaches = false;
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

    public interface PopulateListener {

        void onError(String message);
        void onFinished();

    }
}
