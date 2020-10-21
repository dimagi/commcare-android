package org.commcare.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.MediaUtil;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class CustomBanner {

    public static boolean useCustomBannerFitToActivity(AppCompatActivity activity,
                                                       @NonNull ImageView topBannerImageView,
                                                       Banner banner) {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int screenHeight = displaymetrics.heightPixels;
        int screenWidth = displaymetrics.widthPixels;

        return useCustomBanner(activity, screenHeight, screenWidth, topBannerImageView, banner);
    }

    public static boolean useCustomBanner(Context context,
                                          int screenHeight, int screenWidth,
                                          @NonNull ImageView topBannerImageView,
                                          Banner banner) {
        String customBannerURI = banner.getBannerURI();

        if (!"".equals(customBannerURI)) {
            int maxBannerHeight = screenHeight / 4;

            Bitmap bitmap =
                    MediaUtil.inflateDisplayImage(context, customBannerURI,
                            screenWidth, maxBannerHeight);
            if (bitmap != null) {
                topBannerImageView.setMaxHeight(maxBannerHeight);
                topBannerImageView.setImageBitmap(bitmap);
                return true;
            }
        }
        return false;
    }

    public enum Banner {
        LOGIN, HOME;

        /**
         * @return Banner URI for home and login screen.
         * Returns empty string if the uri is not specified.
         */
        public String getBannerURI() {
            CommCareApp app = CommCareApplication.instance().getCurrentApp();
            if (app == null) {
                return "";
            }
            switch (this) {
                case LOGIN:
                    return app.getAppPreferences().getString(HiddenPreferences.BRAND_BANNER_LOGIN, "");
                case HOME:
                    String bannerURI = app.getAppPreferences().getString(HiddenPreferences.BRAND_BANNER_HOME, "");
                    if (CommCareApplication.instance().isInDemoMode(false)) {
                        bannerURI = app.getAppPreferences().getString(HiddenPreferences.BRAND_BANNER_HOME_DEMO, bannerURI);
                    }
                    return bannerURI;
            }
            return "";
        }
    }
}
