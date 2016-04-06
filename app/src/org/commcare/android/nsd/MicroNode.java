package org.commcare.android.nsd;

import android.support.v4.util.Pair;

import org.commcare.network.HttpRequestGenerator;
import org.commcare.utils.AndroidStreamUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

/**
 * API wrapper class for services provided by CommCare micronodes.
 *
 * Micronodes are HTTP services which provide access to CommCare user and applicaiton level data
 * across local networks.
 *
 * Created by ctsims on 2/19/2016.
 */
public class MicroNode {
    private final String serviceUrlRoot;

    private ArrayList<Pair<String, String>> availableApplications;

    public MicroNode(String uri) {
        serviceUrlRoot = uri;
    }

    public ArrayList<Pair<String, String>> getAvailableApplications() {
        if (availableApplications == null) {
            availableApplications = new ArrayList<>();

            try {
                InputStream is = new BufferedInputStream(
                        new HttpRequestGenerator().simpleGet(new URL(serviceUrlRoot + "/apps/manifest")));
                byte[] manifest = AndroidStreamUtil.inputStreamToByteArray(is);

                JSONObject object = new JSONObject(new String(manifest));

                JSONArray array = object.getJSONArray("applications");

                for (int i = 0; i < array.length(); ++i) {
                    JSONObject app = array.getJSONObject(i);
                    Pair<String, String> appRecord = new Pair<>(app.getString("name"), app.getString("profile_url"));
                    availableApplications.add(appRecord);
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        return availableApplications;
    }
}
