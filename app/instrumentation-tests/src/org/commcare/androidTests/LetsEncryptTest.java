package org.commcare.androidTests;

import android.os.Build;

import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.HashMap;

import javax.net.ssl.SSLHandshakeException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Let's Encrypt expiring root test.
 *
 * Read https://community.letsencrypt.org/t/mobile-client-workarounds-for-isrg-issue/137807
 * for background.
 */
@RunWith(AndroidJUnit4.class)
public class LetsEncryptTest {

    private static final String ISRG_TEST_URL = "https://valid-isrgrootx1.letsencrypt.org/robots.txt"; // ISRG Root X1
    private static final String GOOGLE_TEST_URL = "https://www.google.com/robots.txt"; // GlobalSign
    private static final String COMMCARE_TEST_URL = "http://www.commcarehq.org/serverup.txt"; // AWS

    // DST Root CA X3 , will shift to ISRG Root X1 on 1 Sep 2021 and can be removed there after
    private static final String SWISS_TEST_URL = "https://swiss.commcarehq.org/serverup.txt";

    private CommCareNetworkService commCareNetworkService;

    @Before
    public void setUp() {
        commCareNetworkService = CommCareNetworkServiceGenerator.createNoAuthCommCareNetworkService();
    }

    @Test
    public void getPassesWithoutException() throws IOException {
        makeGetRequest(ISRG_TEST_URL, 404);
        makeGetRequest(SWISS_TEST_URL, 200);
        makeGetRequest(COMMCARE_TEST_URL, 200);
        makeGetRequest(GOOGLE_TEST_URL, 200);
    }

    private void makeGetRequest(String url, int expectedCode) throws IOException {
        Response<ResponseBody> response = commCareNetworkService.makeGetRequest(url, new HashMap<>(), new HashMap<>()).execute();
        assertTrue(response.code() == expectedCode);
        assertEquals(Protocol.HTTP_2, response.raw().protocol());
        if (response.body() != null) {
            response.body().close();
        }
    }
}
