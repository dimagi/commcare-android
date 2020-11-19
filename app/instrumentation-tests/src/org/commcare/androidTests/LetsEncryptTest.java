package org.commcare.androidTests;

import android.os.Build;

import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
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

    private static final String ISRG_TEST_URL = "https://valid-isrgrootx1.letsencrypt.org/robots.txt";


    @Test
    public void getPassesWithoutException() throws IOException {
        CommCareNetworkService commCareNetworkService = CommCareNetworkServiceGenerator.createNoAuthCommCareNetworkService();
        Response<ResponseBody> response = commCareNetworkService.makeGetRequest(ISRG_TEST_URL, new HashMap<>(), new HashMap<>()).execute();
        assertTrue(response.code() == 404);
        assertEquals(Protocol.HTTP_2, response.raw().protocol());
    }
}
