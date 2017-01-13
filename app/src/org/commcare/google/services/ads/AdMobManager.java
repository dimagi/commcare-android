package org.commcare.google.services.ads;

import android.content.Context;

import com.google.android.gms.ads.MobileAds;

import org.commcare.CommCareApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by amstone326 on 1/13/17.
 */

public class AdMobManager {

    private static final Map<String, String> adMobIdsMap = initAdMobIdsMap();

    public static Map<String, String> initAdMobIdsMap() {
        Map<String, String> map = new HashMap<>();
        map.put("org.commcare.consumerapps.clinicalscales", "ca-app-pub-8038725004530429~5040587593");
        return map;
    }

    public static void initAdsForCurrentConsumerApp(Context context) {
        if (CommCareApplication.instance().isConsumerApp()) {
            String packageIdentifier = context.getPackageName();
            String adMobId = adMobIdsMap.get(packageIdentifier);
            if (adMobId != null) {
                MobileAds.initialize(context, adMobId);
            }
        }
    }
}
