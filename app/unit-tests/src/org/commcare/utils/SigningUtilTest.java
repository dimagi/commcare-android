package org.commcare.utils;

import org.junit.Assert;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Tests for util methods surrounding processing of app install SMSs
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SigningUtilTest {

    public static final String TEST_PUBLIC_KEY_STRING = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCfn1CCWi3U8Im3cFs0Q8Uw8+fR4fAAEuytzABgLrRQAZLMLrpxHkpJQ80L4KV9upSnAw69IKYXdGKtrn5ne40XIPYWIHAsUnZTqQn9talWAolSHVYMpMWxi+6o1eJr2YbPLJ3yOYmb1lhU0o6FnmBfANsQk/RWV+QtRlO86ARq3wIDAQAB";

    @Test
    public void parseSMSInstallMessage() throws UnsupportedEncodingException, Base64DecoderException {
        String exampleSMS = "[commcare app - do not delete] aHR0cHM6Ly93d3cuY29tbWNhcmVocS5vcmcvYS9nYy9zbXMvYXBwX2luZm8vN2M3ZDQ5ZmJlZjU5YjcwM2ZiNDY4ZTIwZDUyYTIxZTQv";
        String encodedURL = SigningUtil.trimMessagePayload(exampleSMS);
        String decodedURL = SigningUtil.decodeUrl(encodedURL);
        assertTrue(decodedURL.startsWith("https://www.commcarehq.org"));

        // check that the old format still works
        String legacyExampleSMS = "[commcare app - do not delete] https://www.commcarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/";
        String legacyEncodedURL = SigningUtil.trimMessagePayload(legacyExampleSMS);
        String legacyDecodedURL = SigningUtil.decodeUrl(legacyEncodedURL);
        assertTrue(legacyDecodedURL.startsWith("https://www.commcarehq.org"));
    }

    @Test
    public void validateBaseEncodedURLTest() throws UnsupportedEncodingException, Base64DecoderException {
        // decode a valid URL
        String goodUrlBase = "https://www.commcarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/";
        String goodUrl = Base64.encode(goodUrlBase.getBytes("UTF-8"));
        assertEquals(goodUrlBase, SigningUtil.decodeUrl(goodUrl));

        // try to decode a 'malicious', of non-commcarehq origin, URL
        assertWhitelistURLFailure("https://www.corncarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/");
        assertWhitelistURLFailure("https://zcommcarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/");
    }

    private void assertWhitelistURLFailure(String url) throws UnsupportedEncodingException, Base64DecoderException {
        String badUrl = Base64.encode(url.getBytes("UTF-8"));
        boolean didFail = false;
        try {
            SigningUtil.decodeUrl(badUrl);
        } catch (RuntimeException e) {
            didFail = true;
        }
        assertTrue(didFail);
    }


    @Test
    public void parseBasicSignature() throws Exception {
        //NOTE: The Test certificate and tool to generate signatures is in the "test_assets" directory

        byte[] signatureBytes = SigningUtil.getBytesFromString("byegtc7fDpGkp3Y7MgyQtcPI6qTfzz/zORYkWjAS/H77V7E4J/F4ElUIPuM9YUnir8krQvTd8/yzopZLcfTap/p5+3cJXVGo59RoZJpdnrXoD1KKZUn9FNpifejP08lq3pdBf2kjuSCupZ+u3BZLSc1gcZZQGI9IPZMY/O7qnFs=");
        Assert.assertEquals("TEST",
                SigningUtil.verifyMessageAndBytes(TEST_PUBLIC_KEY_STRING, "TEST", signatureBytes));

    }

}
