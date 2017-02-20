package org.commcare.google.services.ads;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Controls all actions related to showing ads in consumer apps via AdMob
 *
 * @author Aliza Stone
 */
public class AdMobManager {

    private static final Map<String, String> adMobIdsMap = initAdMobIdsMap();

    public static final String CLINICAL_SCALES_PACKAGE_ID =
            "org.commcare.consumerapps.clinicalscales";
    private static final String CLINICAL_SCALES_ADMOB_ID =
            "ca-app-pub-8038725004530429~5040587593";

    public static Map<String, String> initAdMobIdsMap() {
        Map<String, String> map = new HashMap<>();
        map.put(CLINICAL_SCALES_PACKAGE_ID, CLINICAL_SCALES_ADMOB_ID);
        return map;
    }

    public static void requestBannerAdForView(Context context, FrameLayout adContainer,
                                              AdLocation adLocation) {
        String currentConsumerAppPackageId = getPackageIdentifierForCurrentConsumerApp(context);
        if (hasAdmobId(currentConsumerAppPackageId)) {
            AdView adView = buildBannerAdView(context, currentConsumerAppPackageId, adLocation);
            adContainer.setVisibility(View.VISIBLE);
            adContainer.addView(adView);

            AdRequest adRequest = buildAdRequest();
            adView.loadAd(adRequest);
        } else {
            adContainer.setVisibility(View.GONE);
        }
    }

    private static AdView buildBannerAdView(Context context, String currentConsumerAppPackageId,
                                            AdLocation adLocation) {
        AdView adView = new AdView(context);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(AdUnitIdStore.getBannerAdUnitIdForCurrentConsumerApp(
                currentConsumerAppPackageId, adLocation));
        return adView;
    }

    private static AdRequest buildAdRequest() {
        if (BuildConfig.DEBUG) {
            return buildTestAdRequest();
        } else {
            return new AdRequest.Builder().build();
        }
    }

    private static AdRequest buildTestAdRequest() {
        return new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .addTestDevice(CommCareApplication.instance().getPhoneId())
                .build();
    }

    public static void initAdsForCurrentConsumerApp(Context context) {
        String currentConsumerAppPackageId = getPackageIdentifierForCurrentConsumerApp(context);
        if (hasAdmobId(currentConsumerAppPackageId)) {
            String adMobId = adMobIdsMap.get(currentConsumerAppPackageId);
            if (adMobId != null) {
                MobileAds.initialize(context, adMobId);
            }
        }
    }

    private static String getPackageIdentifierForCurrentConsumerApp(Context context) {
        if (CommCareApplication.instance().isConsumerApp()) {
            return context.getPackageName();
        }
        return "";
    }

    private static boolean hasAdmobId(String packageIdentifier) {
        return adMobIdsMap.containsKey(packageIdentifier);
    }

}
