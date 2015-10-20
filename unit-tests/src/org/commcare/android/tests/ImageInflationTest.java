package org.commcare.android.tests;

import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.BuildConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class ImageInflationTest {

    private String imageFilepath;
    private DisplayMetrics lowDensityDevice;
    private DisplayMetrics mediumDensityDevice;
    
    private static int[] boundingDimens_RESTRICTIVE = {50, 50};
    private static int[] boundingDimens_UNRESTRICTIVE = {200, 200};

    private DisplayMetrics createFakeDisplayMetrics(int screenDensity) {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.densityDpi = screenDensity;
        metrics.density = (float)screenDensity / DisplayMetrics.DENSITY_DEFAULT;
        return metrics;
    }
    private void testCorrectInflation(int targetDensity, DisplayMetrics mockDevice,
                                      int[] boundingDimens, int expectedNewDimen) {
        Bitmap b = MediaUtil.getBitmapScaledForNativeDensity(mockDevice, imageFilepath,
               boundingDimens[0], boundingDimens[1], targetDensity);
        Assert.assertNotNull(b);
        Assert.assertEquals(expectedNewDimen, b.getWidth());
        Assert.assertEquals(expectedNewDimen, b.getHeight());
    }

    @Before
    public void init() {
        imageFilepath = "/images/100x100.png";
        lowDensityDevice = createFakeDisplayMetrics(DisplayMetrics.DENSITY_LOW);
        mediumDensityDevice = createFakeDisplayMetrics(DisplayMetrics.DENSITY_MEDIUM);
    }

    @Test
    public void testDoNoScaling() {
        // Low density device with low density target should result in no size change
        int targetDensity = DisplayMetrics.DENSITY_LOW;
        testCorrectInflation(targetDensity, lowDensityDevice, boundingDimens_UNRESTRICTIVE, 100);
    }

    @Test
    public void testScaleUpDueToDensity() {
        // Medium density device with low density target should result in image being scaled up
        // by a factor of 160/120 = 1.33
        int targetDensity = DisplayMetrics.DENSITY_LOW;
        testCorrectInflation(targetDensity, mediumDensityDevice, boundingDimens_UNRESTRICTIVE, 133);
    }

    @Test
    public void testScaleDownDueToDensity() {
        // Low density device with medium density target should result in image being scaled down
        // by a factor of 120/160 = .75
        int targetDensity = DisplayMetrics.DENSITY_MEDIUM;
        testCorrectInflation(targetDensity, lowDensityDevice, boundingDimens_UNRESTRICTIVE, 75);
    }

}