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

import org.broadleafcommerce.openadmin.server.service.artifact.image.Operation;
import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.UnmarshalledParameter;
import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.conversion.ParameterTypeEnum;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;

public class Resize extends BaseFilter {

    public static final int MAX_TARGET_DIMENSION = 4096;

    private RenderingHints hints;
    private int targetWidth;
    private int targetHeight;
    private boolean highQuality;
    private Object hint;
    private boolean maintainAspectRatio;
    private boolean remainUnderBoundsWhileKeepingAspectRatio;
    private boolean reduceOnly;

    public Resize() {
        //do nothing
    }

    public Resize(
            int targetWidth,
            int targetHeight,
            boolean highQuality,
            boolean maintainAspectRatio,
            boolean reduceOnly,
            boolean remainUnderBoundsWhileKeepingAspectRatio,
            RenderingHints hints
    ) {
        this.hints = hints;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.highQuality = highQuality;
        this.hint = RenderingHints.VALUE_INTERPOLATION_BICUBIC;
        this.maintainAspectRatio = maintainAspectRatio;
        this.reduceOnly = reduceOnly;
        this.remainUnderBoundsWhileKeepingAspectRatio = remainUnderBoundsWhileKeepingAspectRatio;
    }

    @Override
    public Operation buildOperation(Map<String, String> parameterMap, InputStream artifactStream, String mimeType) {
        String key = FilterTypeEnum.RESIZE.toString().toLowerCase();

        if (!containsMyFilterParams(key, parameterMap)) {
            return null;
        }

        Operation operation = new Operation();
        operation.setName(key);
        String factor = parameterMap.get(key + "-factor");
        operation.setFactor(factor == null ? null : Double.valueOf(factor));

        UnmarshalledParameter targetWidth = new UnmarshalledParameter();
        String targetWidthApplyFactor = parameterMap.get(key + "-width-apply-factor");
        targetWidth.setApplyFactor(targetWidthApplyFactor == null ? false : Boolean.valueOf(targetWidthApplyFactor));
        targetWidth.setName("target-width");
        targetWidth.setType(ParameterTypeEnum.INT.toString());
        targetWidth.setValue(parameterMap.get(key + "-width-amount"));

        UnmarshalledParameter targetHeight = new UnmarshalledParameter();
        String targetHeightApplyFactor = parameterMap.get(key + "-height-apply-factor");
        targetHeight.setApplyFactor(targetHeightApplyFactor == null ? false : Boolean.valueOf(targetHeightApplyFactor));
        targetHeight.setName("target-height");
        targetHeight.setType(ParameterTypeEnum.INT.toString());
        targetHeight.setValue(parameterMap.get(key + "-height-amount"));

        UnmarshalledParameter highQuality = new UnmarshalledParameter();
        highQuality.setName("high-quality");
        highQuality.setType(ParameterTypeEnum.BOOLEAN.toString());
        highQuality.setValue(
                parameterMap.get(key + "-high-quality") == null ? "false" : parameterMap.get(key + "-high-quality"
                ));

        UnmarshalledParameter maintainAspectRatio = new UnmarshalledParameter();
        maintainAspectRatio.setName("maintain-aspect-ratio");
        maintainAspectRatio.setType(ParameterTypeEnum.BOOLEAN.toString());
        maintainAspectRatio.setValue(
                parameterMap.get(key + "-maintain-aspect-ratio") == null
                        ? "false"
                        : parameterMap.get(key + "-maintain-aspect-ratio")
        );

        UnmarshalledParameter reduceOnly = new UnmarshalledParameter();
        reduceOnly.setName("reduce-only");
        reduceOnly.setType(ParameterTypeEnum.BOOLEAN.toString());
        reduceOnly.setValue(
                parameterMap.get(key + "-reduce-only") == null ? "false" : parameterMap.get(key + "-reduce-only")
        );

        UnmarshalledParameter remainUnderBoundsWhileKeepingAspectRatio = new UnmarshalledParameter();
        remainUnderBoundsWhileKeepingAspectRatio.setName("remain-under-bounds-while-keeping-aspect-ratio");
        remainUnderBoundsWhileKeepingAspectRatio.setType(ParameterTypeEnum.BOOLEAN.toString());
        remainUnderBoundsWhileKeepingAspectRatio.setValue(
                parameterMap.get(key + "-remain-under-bounds-while-keeping-aspect-ratio") == null
                        ? "false"
                        : parameterMap.get(key + "-remain-under-bounds-while-keeping-aspect-ratio")
        );

        operation.setParameters(new UnmarshalledParameter[]{
                targetWidth,
                targetHeight,
                highQuality,
                maintainAspectRatio,
                reduceOnly,
                remainUnderBoundsWhileKeepingAspectRatio
        });
        return operation;
    }

