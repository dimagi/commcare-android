package org.commcare.android.util;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Various utilities used by TemplatePrinterTask and TemplatePrinterActivity
 * 
 * @author Richard Lu
 * @author amstone
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

    public static String docToString(File file) throws IOException {
        String fileText = "";
        BufferedReader in = new BufferedReader(new FileReader(file));
        String str;
        while ((str = in.readLine()) != null) {
            fileText += str;
        }
        in.close();
        return fileText;
    }

    /*public static void docxToPDF(TemplatePrinterEncryptedStream eStream) {
        try {
            // 1) Load DOCX into XWPFDocument
            //InputStream in= new FileInputStream(new File("HelloWord.docx"));
            XWPFDocument document = new XWPFDocument(eStream.getDocxInputStream());

            // 2) Prepare Pdf options
            PdfOptions options = PdfOptions.create();

            // 3) Convert XWPFDocument to Pdf
            //OutputStream out = new FileOutputStream(new File("HelloWord.pdf"));
            OutputStream out = eStream.getPdfOutputStream();
            PdfConverter.getInstance().convert(document, out, options);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    /*public static void docxToPDF(TemplatePrinterEncryptedStream eStream,
                                 String pathWithoutExtension) {

        try {
            // 1) Document loading (required)
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(
                    eStream.getDocxInputStream());

            // 2) Refresh the values of DOCPROPERTY fields
            FieldUpdater updater = new FieldUpdater(wordMLPackage);
            updater.update(true);

            // 3) FO exporter setup (required)
            FOSettings foSettings = Docx4J.createFOSettings();
            foSettings.setFoDumpFile(new File(pathWithoutExtension + ".fo"));  // not needed?
            foSettings.setWmlPackage(wordMLPackage);

            // 4) Exporter writes to an OutputStream.
            OutputStream os = eStream.getPdfOutputStream();

            // 5) Specify whether PDF export uses XSLT or not to create the FO
            // (XSLT takes longer, but is more complete).
            Docx4J.toFO(foSettings, os, Docx4J.FLAG_EXPORT_PREFER_XSL);

            // Clean up, so any ObfuscatedFontPart temp files can be deleted
            updater = null;
            foSettings = null;
            wordMLPackage = null;

        } catch (Docx4JException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

}
