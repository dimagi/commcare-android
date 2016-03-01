package org.commcare.android.tests;

import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import org.commcare.android.CommCareTestRunner;
import org.commcare.dalvik.BuildConfig;
import org.commcare.utils.MediaUtil;
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
    private DisplayMetrics highDensityDevice;

    private static final int[] boundingDimens_RESTRICTIVE = {50, 50};
    private static final int[] boundingDimens_LESS_RESTRICTIVE = {80, 80};
    private static final int[] boundingDimens_UNRESTRICTIVE = {200, 200};
    private static final int[] boundingDimens_LESS_UNRESTRICTIVE = {150, 150};

    private DisplayMetrics createFakeDisplayMetrics(int screenDensity) {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.densityDpi = screenDensity;
        metrics.density = (float)screenDensity / DisplayMetrics.DENSITY_DEFAULT;
        return metrics;
    }

    private void testCorrectInflationWithoutDensity(int[] boundingDimens, int expectedNewDimen) {
        Bitmap b = MediaUtil.getBitmapScaledToContainer(imageFilepath, boundingDimens[0],
                boundingDimens[1]);
        Assert.assertNotNull(b);
        Assert.assertEquals(expectedNewDimen, b.getWidth());
        Assert.assertEquals(expectedNewDimen, b.getHeight());
    }

    private void testCorrectInflationWithDensity(int targetDensity, DisplayMetrics mockDevice,
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
        highDensityDevice = createFakeDisplayMetrics(DisplayMetrics.DENSITY_HIGH);

    }

    @Test
    public void testScaleFactorComputationSimple() {
        Assert.assertEquals((double)160 / 280,
                MediaUtil.computeInflationScaleFactor(mediumDensityDevice, DisplayMetrics.DENSITY_280), .1);
    }

    // When the device's native density is something other than the standard value of
    // (device_density / default_density), our custom scale factor should be adjusted by the
    // same proportions
    @Test
    public void testScaleFactorComputationComplex1() {
        // the expected value of density for a 160 dpi device is 160/160 = 1, so this is 10% larger
        mediumDensityDevice.density = (float)1.1;
        Assert.assertEquals((double)160 / 280 * 1.1,
                MediaUtil.computeInflationScaleFactor(mediumDensityDevice, DisplayMetrics.DENSITY_280), .1);
    }

    @Test
    public void testScaleFactorComputationComplex2() {
        // the expected value of density for 120dpi device is 120/160 = .75, so this is 33% smaller
        lowDensityDevice.density = (float)0.5;
        Assert.assertEquals((double)120 / 280 * ((double)2 / 3),
                MediaUtil.computeInflationScaleFactor(lowDensityDevice, DisplayMetrics.DENSITY_280), .1);
    }

    @Test
    public void testInflationWithoutDensity_noChange() {
        testCorrectInflationWithoutDensity(boundingDimens_UNRESTRICTIVE, 100);
    }

    @Test
    public void testInflationWithoutDensity_shouldChange() {
        testCorrectInflationWithoutDensity(boundingDimens_RESTRICTIVE, 50);
    }

    @Test
    public void testDoNoScaling() {
        // Low density device with low density target should result in no size change
        int targetDensity = DisplayMetrics.DENSITY_LOW;
        testCorrectInflationWithDensity(targetDensity, lowDensityDevice, boundingDimens_UNRESTRICTIVE, 100);
    }

    @Test
    public void testScaleUpDueToDensity() {
        // Medium density device with low density target should result in image being scaled up
        // by a factor of 160/120 = 1.33
        int targetDensity = DisplayMetrics.DENSITY_LOW;
        testCorrectInflationWithDensity(targetDensity, mediumDensityDevice, boundingDimens_UNRESTRICTIVE, 133);
    }

    @Test
    public void testScaleDownDueToDensity() {
        // Low density device with medium density target should result in image being scaled down
        // by a factor of 120/160 = .75
        int targetDensity = DisplayMetrics.DENSITY_MEDIUM;
        testCorrectInflationWithDensity(targetDensity, lowDensityDevice, boundingDimens_UNRESTRICTIVE, 75);
    }

    @Test
    public void testScaleUpLimitedByContainer() {
        // High density device with low density target should result in image being scaled up by a
        // factor of 240 / 120 = 2, which would mean a final size of 200. However, the actual
        // scale-up should be bounded to 150 by the container
        int targetDensity = DisplayMetrics.DENSITY_LOW;
        testCorrectInflationWithDensity(targetDensity, highDensityDevice, boundingDimens_LESS_UNRESTRICTIVE, 150);
    }

    @Test
    public void testScaleDownDueToContainer_noDensityEffect() {
        // Low density device with low density target means no change based on density, but
        // restrictive container causes scale down
        int targetDensity = DisplayMetrics.DENSITY_MEDIUM;
        testCorrectInflationWithDensity(targetDensity, mediumDensityDevice, boundingDimens_RESTRICTIVE, 50);
    }

    @Test
    public void testScaleDownDueToContainer_densityWouldIncrease() {
        // Medium density device with low density target would cause an upscale based on density,
        // but the scale down imposed by a restrictive container should be what actual takes effect
        int targetDensity = DisplayMetrics.DENSITY_LOW;
        testCorrectInflationWithDensity(targetDensity, mediumDensityDevice, boundingDimens_RESTRICTIVE, 50);
    }

    @Test
    public void testScaleDownDueToBoth_densityDominant() {
        // Both the container size and the relative densities impose scale down requirements,
        // but the density factor imposes a larger scale down, so we scale to those dimens
        int targetDensity = DisplayMetrics.DENSITY_MEDIUM;
        testCorrectInflationWithDensity(targetDensity, lowDensityDevice, boundingDimens_LESS_RESTRICTIVE, 75);
    }

    @Test
    public void testScaleDownDueToBoth_containerDominant() {
        // Both the container size and the relative densities impose scale down requirements,
        // but the container restrictions impose a larger scale down, so we scale to those dimens
        int targetDensity = DisplayMetrics.DENSITY_MEDIUM;
        testCorrectInflationWithDensity(targetDensity, lowDensityDevice, boundingDimens_RESTRICTIVE, 50);
    }

}