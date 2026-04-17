/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.config.javaconfig;

import org.broadleafcommerce.common.web.resource.BroadleafResourceHttpRequestHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceTransformer;

import java.util.List;

/**
 * Java configuration equivalent of blc-config/site/framework/bl-common-applicationContext.xml.
 * Imports the main common configuration, adds site-specific component scanning,
 * and defines resource request handlers for JS, CSS, images, and fonts.
 *
 * <p>The original XML configuration is kept in place for backward compatibility
 * until all modules are migrated to Java configuration.</p>
 */
@Configuration
@Import(CommonConfiguration.class)
@ComponentScan(basePackages = "org.broadleafcommerce.site.common.web")
public class CommonSiteConfiguration {

    @Value("${staticResourceBrowserCacheSeconds:0}")
    private int staticResourceBrowserCacheSeconds;

    @Bean(name = "blJsResources")
    public BroadleafResourceHttpRequestHandler blJsResources(
            @Qualifier("blSiteResourceResolvers") List<ResourceResolver> resourceResolvers,
            @Qualifier("blJsLocations") List<Resource> jsLocations,
            @Qualifier("blJsResourceTransformers") List<ResourceTransformer> jsResourceTransformers) {
        BroadleafResourceHttpRequestHandler handler = new BroadleafResourceHttpRequestHandler();
        handler.setCacheSeconds(staticResourceBrowserCacheSeconds);
        handler.setResourceResolvers(resourceResolvers);
        handler.setLocations(jsLocations);
        handler.setResourceTransformers(jsResourceTransformers);
        return handler;
    }

    @Bean(name = "blCssResources")
    public BroadleafResourceHttpRequestHandler blCssResources(
            @Qualifier("blSiteResourceResolvers") List<ResourceResolver> resourceResolvers,
            @Qualifier("blCssLocations") List<Resource> cssLocations,
            @Qualifier("blCssResourceTransformers") List<ResourceTransformer> cssResourceTransformers) {
        BroadleafResourceHttpRequestHandler handler = new BroadleafResourceHttpRequestHandler();
        handler.setCacheSeconds(staticResourceBrowserCacheSeconds);
        handler.setResourceResolvers(resourceResolvers);
        handler.setLocations(cssLocations);
        handler.setResourceTransformers(cssResourceTransformers);
        return handler;
    }

    @Bean(name = "blImageResources")
    public BroadleafResourceHttpRequestHandler blImageResources(
            @Qualifier("blSiteResourceResolvers") List<ResourceResolver> resourceResolvers,
            @Qualifier("blImageLocations") List<Resource> imageLocations) {
        BroadleafResourceHttpRequestHandler handler = new BroadleafResourceHttpRequestHandler();
        handler.setCacheSeconds(staticResourceBrowserCacheSeconds);
        handler.setResourceResolvers(resourceResolvers);
        handler.setLocations(imageLocations);
        return handler;
    }

    @Bean(name = "blFontResources")
    public BroadleafResourceHttpRequestHandler blFontResources(
            @Qualifier("blSiteResourceResolvers") List<ResourceResolver> resourceResolvers,
            @Qualifier("blFontLocations") List<Resource> fontLocations) {
        BroadleafResourceHttpRequestHandler handler = new BroadleafResourceHttpRequestHandler();
        handler.setCacheSeconds(staticResourceBrowserCacheSeconds);
        handler.setResourceResolvers(resourceResolvers);
        handler.setLocations(fontLocations);
        return handler;
    }
}
