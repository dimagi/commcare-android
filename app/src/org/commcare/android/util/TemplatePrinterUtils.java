package org.commcare.android.util;

import android.text.TextUtils;

/**
 * Various utilities used by TemplatePrinterTask and TemplatePrinterActivity
 * 
 * @author Richard Lu
 */
public abstract class TemplatePrinterUtils {

    private static final String FORMAT_REGEX_WITH_DELIMITER = "((?<=%2$s)|(?=%1$s))";
    
    /**
     * Returns a copy of the byte array, truncated to the specified length.
     * 
     * @param array Input array
     * @param length Length to truncate to; must be less than or equal to array.length
     * @return Copied, truncated array
     */
    public static byte[] copyOfArray(byte[] array, int length) {
        byte[] result = new byte[length];
        
        for (int i=0; i<result.length; i++) {
            result[i] = array[i];
        }
        
        return result;
    }

    /**
     * Gets the file extension from the given file path.
     *
     * @param path File path
     * @return File extension
     */
    public static String getExtension(String path) {
        return last(path.split("\\."));
    }

    /**
     * Concatenate all Strings in a String array to one String.
     *
     * @param strings String array to join
     * @return Joined String
     */
    public static String join(String[] strings) {
        return TextUtils.join("", strings);
    }

    /**
     * Get the last element of a String array.
     * @param strings String array
     * @return Last element
     */
    public static String last(String[] strings) {
        return strings[strings.length - 1];
    }

    /**
     * Remove all occurrences of the specified String segment
     * from the input String.
     *
     * @param input String input to remove from
     * @param toRemove String segment to remove
     * @return input with all occurrences of toRemove removed
     */
    public static String remove(String input, String toRemove) {
        return TextUtils.join("", input.split(toRemove));
    }

    /**
     * Split a String while keeping the specified start and end delimiters.
     *
     * Sources:
     * http://stackoverflow.com/questions/2206378/how-to-split-a-string-but-also-keep-the-delimiters
     *
     * @param input String to split
     * @param delimiterStart Start delimiter; will split immediately before this delimiter
     * @param delimiterEnd End delimiter; will split immediately after this delimiter
     * @return Split string array
     */
    public static String[] splitKeepDelimiter(
            String input,
            String delimiterStart,
            String delimiterEnd) {

        String delimiter = String.format(FORMAT_REGEX_WITH_DELIMITER, delimiterStart, delimiterEnd);

        return input.split(delimiter);
    }

}
