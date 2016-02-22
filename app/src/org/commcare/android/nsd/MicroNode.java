package org.commcare.android.nsd;

import org.commcare.android.net.HttpRequestGenerator;
import org.javarosa.core.io.StreamsUtil;
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
 * Created by ctsims on 2/19/2016.
 */
public class MicroNode {
    private final String mRoot;

    private ArrayList<String[]> availableApplications;

    public MicroNode(String uri) {
        mRoot = uri;
    }

    public ArrayList<String[]> getAvailableApplications() {
        if (availableApplications == null) {
            availableApplications = new ArrayList<>();

            try {
                InputStream is = new BufferedInputStream(
                        new HttpRequestGenerator().simpleGet(new URL(mRoot + "/apps/manifest")));
                byte[] manifest = StreamsUtil.getStreamAsBytes(is);

                JSONObject object = new JSONObject(new String(manifest));

                JSONArray array = object.getJSONArray("applications");

                for (int i = 0; i < array.length(); ++i) {
                    JSONObject app = array.getJSONObject(i);
                    String[] appRecord = new String[]{app.getString("name"), app.getString("profile_url")};
                    availableApplications.add(appRecord);
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        return availableApplications;
    }
}
