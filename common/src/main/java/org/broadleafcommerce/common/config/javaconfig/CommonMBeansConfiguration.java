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

import org.broadleafcommerce.common.cache.StatisticsServiceImpl;
import org.broadleafcommerce.common.cache.StatisticsServiceLogAdapter;
import org.broadleafcommerce.common.extensibility.jpa.AutoDDLCreateStatusTestBean;
import org.broadleafcommerce.common.extensibility.jpa.AutoDDLCreateStatusTestBeanImpl;
import org.broadleafcommerce.common.jmx.AnnotationJmxAttributeSource;
import org.broadleafcommerce.common.jmx.MetadataMBeanInfoAssembler;
import org.broadleafcommerce.common.jmx.MetadataNamingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.jndi.JndiObjectFactoryBean;

import java.util.HashMap;
import java.util.Map;

/**
 * Java configuration equivalent of bl-common-applicationContext-mbeans.xml.
 * Defines JMX/MBean-related beans for monitoring and management.
 * All beans in this configuration are disabled when the "mbeansdisabled" Spring profile is active.
 *
 * <p>The original XML configuration is kept in place for backward compatibility
 * until all modules are migrated to Java configuration.</p>
 */
@Configuration
@Profile("!mbeansdisabled")
public class CommonMBeansConfiguration {

    @Bean(name = "blJmxNamingBean")
    public JndiObjectFactoryBean blJmxNamingBean() {
        JndiObjectFactoryBean jndiObjectFactoryBean = new JndiObjectFactoryBean();
        jndiObjectFactoryBean.setJndiName("java:comp/env/appName");
        jndiObjectFactoryBean.setDefaultObject("broadleaf");
        return jndiObjectFactoryBean;
    }

    @Bean(name = "blAttributeSource")
    public AnnotationJmxAttributeSource blAttributeSource(
            @Qualifier("blJmxNamingBean") Object blJmxNamingBean) {
        return new AnnotationJmxAttributeSource(String.valueOf(blJmxNamingBean));
    }

    @Bean(name = "blAssembler")
    public MetadataMBeanInfoAssembler blAssembler(
            @Qualifier("blAttributeSource") AnnotationJmxAttributeSource blAttributeSource) {
        MetadataMBeanInfoAssembler assembler = new MetadataMBeanInfoAssembler();
        assembler.setAttributeSource(blAttributeSource);
        return assembler;
    }

    @Bean(name = "blNamingStrategy")
    public MetadataNamingStrategy blNamingStrategy(
            @Qualifier("blAttributeSource") AnnotationJmxAttributeSource blAttributeSource) {
        MetadataNamingStrategy namingStrategy = new MetadataNamingStrategy();
        namingStrategy.setAttributeSource(blAttributeSource);
        return namingStrategy;
    }

    @Bean(name = "blAutoDDLStatusTestBean")
    public AutoDDLCreateStatusTestBeanImpl blAutoDDLStatusTestBean() {
        return new AutoDDLCreateStatusTestBeanImpl();
    }

    @Bean(name = "blAutoDDLStatusExporter")
    public MBeanExporter blAutoDDLStatusExporter(
            @Qualifier("blAutoDDLStatusTestBean") AutoDDLCreateStatusTestBeanImpl blAutoDDLStatusTestBean) {
        MBeanExporter exporter = new MBeanExporter();
        Map<String, Object> beans = new HashMap<>();
        beans.put("bean:name=autoDDLCreateStatusTestBean", blAutoDDLStatusTestBean);
        exporter.setBeans(beans);
        exporter.setRegistrationPolicy(RegistrationPolicy.IGNORE_EXISTING);
        return exporter;
    }
}
