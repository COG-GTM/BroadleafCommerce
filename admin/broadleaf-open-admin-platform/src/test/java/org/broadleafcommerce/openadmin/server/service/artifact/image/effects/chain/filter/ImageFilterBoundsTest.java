/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
 * %%
 * Copyright (C) 2009 - 2026 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.filter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class ImageFilterBoundsTest {

    private static BufferedImage image(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, ((x * 7) & 0xFF) << 16 | ((y * 13) & 0xFF) << 8 | ((x + y) & 0xFF));
            }
        }
        return img;
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    @Test(timeout = 10000)
    public void gaussianBlurPassCountIsCapped() {
        BufferedImage src = image(64, 48);

        BufferedImage huge = new GaussianBlur(16, Integer.MAX_VALUE, null).filter(src, null);
        BufferedImage max = new GaussianBlur(16, GaussianBlur.MAX_NUM_PASSES, null).filter(src, null);

        assertEquals(64, huge.getWidth());
        assertEquals(48, huge.getHeight());
        assertArrayEquals(pixels(max), pixels(huge));
    }

    @Test
    public void gaussianBlurPassCountWithinLimitIsUnchanged() {
        BufferedImage src = image(32, 32);

        BufferedImage two = new GaussianBlur(4, 2, null).filter(src, null);
        BufferedImage three = new GaussianBlur(4, 3, null).filter(src, null);

        assertTrue(!java.util.Arrays.equals(pixels(two), pixels(three)));
    }

    @Test(timeout = 10000)
    public void cropIsClampedToSourceBounds() {
        BufferedImage src = image(50, 40);

        BufferedImage cropped = new Crop(new Rectangle(0, 0, 40000, 40000), null).filter(src, null);

        assertEquals(50, cropped.getWidth());
        assertEquals(40, cropped.getHeight());
    }

    @Test
    public void cropIntersectsPartiallyOutOfBoundsRegion() {
        BufferedImage src = image(50, 40);

        BufferedImage cropped = new Crop(new Rectangle(30, 10, 1000, 1000), null).filter(src, null);

        assertEquals(20, cropped.getWidth());
        assertEquals(30, cropped.getHeight());
        assertEquals(src.getRGB(30, 10), cropped.getRGB(0, 0));
    }

    @Test
    public void cropWithinBoundsIsUnchanged() {
        BufferedImage src = image(50, 40);

        BufferedImage cropped = new Crop(new Rectangle(5, 5, 10, 20), null).filter(src, null);

        assertEquals(10, cropped.getWidth());
        assertEquals(20, cropped.getHeight());
        assertEquals(src.getRGB(5, 5), cropped.getRGB(0, 0));
    }

    @Test
    public void cropOutsideSourceReturnsSource() {
        BufferedImage src = image(50, 40);

        assertSame(src, new Crop(new Rectangle(100, 100, 10, 10), null).filter(src, null));
        assertSame(src, new Crop(new Rectangle(0, 0, -5, 10), null).filter(src, null));
    }

    @Test(timeout = 10000)
    public void resizeTargetIsCappedAtMaxDimension() {
        BufferedImage src = image(20, 10);

        BufferedImage resized = new SmallResize(40000, 40000, false, false).filter(src, null);

        assertEquals(64, resized.getWidth());
        assertEquals(64, resized.getHeight());
    }

    @Test(timeout = 10000)
    public void resizeKeepsAspectRatioWhenCapped() {
        BufferedImage src = image(200, 10);

        BufferedImage resized = new SmallResize(5000, 250, false, true).filter(src, null);
        BufferedImage hq = new SmallResize(5000, 250, true, true).filter(src, null);

        assertEquals(64, resized.getWidth());
        assertEquals(3, resized.getHeight());
        assertEquals(64, hq.getWidth());
        assertEquals(3, hq.getHeight());
    }

    @Test
    public void resizeWithinLimitIsUnchanged() {
        BufferedImage src = image(20, 10);

        BufferedImage resized = new SmallResize(40, 30, false, false).filter(src, null);

        assertEquals(40, resized.getWidth());
        assertEquals(30, resized.getHeight());
    }

    @Test(timeout = 10000)
    public void resizeReduceOnlyNeverEnlarges() {
        BufferedImage src = image(30, 20);

        BufferedImage resized = new Resize(40000, 10, false, false, true, false, null).filter(src, null);

        assertEquals(30, resized.getWidth());
        assertEquals(10, resized.getHeight());
    }

    @Test
    public void resizeNonPositiveTargetReturnsSource() {
        BufferedImage src = image(30, 20);

        assertSame(src, new Resize(0, 10, false, false, false, false, null).filter(src, null));
        assertSame(src, new Resize(10, -1, false, false, false, false, null).filter(src, null));
    }

    @Test
    public void defaultResizeLimitIsBounded() {
        assertEquals(4096, new Resize().getMaxTargetDimension());
        assertEquals(10, new GaussianBlur().getMaxNumOfPasses());
    }

    private static class SmallResize extends Resize {

        SmallResize(int width, int height, boolean highQuality, boolean maintainAspectRatio) {
            super(width, height, highQuality, maintainAspectRatio, false, false, null);
        }

        @Override
        protected int getMaxTargetDimension() {
            return 64;
        }
    }
}
