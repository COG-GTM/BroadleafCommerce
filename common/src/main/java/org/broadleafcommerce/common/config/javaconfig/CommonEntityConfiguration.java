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

import org.broadleafcommerce.common.email.domain.EmailTargetImpl;
import org.broadleafcommerce.common.email.domain.EmailTrackingClicksImpl;
import org.broadleafcommerce.common.email.domain.EmailTrackingImpl;
import org.broadleafcommerce.common.email.domain.EmailTrackingOpensImpl;
import org.broadleafcommerce.common.enumeration.domain.DataDrivenEnumerationImpl;
import org.broadleafcommerce.common.enumeration.domain.DataDrivenEnumerationValueImpl;
import org.broadleafcommerce.common.config.domain.SystemPropertyImpl;
import org.broadleafcommerce.common.i18n.domain.ISOCountryImpl;
import org.broadleafcommerce.common.i18n.domain.TranslationImpl;
import org.broadleafcommerce.common.id.domain.IdGenerationImpl;
import org.broadleafcommerce.common.media.domain.MediaDto;
import org.broadleafcommerce.common.sandbox.domain.SandBoxManagementImpl;
import org.broadleafcommerce.common.site.domain.CatalogImpl;
import org.broadleafcommerce.common.site.domain.SiteCatalogXrefImpl;
import org.broadleafcommerce.common.site.domain.SiteImpl;
import org.broadleafcommerce.common.sitemap.domain.CustomUrlSiteMapGeneratorConfigurationImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapConfigurationImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapGeneratorConfigurationImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapUrlEntryImpl;
import org.broadleafcommerce.common.structure.dto.ItemCriteriaDTO;
import org.broadleafcommerce.common.structure.dto.StructuredContentDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Java configuration equivalent of bl-common-applicationContext-entity.xml.
 * Defines prototype-scoped entity beans used by the EntityConfiguration framework
 * to create new instances of domain objects by their interface names.
 *
 * <p>The original XML configuration is kept in place for backward compatibility
 * until all modules are migrated to Java configuration.</p>
 */
@Configuration
public class CommonEntityConfiguration {

    @Bean(name = "org.broadleafcommerce.common.email.domain.EmailTracking")
    @Scope("prototype")
    public EmailTrackingImpl emailTracking() {
        return new EmailTrackingImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.email.domain.EmailTrackingClicks")
    @Scope("prototype")
    public EmailTrackingClicksImpl emailTrackingClicks() {
        return new EmailTrackingClicksImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.email.domain.EmailTrackingOpens")
    @Scope("prototype")
    public EmailTrackingOpensImpl emailTrackingOpens() {
        return new EmailTrackingOpensImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.email.domain.EmailTarget")
    @Scope("prototype")
    public EmailTargetImpl emailTarget() {
        return new EmailTargetImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.enumeration.domain.DataDrivenEnumeration")
    @Scope("prototype")
    public DataDrivenEnumerationImpl dataDrivenEnumeration() {
        return new DataDrivenEnumerationImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.enumeration.domain.DataDrivenEnumerationValue")
    @Scope("prototype")
    public DataDrivenEnumerationValueImpl dataDrivenEnumerationValue() {
        return new DataDrivenEnumerationValueImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.config.domain.SystemProperty")
    @Scope("prototype")
    public SystemPropertyImpl systemProperty() {
        return new SystemPropertyImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.i18n.domain.ISOCountry")
    @Scope("prototype")
    public ISOCountryImpl isoCountry() {
        return new ISOCountryImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.i18n.domain.Translation")
    @Scope("prototype")
    public TranslationImpl translation() {
        return new TranslationImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.structure.dto.StructuredContentDTO")
    @Scope("prototype")
    public StructuredContentDTO structuredContentDTO() {
        return new StructuredContentDTO();
    }

    @Bean(name = "org.broadleafcommerce.common.structure.dto.ItemCriteriaDTO")
    @Scope("prototype")
    public ItemCriteriaDTO itemCriteriaDTO() {
        return new ItemCriteriaDTO();
    }

    @Bean(name = "org.broadleafcommerce.common.media.domain.MediaDto")
    public MediaDto mediaDto() {
        return new MediaDto();
    }

    @Bean(name = "org.broadleafcommerce.common.sitemap.domain.SiteMapConfiguration")
    @Scope("prototype")
    public SiteMapConfigurationImpl siteMapConfiguration() {
        return new SiteMapConfigurationImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.sitemap.domain.SiteMapGeneratorConfiguration")
    @Scope("prototype")
    public SiteMapGeneratorConfigurationImpl siteMapGeneratorConfiguration() {
        return new SiteMapGeneratorConfigurationImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.sitemap.domain.SiteMapUrlEntry")
    @Scope("prototype")
    public SiteMapUrlEntryImpl siteMapUrlEntry() {
        return new SiteMapUrlEntryImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.sitemap.domain.CustomUrlSiteMapGeneratorConfiguration")
    @Scope("prototype")
    public CustomUrlSiteMapGeneratorConfigurationImpl customUrlSiteMapGeneratorConfiguration() {
        return new CustomUrlSiteMapGeneratorConfigurationImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.sandbox.domain.SandBoxManagement")
    @Scope("prototype")
    public SandBoxManagementImpl sandBoxManagement() {
        return new SandBoxManagementImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.site.domain.Site")
    @Scope("prototype")
    public SiteImpl site() {
        return new SiteImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.site.domain.Catalog")
    @Scope("prototype")
    public CatalogImpl catalog() {
        return new CatalogImpl();
    }

    @Bean(name = "org.broadleafcommerce.common.site.domain.SiteCatalogXref")
    @Scope("prototype")
    public SiteCatalogXrefImpl siteCatalogXref() {
        return new SiteCatalogXrefImpl();
    }

    @Bean(name = "org.broadleafcommerce.profile.core.domain.IdGeneration")
    @Scope("prototype")
    public IdGenerationImpl idGeneration() {
        return new IdGenerationImpl();
    }
}
