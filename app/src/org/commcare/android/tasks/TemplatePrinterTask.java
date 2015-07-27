package org.commcare.android.tasks;

import android.os.AsyncTask;
import android.os.Bundle;

import java.io.File;
import java.io.IOException;

import org.commcare.android.util.TemplatePrinterUtils;


/**
 * Asynchronous task for populating an html document with data.
 *
 * @author Richard Lu
 * @author amstone
 */
public class TemplatePrinterTask extends AsyncTask<Void, Void, Integer> {

    /**
     * The 3 result codes that can be sent back by this task
     */
    public static final int SUCCESS = 0;
    public static final int IO_ERROR = 1;
    public static final int VALIDATION_ERROR = 2;

    /**
     * The template file for this print action
     */
    private final File inputFile;

    /**
     * The path where the populated template should be saved to
     */
    private final String outputPath;

    /**
     * The mapping from keywords to case property values to be used in populating the template
     */
    private final Bundle values;

    private final PopulateListener listener;


    public TemplatePrinterTask(File input, String outputPath, Bundle values,
                               PopulateListener listener) {
        this.inputFile = input;
        this.outputPath = outputPath;
        this.values = values;
        this.listener = listener;
    }

    /**
     * Attempts to perform population of the template file, and throws the appropriate exception
     * if encountering an error.
     */
    @Override
    protected Integer doInBackground(Void... params) {
        try {
            populateHtml(inputFile, values);
            return SUCCESS;
        } catch (IOException e) {
            return IO_ERROR;
        } catch (RuntimeException e) {
            return VALIDATION_ERROR;
        }
    }

    /**
     * Receives the return value from doInBackground and proceeds accordingly
     */
    @Override
    protected void onPostExecute(Integer result) {
        listener.onFinished(result);
    }

    /**
     * Populates an html print template based on the given set of key-value pairings
     * and save the newly-populated template to a temp location
     *
     * @param input the html print template
     * @param values the mapping of keywords to case property values
     * @throws IOException
     */
    private void populateHtml(File input, Bundle values) throws IOException {
        // Read from input file
        // throws IOException
        String fileText = TemplatePrinterUtils.docToString(input);

        // Check if <body></body> section of html string is properly formed
        // throws RuntimeException
        int startBodyIndex = fileText.indexOf("<body");
        String beforeBodySection = fileText.substring(0, startBodyIndex);
        String bodySection = fileText.substring(startBodyIndex);
        validateStringOrThrowException(bodySection);

        // Swap out place-holder keywords for case property values within <body></body> section
        bodySection = replace(bodySection, values);

        // Write the new HTML to the desired  temp file location
        // throws IO Exception
        TemplatePrinterUtils.writeStringToFile(beforeBodySection + bodySection, outputPath);
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
        return TemplatePrinterUtils.join(tokens);
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
                    if (input.charAt(i) == '{') {
                        isBetweenMustaches = true;
                    } else {
                        isBetweenMustaches = false;
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
            throw new RuntimeException();
        }

    }

    /**
     * A listener for this task, implemented by TemplatePrinterActivity
     */
    public interface PopulateListener {
        void onFinished(int result);
    }
}