    /* (non-Javadoc)
     * @see java.awt.image.BufferedImageOp#filter(java.awt.image.BufferedImage, java.awt.image.BufferedImage)
     */
    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        if (src == null) {
            throw new NullPointerException("src image is null");
        }
        if (src == dst) {
            throw new IllegalArgumentException("src image cannot be the " +
                    "same as the dst image");
        }

        if (src.getWidth() <= targetWidth && src.getHeight() <= targetHeight && reduceOnly) {
            return src;
        }

        if (targetWidth < 1 || targetHeight < 1) {
            return src;
        }

        int maxDimension = getMaxTargetDimension();
        BufferedImage temp = getScaledInstance(
                src, targetWidth, targetHeight, hint, highQuality, maintainAspectRatio, reduceOnly, maxDimension
        );

        if (dst != null) {
            Graphics g = dst.createGraphics();
            g.drawImage(temp, 0, 0, temp.getWidth(), temp.getHeight(), null);
            g.dispose();
        } else {
            dst = temp;
        }

        return dst;
    }

    private BufferedImage getScaledInstance(
            BufferedImage img,
            int targetWidth,
            int targetHeight,
            Object hint,
            boolean higherQuality,
            boolean maintainAspectRatio,
            boolean reduceOnly,
            int maxDimension
    ) {
        BufferedImage ret = (BufferedImage) img;
        int w, h, destW, destH;

        double scaledW;
        double scaledH;
        if (maintainAspectRatio) {
            int wDiff = Math.abs(img.getWidth() - targetWidth);
            int hDiff = Math.abs(img.getHeight() - targetHeight);
            if (wDiff > hDiff) {
                scaledH = targetHeight;
                scaledW = Math.floor((((double) img.getWidth()) * scaledH) / ((double) img.getHeight()));
                if (remainUnderBoundsWhileKeepingAspectRatio && scaledW > targetWidth) {
                    scaledH = Math.floor((scaledH * targetWidth) / scaledW);
                    scaledW = targetWidth;
                }
            } else {
                scaledW = targetWidth;
                scaledH = Math.floor((((double) img.getHeight()) * scaledW) / ((double) img.getWidth()));
                if (remainUnderBoundsWhileKeepingAspectRatio && scaledH > targetHeight) {
                    scaledW = Math.floor((scaledW * targetHeight) / scaledH);
                    scaledH = targetHeight;
                }
            }
            if (scaledW > maxDimension || scaledH > maxDimension) {
                double scale = Math.min(maxDimension / scaledW, maxDimension / scaledH);
                scaledW = Math.floor(scaledW * scale);
                scaledH = Math.floor(scaledH * scale);
            }
        } else {
            scaledW = Math.min(targetWidth, maxDimension);
            scaledH = Math.min(targetHeight, maxDimension);
        }
        destW = (int) scaledW;
        destH = (int) scaledH;
        destW = Math.max(1, destW);
        destH = Math.max(1, destH);

        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = img.getWidth();
            h = img.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = destW;
            h = destH;
        }

        do {
            if (higherQuality && w > destW) {
                w /= 2;
                if (w < destW) {
                    w = destW;
                }
            } else if (higherQuality && w < destW) {
                w *= 2;
                if (w > destW) {
                    w = destW;
                }
            }

            if (higherQuality && h > destH) {
                h /= 2;
                if (h < destH) {
                    h = destH;
                }
            } else if (higherQuality && h < destH) {
                h *= 2;
                if (h > destH) {
                    h = destH;
                }
            }
            int type;
            if (img.getType() != BufferedImage.TYPE_INT_ARGB && img.getType() != BufferedImage.TYPE_INT_RGB
                    && !"bmp".equalsIgnoreCase(imageFormat)) {
                type = BufferedImage.TYPE_INT_ARGB;
            } else {
                type = img.getType();
            }

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.drawImage(ret.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
        } while (w != destW || h != destH);

        return ret;
    }

    protected int getMaxTargetDimension() {
        return MAX_TARGET_DIMENSION;
    }

}
