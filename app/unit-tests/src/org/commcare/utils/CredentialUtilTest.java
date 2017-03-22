package org.commcare.utils;

import junit.framework.Assert;

import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Test for the basic wrapping scheme
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
public class CredentialUtilTest {

    @Test
    public void testWrappedStrings() {
        Assert.assertEquals("sha256$4bf7cdc2hhMjU2JDRiZjdjZE1USXpRQ01rSlRFeTEzMGM4ZD0=130c8d=",
                CredentialUtil.wrap("123@#$%12", "sha256$4bf7cd","130c8d="));

        Assert.assertEquals("sha256$8f5008c2hhMjU2JDhmNTAwOFlXSmpNVEl6TFE9PTRhNjBhOT0=4a60a9=",
                    CredentialUtil.wrap("abc123-", "sha256$8f5008","4a60a9="));

        Assert.assertEquals("sha256$29df66c2hhMjU2JDI5ZGY2NklDRkFJeVFsWGlZcUtDbGZLeTFjYTQwN2VkPQ==a407ed=",
                CredentialUtil.wrap(" !@#$%^&*()_+-\\", "sha256$29df66","a407ed="));

        Assert.assertEquals("sha256$ad5e3ac2hhMjU2JGFkNWUzYTRLU0o0S1NxNEtTVjRLU3c0S1NqTVRJejQyNDgyOT0=424829=",
                CredentialUtil.wrap("उपकरण123", "sha256$ad5e3a","424829="));

        Assert.assertEquals("sha256$1e2d5bc2hhMjU2JDFlMmQ1Yk1USXpORFUyZjc5MTI3PQ==f79127=",
                CredentialUtil.wrap("123456", "sha256$1e2d5b","f79127="));

    }

    @Test
    public void testUnwrap() {
        Assert.assertEquals("123456", CredentialUtil.unwrap("sha256$1e2d5bc2hhMjU2JDFlMmQ1Yk1USXpORFUyZjc5MTI3PQ==f79127="));
        Assert.assertEquals(" !@#$%^&*()_+-\\", CredentialUtil.unwrap("sha256$29df66c2hhMjU2JDI5ZGY2NklDRkFJeVFsWGlZcUtDbGZLeTFjYTQwN2VkPQ==a407ed="));

    }

    @Test
    public void testRoundTrip() {
        Assert.assertEquals("123456", CredentialUtil.unwrap(CredentialUtil.wrap("123456")));
        Assert.assertEquals("abc123-", CredentialUtil.unwrap(CredentialUtil.wrap("abc123-")));
        Assert.assertEquals("!@#$%^&*()_+-\\\\", CredentialUtil.unwrap(CredentialUtil.wrap("!@#$%^&*()_+-\\\\")));
        Assert.assertEquals("उपकरण123", CredentialUtil.unwrap(CredentialUtil.wrap("उपकरण123")));
    }
}

