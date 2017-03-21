package org.commcare.utils;

import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ctsims on 3/15/2017.
 */

public class CredentialUtil {
    public static final String TAG = CredentialUtil.class.getName();

    private static final int SALT_LENGTH = 6;
    public static final String saltCharSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Pattern pattern = Pattern.compile("(sha256\\$......)(.*)(......=)");

    public static String generateSalt(int chars) {
        String salt = "";
        for(int i = 0 ; i < chars ;i++) {
            salt += saltCharSet.charAt(new Random().nextInt(saltCharSet.length()));
        }
        return salt;
    }

    public static String wrap(String input) {
        String salt1 = "sha256$" + generateSalt(SALT_LENGTH);
        String salt2 = generateSalt(SALT_LENGTH) + "=";

        return wrap(input, salt1, salt2);
    }

    public static String wrap(String input, String salt1, String salt2) {
        try {
            String encodedPw = Base64.encode(input.getBytes("UTF-8"));
            String encoded = Base64.encode((salt1 + encodedPw + salt2).getBytes("UTF-8"));
            return String.format("%s%s%s", salt1, encoded, salt2);
        } catch (UnsupportedEncodingException iee) {
            throw new RuntimeException(iee);
        }
    }

    public static String unwrap(String input) {
        Matcher m = pattern.matcher(input);
        if(!m.matches()) {
            System.out.println("Couldn't unwrap: " + input);
            return null;
        }

        if(m.groupCount() != 3 ) {
            System.out.println("Couldn't unwrap (missing groups): " + input);
            return null;
        }
        String salt1 = m.group(1);
        String encoded  = m.group(2);
        String salt2 = m.group(3);



        try {
            String decoded = new String(Base64.decode(encoded), "UTF-8");
            return new String(Base64.decode(decoded.substring(salt1.length(),
                    decoded.length() - salt2.length())), "UTF-8");
        } catch (UnsupportedEncodingException iee) {
            throw new RuntimeException(iee);
        } catch (Base64DecoderException e) {
            System.out.println("Couldn't unwrap (base64 exception): " + input);
            e.printStackTrace();
            return null;
        }
    }

}
