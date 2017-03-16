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

    private static final String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111";

    public static void requestBannerAdForView(Context context, FrameLayout adContainer,
                                              AdLocation adLocation) {
        if (hasValidAdmobId()) {
            // TODO: It's probably bad not to validate this in any way, but any mechanism for doing
            // so would defeat the purpose of having made this configurable from the consumer apps resources
            AdView adView = buildBannerAdView(context, adLocation);
            adContainer.setVisibility(View.VISIBLE);
            adContainer.addView(adView);

            AdRequest adRequest = buildAdRequest();
            adView.loadAd(adRequest);
        } else {
            adContainer.setVisibility(View.GONE);
        }
    }

    private static AdView buildBannerAdView(Context context, AdLocation adLocation) {
        AdView adView = new AdView(context);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(getBannerAdUnitIdForCurrentConsumerApp(adLocation));
        return adView;
    }

    public static String getBannerAdUnitIdForCurrentConsumerApp(AdLocation adLocation) {
        if (BuildConfig.DEBUG) {
            return TEST_BANNER_AD_UNIT_ID;
        } else {
            switch(adLocation) {
                case EntityDetail:
                    return BuildConfig.ENTITY_DETAIL_AD_UNIT_ID;
                case EntitySelect:
                    return BuildConfig.ENTITY_SELECT_AD_UNIT_ID;
                case MenuGrid:
                    return BuildConfig.MENU_GRID_AD_UNIT_ID;
                case MenuList:
                    return BuildConfig.MENU_LIST_AD_UNIT_ID;
                default:
                    return "";
            }
        }
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
        if (hasValidAdmobId()) {
            MobileAds.initialize(context, BuildConfig.ADMOB_ID);
        }
    }

    private static boolean hasValidAdmobId() {
        return CommCareApplication.instance().isConsumerApp() && !"".equals(BuildConfig.ADMOB_ID);
    }
}
