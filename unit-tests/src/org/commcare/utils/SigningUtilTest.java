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
    public void validateBaseEncodedURLTest() throws UnsupportedEncodingException, Base64DecoderException {
        String goodUrlBase = "https://www.commcarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/";
        String badUrlBase = "https://www.corncarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/";
        String goodUrl = Base64.encode(goodUrlBase.getBytes("UTF-8"));
        String badUrl = Base64.encode(badUrlBase.getBytes("UTF-8"));

        assertEquals(goodUrlBase, SigningUtil.decodeUrl(goodUrl));

        boolean didFail = false;
        try {
            SigningUtil.decodeUrl(badUrl);
        } catch (RuntimeException e) {
            didFail = true;
        }
        assertTrue(didFail);
    }
}
