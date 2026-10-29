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
package org.broadleafcommerce.openadmin.spec

import org.broadleafcommerce.openadmin.server.service.artifact.image.Operation
import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.EffectsManager
import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.ImageDimensionException
import org.broadleafcommerce.openadmin.server.service.artifact.image.effects.chain.conversion.ConversionManager
import spock.lang.Specification
import spock.lang.Unroll

import java.awt.image.BufferedImage

class ImageEffectDimensionSpec extends Specification {

    EffectsManager effectsManager = new EffectsManager(conversionManager: new ConversionManager())
    BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)

    BufferedImage render(Map<String, String> parameters) {
        BufferedImage image = source
        for (Operation operation : effectsManager.buildOperations(parameters, null, "image/png")) {
            image = effectsManager.renderEffect(operation.name, operation.factor, operation.parameters, image, "png")
        }
        return image
    }

    @Unroll
    def "resize to #width x #height is allowed"() {
        when:
        BufferedImage result = render(["resize-width-amount": width, "resize-height-amount": height])

        then:
        result.width == width.toInteger()
        result.height == height.toInteger()

        where:
        width  | height
        "1"    | "1"
        "1024" | "768"
        "4096" | "4096"
    }

    def "maintaining aspect ratio still works"() {
        expect:
        render(["resize-width-amount"         : "60", "resize-height-amount": "60",
                "resize-maintain-aspect-ratio": "true", "resize-reduce-only": "true"]).width == 80
    }

    @Unroll
    def "resize to #width x #height (high quality: #highQuality) is rejected before allocating"() {
        when:
        render(["resize-width-amount": width, "resize-height-amount": height, "resize-high-quality": highQuality])

        then:
        thrown(ImageDimensionException)

        where:
        width   | height  | highQuality
        "40000" | "40000" | "false"
        "40000" | "40000" | "true"
        "4097"  | "100"   | "false"
        "100"   | "4097"  | "false"
        "0"     | "100"   | "false"
        "100"   | "-5"    | "false"
    }

    def "a factor cannot be used to bypass the limit"() {
        when:
        render(["resize-width-amount"      : "40", "resize-height-amount": "40", "resize-factor": "0.001",
                "resize-width-apply-factor": "true", "resize-height-apply-factor": "true"])

        then:
        thrown(ImageDimensionException)
    }

    def "an aspect ratio calculation cannot be used to bypass the limit"() {
        given:
        source = new BufferedImage(1, 4096, BufferedImage.TYPE_INT_RGB)

        when:
        render(["resize-width-amount": "4096", "resize-height-amount": "1", "resize-maintain-aspect-ratio": "true"])

        then:
        thrown(ImageDimensionException)
    }

    def "the limit is configurable"() {
        given:
        effectsManager.maxImageDimension = 100

        when:
        render(["resize-width-amount": "101", "resize-height-amount": "50"])

        then:
        thrown(ImageDimensionException)

        when:
        BufferedImage result = render(["resize-width-amount": "100", "resize-height-amount": "50"])

        then:
        result.width == 100
        result.height == 50
    }

    def "crop within the limit is allowed"() {
        expect:
        BufferedImage result = render(["crop-x-amount": "10", "crop-y-amount": "10", "crop-width-amount": "100", "crop-height-amount": "50"])
        result.width == 100
        result.height == 50
    }

    def "crop larger than the limit is rejected"() {
        when:
        render(["crop-x-amount": "0", "crop-y-amount": "0", "crop-width-amount": "40000", "crop-height-amount": "40000"])

        then:
        thrown(ImageDimensionException)
    }

    def "rotate still works and rejects non-finite angles"() {
        expect:
        render(["rotate-rotate-amount": "90"]).width == 600

        when:
        render(["rotate-rotate-amount": "NaN"])

        then:
        thrown(ImageDimensionException)
    }
}
