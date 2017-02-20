package org.commcare.google.services.ads;

import org.commcare.dalvik.BuildConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by amstone326 on 2/20/17.
 */

public class AdUnitIdStore {

    private static final String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111";

    private static final String CLINICAL_SCALES_AD_UNIT_ID_ENTITY_DETAIL =
            "ca-app-pub-8038725004530429/9992164391";
    private static final String CLINICAL_SCALES_AD_UNIT_ID_ENTITY_SELECT =
            "ca-app-pub-8038725004530429/9045627199";
    private static final String CLINICAL_SCALES_AD_UNIT_ID_MENU_LIST =
            "ca-app-pub-8038725004530429/5952559991";
    private static final String CLINICAL_SCALES_AD_UNIT_ID_MENU_GRID =
            "ca-app-pub-8038725004530429/9405553999";

    private static boolean mappingsCreated = false;
    private static Map<AdLocation, String> clinicalScalesAdUnitIdsMap;

    private static void initAdUnitIdMaps() {
        if (!mappingsCreated) {
            clinicalScalesAdUnitIdsMap = new HashMap<>();
            clinicalScalesAdUnitIdsMap.put(AdLocation.EntityDetail, CLINICAL_SCALES_AD_UNIT_ID_ENTITY_DETAIL);
            clinicalScalesAdUnitIdsMap.put(AdLocation.EntitySelect, CLINICAL_SCALES_AD_UNIT_ID_ENTITY_SELECT);
            clinicalScalesAdUnitIdsMap.put(AdLocation.MenuList, CLINICAL_SCALES_AD_UNIT_ID_MENU_LIST);
            clinicalScalesAdUnitIdsMap.put(AdLocation.MenuGrid, CLINICAL_SCALES_AD_UNIT_ID_MENU_GRID);

            mappingsCreated = true;
        }
    }

    public static String getBannerAdUnitIdForCurrentConsumerApp(String pkgIdentifier,
                                                                AdLocation adLocation) {
        initAdUnitIdMaps();
        if (BuildConfig.DEBUG) {
            return TEST_BANNER_AD_UNIT_ID;
        } else {
            switch(pkgIdentifier) {
                case AdMobManager.CLINICAL_SCALES_PACKAGE_ID:
                    return clinicalScalesAdUnitIdsMap.get(adLocation);
                default:
                    return "";
            }
        }
    }

}
