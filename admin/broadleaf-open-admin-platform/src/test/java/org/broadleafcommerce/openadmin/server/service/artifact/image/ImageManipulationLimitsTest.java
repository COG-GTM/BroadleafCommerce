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
package org.broadleafcommerce.openadmin.server.service.artifact.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.filter.GaussianBlur;
import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.filter.Resize;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Random;

import javax.imageio.ImageIO;

public class ImageManipulationLimitsTest {

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private static InputStream jpeg(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image(width, height), "jpeg", out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test(timeout = 5000)
    public void gaussianBlurRejectsExcessivePassCount() {
        assertRejected(() -> new GaussianBlur(16, 2_000_000_000, null).filter(image(64, 64), null));
        assertRejected(() -> new GaussianBlur(16, GaussianBlur.MAX_NUM_PASSES + 1, null).filter(image(64, 64), null));
    }

    @Test
    public void gaussianBlurAllowsPassCountUpToLimit() {
        BufferedImage result = new GaussianBlur(5, GaussianBlur.MAX_NUM_PASSES, null).filter(image(64, 64), null);
        assertEquals(64, result.getWidth());
        assertEquals(64, result.getHeight());
    }

    @Test(timeout = 5000)
    public void resizeRejectsOversizedTarget() {
        assertRejected(() -> new Resize(30000, 30000, false, false, false, false, null).filter(image(10, 10), null));
        assertRejected(() -> new Resize(30000, 30000, true, false, false, false, null).filter(image(10, 10), null));
        assertRejected(() -> new Resize(Resize.MAX_TARGET_DIMENSION + 1, 10, false, false, false, false, null)
                .filter(image(10, 10), null));
        assertRejected(() -> new Resize(6000, 6000, false, false, false, false, null).filter(image(10, 10), null));
    }

    @Test(timeout = 5000)
    public void resizeRejectsOversizedTargetDerivedFromAspectRatio() {
        // height diff is smaller, so the width is derived from the 100:1 aspect ratio: 1000x10 -> 100000x1000
        assertRejected(() -> new Resize(1_000_000, 1000, false, true, false, false, null).filter(image(1000, 10), null));
    }

    @Test
    public void resizeAllowsTargetWithinLimits() {
        BufferedImage result = new Resize(40, 30, false, false, false, false, null).filter(image(400, 300), null);
        assertEquals(40, result.getWidth());
        assertEquals(30, result.getHeight());

        result = new Resize(800, 600, true, false, false, false, null).filter(image(400, 300), null);
        assertEquals(800, result.getWidth());
        assertEquals(600, result.getHeight());
    }

    @Test
    public void convertRejectsOversizedSourceBeforeDecoding() throws Exception {
        ImageArtifactProcessor processor = new ImageArtifactProcessor();
        processor.setMaxSourcePixels(10_000L);
        try {
            processor.convert(jpeg(200, 200), new Resize(10, 10, false, false, false, false, null));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("200x200"));
        }

        processor.setMaxSourcePixels(Long.MAX_VALUE);
        processor.setMaxSourceDimension(100);
        try {
            processor.convert(jpeg(150, 20), new Resize(10, 10, false, false, false, false, null));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void convertProcessesSourceWithinLimits() throws Exception {
        ImageArtifactProcessor processor = new ImageArtifactProcessor();
        InputStream result = processor.convert(jpeg(200, 200), new Resize(20, 20, false, false, false, false, null));
        assertNotNull(result);
        BufferedImage converted = ImageIO.read(result);
        assertEquals(20, converted.getWidth());
        assertEquals(20, converted.getHeight());
    }

    @Test
    public void convertDecodesFileStreamsAfterHeaderCheck() throws Exception {
        BufferedImage noisy = image(600, 400);
        Random random = new Random(42);
        for (int x = 0; x < noisy.getWidth(); x++) {
            for (int y = 0; y < noisy.getHeight(); y++) {
                noisy.setRGB(x, y, random.nextInt());
            }
        }
        File file = File.createTempFile("image-limits", ".jpg");
        file.deleteOnExit();
        ImageIO.write(noisy, "jpeg", file);
        assertTrue("source must exceed the default BufferedInputStream buffer", file.length() > 8192);

        ImageArtifactProcessor processor = new ImageArtifactProcessor();
        // mirrors StaticAssetStorageServiceImpl, which passes a BufferedInputStream marked with a zero read limit
        try (BufferedInputStream original = new BufferedInputStream(new FileInputStream(file))) {
            original.mark(0);
            BufferedImage converted = ImageIO.read(processor.convert(original, new Resize(60, 40, false, false, false, false, null)));
            assertEquals(60, converted.getWidth());
            assertEquals(40, converted.getHeight());
        }
        try (InputStream unmarkable = new FileInputStream(file)) {
            BufferedImage converted = ImageIO.read(processor.convert(unmarkable, new Resize(30, 20, false, false, false, false, null)));
            assertEquals(30, converted.getWidth());
            assertEquals(20, converted.getHeight());
        }
    }
}
