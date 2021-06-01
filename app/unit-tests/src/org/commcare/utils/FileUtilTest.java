package org.commcare.utils;

import org.commcare.CommCareTestApplication;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.UnsupportedEncodingException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * @author Clayton Sims (csims@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FileUtilTest {

    @Test
    public void testContentTools() throws UnsupportedEncodingException, Base64DecoderException {
        assertFalse(FileUtil.isContentUri("/path/to/data"));
        assertFalse(FileUtil.isContentUri("file:///path/to/data"));
        assertTrue(FileUtil.isContentUri("content://org.rdtoolkit.fileprovider/session_media_data/86f9bfa5-6144-4466-92e3-dc30eb3d8c85/20210420_140316_cropped.jpg"));
    }
}
