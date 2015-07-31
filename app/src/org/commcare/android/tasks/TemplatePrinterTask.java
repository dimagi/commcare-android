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
     * The 4 result codes that can be sent back by this task
     */
    public static final int SUCCESS = 0;
    public static final int IO_ERROR = 1;
    public static final int VALIDATION_ERROR_MUSTACHE = 2;
    public static final int VALIDATION_ERROR_CHEVRON = 3;

    /**
     * Used to track which type of error (2 or 3 above) was encountered in validateString
     */
    private static int validationErrorType;

    /**
     * Used to track the string in the template file where a validation error was encountered
     */
    private String problemString;

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
            problemString = e.getMessage();
            return validationErrorType;
        }
    }

    /**
     * Receives the return value from doInBackground and proceeds accordingly
     */
    @Override
    protected void onPostExecute(Integer result) {
        listener.onFinished(result, problemString);
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
        String fileText = TemplatePrinterUtils.docToString(input).toLowerCase();

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
     * doInBackground(), and trigger the appropriate result code to be
     * sent back to the attached PopulateListener
     * 
     * @param input String to validate
     */
    private static void validateStringOrThrowException(String input) {

        boolean isBetweenMustaches = false;
        boolean isBetweenChevrons = false;
        StringBuilder recentString = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            recentString.append(c);

            if (recentString.length() > 40) {
                recentString.deleteCharAt(0);
            }

            if (c == '{') {
                if (isBetweenMustaches) {
                    validationErrorType = VALIDATION_ERROR_MUSTACHE;
                    throw new RuntimeException(recentString.toString());
                } else {
                    i++;
                    c = input.charAt(i);
                    if (c == '{') {
                        isBetweenMustaches = true;
                        recentString.append(c);
                    } else {
                        isBetweenMustaches = false;
                    }
                }
            } else if (c == '}') {
                if (isBetweenMustaches) {
                    i++;
                    c = input.charAt(i);
                    if (c != '}') {
                        validationErrorType = VALIDATION_ERROR_MUSTACHE;
                        recentString.append(c);
                        throw new RuntimeException(recentString.toString());
                    } else {
                        isBetweenMustaches = false;
                    }
                }
            } else if (c == '<') {
                if (isBetweenChevrons) {
                    validationErrorType = VALIDATION_ERROR_CHEVRON;
                    throw new RuntimeException(recentString.toString());
                } else {
                    isBetweenChevrons = true;
                }
            } else if (c == '>') {
                if (isBetweenChevrons) {
                    isBetweenChevrons = false;
                } else {
                    validationErrorType = VALIDATION_ERROR_CHEVRON;
                    throw new RuntimeException(recentString.toString());
                }
            }
        }

        // If we reach the end of the string and are in between either type, should also throw error
        if (isBetweenChevrons) {
            validationErrorType = VALIDATION_ERROR_CHEVRON;
            throw new RuntimeException(recentString.toString());
        } else if (isBetweenMustaches) {
            validationErrorType = VALIDATION_ERROR_MUSTACHE;
            throw new RuntimeException(recentString.toString());
        }

    }

    /**
     * A listener for this task, implemented by TemplatePrinterActivity
     */
    public interface PopulateListener {
        void onFinished(int result, String problemString);
    }

}
