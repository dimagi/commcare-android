package org.commcare.android.nsd;

import org.commcare.network.CommcareRequestGenerator;
import org.javarosa.core.io.StreamsUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private ArrayList<AppManifest> availableApplications;

    public MicroNode(String uri) {
        serviceUrlRoot = uri;
    }

    public ArrayList<AppManifest> getAvailableApplications() {
        if (availableApplications == null) {
            availableApplications = new ArrayList<>();

            try {
                InputStream is = new BufferedInputStream(
                        CommcareRequestGenerator.buildNoAuthGenerator()
                                .simpleGet(serviceUrlRoot + "/apps/manifest")
                                .body().byteStream());
                byte[] manifest = StreamsUtil.inputStreamToByteArray(is);

                JSONObject object = new JSONObject(new String(manifest));

                JSONArray array = object.getJSONArray("applications");

                for (int i = 0; i < array.length(); ++i) {
                    JSONObject app = array.getJSONObject(i);
                    AppManifest appRecord = AppManifest.fromJSON(app);
                    availableApplications.add(appRecord);
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        return availableApplications;
    }

    /**
     * Given a unique app ID, return a local hub manifest for a matching record, if available.
     * If no availalbe apps match the id, null will be returned.
     */
    public AppManifest getManifestForAppId(String appId) {
        ArrayList<AppManifest> apps = getAvailableApplications();
        for (AppManifest app : apps) {
            if (appId.equals(app.getId())) {
                return app;
            }
        }
        return null;
    }

    public static class AppManifest {
        final private String name;
        final private String localUrl;
        final private String id;


        public AppManifest(String name, String localUrl, String id) {
            this.name = name;
            this.localUrl = localUrl;
            this.id = id;
        }

        public static AppManifest fromJSON(JSONObject app) throws JSONException {
            String appId = app.has("app_guid") ? app.getString("app_guid") :
                    getAppIdFromCCZHack(app.getString("download_url"));

            return new AppManifest(app.getString("name"), app.getString("profile_url"),
                    appId);
        }

        public String getName() {
            return name;
        }

        public String getLocalUrl() {
            return localUrl;
        }

        public String getId() {
            return id;
        }
    }

    // Replace as soon as the manifest format contains this info
    private static String getAppIdFromCCZHack(String cczUrl) {
        if (cczUrl.contains("app_id=")) {
            String[] results = cczUrl.split("app_id=");
            return results.length < 2 ? null : results[1];
        } else {
            return null;
        }
    }
}
