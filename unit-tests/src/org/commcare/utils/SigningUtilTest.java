package org.commcare.utils;

import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SigningUtilTest {

    @Test
    public void parseSMSInstallMessage() throws UnsupportedEncodingException, Base64DecoderException {
        String exampleSMS = "[commcare app - do not delete] aHR0cHM6Ly93d3cuY29tbWNhcmVocS5vcmcvYS9nYy9zbXMvYXBwX2luZm8vN2M3ZDQ5ZmJlZjU5YjcwM2ZiNDY4ZTIwZDUyYTIxZTQv";
        String encodedURL = SigningUtil.trimMessagePayload(exampleSMS);
        String decodedURL = SigningUtil.decodeUrl(encodedURL);
        assertTrue(decodedURL.startsWith("https://www.commcarehq.org"));
    }

    @Test
    public void validateBaseEncodedURLTest() throws UnsupportedEncodingException, Base64DecoderException {
        // decode a valid URL
        String goodUrlBase = "https://www.commcarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/";
        String goodUrl = Base64.encode(goodUrlBase.getBytes("UTF-8"));
        assertEquals(goodUrlBase, SigningUtil.decodeUrl(goodUrl));

        // try to decode a 'malicious', of non-commcarehq origin, URL
        String badUrlBase = "https://www.corncarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/";
        String badUrl = Base64.encode(badUrlBase.getBytes("UTF-8"));
        boolean didFail = false;
        try {
            SigningUtil.decodeUrl(badUrl);
        } catch (RuntimeException e) {
            didFail = true;
        }
        assertTrue(didFail);
    }
}
