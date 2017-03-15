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
        Assert.assertEquals("sha256$1e2d5bc2hhMjU2JDFlMmQ1YjEyMzQ1NmY3OTEyNz0=f79127=",
                CredentialUtil.wrap("123456", "sha256$1e2d5b","f79127="));

        Assert.assertEquals("sha256$8f5008c2hhMjU2JDhmNTAwOGFiYzEyMy00YTYwYTk94a60a9=",
                CredentialUtil.wrap("abc123-", "sha256$8f5008","4a60a9="));

        Assert.assertEquals("sha256$29df66c2hhMjU2JDI5ZGY2NiAhQCMkJV4mKigpXystXGE0MDdlZD0=a407ed=",
                CredentialUtil.wrap(" !@#$%^&*()_+-\\", "sha256$29df66","a407ed="));
    }

    @Test
    public void testUnwrap() {
        Assert.assertEquals("123456", CredentialUtil.unwrap("sha256$1e2d5bc2hhMjU2JDFlMmQ1YjEyMzQ1NmY3OTEyNz0=f79127="));
        Assert.assertEquals(" !@#$%^&*()_+-\\", CredentialUtil.unwrap("sha256$29df66c2hhMjU2JDI5ZGY2NiAhQCMkJV4mKigpXystXGE0MDdlZD0=a407ed="));

    }

    @Test
    public void testRoundTrip() {
        Assert.assertEquals("123456", CredentialUtil.unwrap(CredentialUtil.wrap("123456")));
        Assert.assertEquals("abc123-", CredentialUtil.unwrap(CredentialUtil.wrap("abc123-")));
        Assert.assertEquals("!@#$%^&*()_+-\\\\", CredentialUtil.unwrap(CredentialUtil.wrap("!@#$%^&*()_+-\\\\")));
        Assert.assertEquals("उपकरण", CredentialUtil.unwrap(CredentialUtil.wrap("उपकरण")));
    }
}

