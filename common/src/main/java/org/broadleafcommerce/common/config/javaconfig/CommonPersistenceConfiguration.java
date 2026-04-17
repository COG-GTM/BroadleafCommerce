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

import org.broadleafcommerce.common.extensibility.cache.jcache.MergeJCacheManagerFactoryBean;
import org.broadleafcommerce.common.extensibility.jpa.JCachePersistenceUnitPostProcessor;
import org.broadleafcommerce.common.extensibility.jpa.JPAPropertiesPersistenceUnitPostProcessor;
import org.broadleafcommerce.common.extensibility.jpa.MergePersistenceUnitManager;
import org.broadleafcommerce.common.extensibility.jpa.ORMConfigPersistenceUnitPostProcessor;
import org.broadleafcommerce.common.persistence.EntityConfiguration;
import org.broadleafcommerce.common.persistence.transaction.LifecycleAwareJpaTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ListFactoryBean;
import org.springframework.beans.factory.config.MapFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.support.SharedEntityManagerBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cache.CacheManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Java configuration equivalent of bl-common-applicationContext-persistence.xml.
 * Defines persistence-related beans including EntityManagerFactory, TransactionManager,
 * cache management, and persistence unit configuration.
 *
 * <p>The original XML configuration is kept in place for backward compatibility
 * until all modules are migrated to Java configuration.</p>
 */
@Configuration
@EnableTransactionManagement
@Import(CommonMBeansConfiguration.class)
public class CommonPersistenceConfiguration {

    @Bean(name = "entityManagerFactory")
    @DependsOn("blCacheManager")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("blJpaVendorAdapter") JpaVendorAdapter jpaVendorAdapter,
            @Qualifier("blPersistenceUnitManager") MergePersistenceUnitManager persistenceUnitManager) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setJpaVendorAdapter(jpaVendorAdapter);
        emf.setPersistenceUnitManager(persistenceUnitManager);
        emf.setPersistenceUnitName("blPU");
        return emf;
    }

    @Bean(name = "prodEntityManager")
    public SharedEntityManagerBean prodEntityManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        SharedEntityManagerBean sharedEmBean = new SharedEntityManagerBean();
        sharedEmBean.setEntityManagerFactory(entityManagerFactory);
        return sharedEmBean;
    }

    @Bean(name = {"blTransactionManager", "transactionManager"})
    @Primary
    public LifecycleAwareJpaTransactionManager blTransactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        LifecycleAwareJpaTransactionManager txManager = new LifecycleAwareJpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory);
        return txManager;
    }

    @Bean(name = "blDefaultTargetModeMap")
    public MapFactoryBean blDefaultTargetModeMap(
            @Qualifier("prodEntityManager") SharedEntityManagerBean prodEntityManager,
            @Qualifier("blTransactionManager") LifecycleAwareJpaTransactionManager blTransactionManager) {
        Map<String, Map<String, Object>> sourceMap = new HashMap<>();

        Map<String, Object> sandboxMap = new HashMap<>();
        sandboxMap.put("entityManager", prodEntityManager);
        sandboxMap.put("transactionManager", blTransactionManager);
        sourceMap.put("sandbox", sandboxMap);

        Map<String, Object> stageMap = new HashMap<>();
        stageMap.put("entityManager", prodEntityManager);
        stageMap.put("transactionManager", blTransactionManager);
        sourceMap.put("stage", stageMap);

        Map<String, Object> productionMap = new HashMap<>();
        productionMap.put("entityManager", prodEntityManager);
        productionMap.put("transactionManager", blTransactionManager);
        sourceMap.put("production", productionMap);

        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(sourceMap);
        return mapFactoryBean;
    }

    @Bean(name = "blTargetModeMaps")
    public ListFactoryBean blTargetModeMaps(
            @Qualifier("blDefaultTargetModeMap") Map<String, ?> blDefaultTargetModeMap) {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(blDefaultTargetModeMap);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blCacheManager")
    public MergeJCacheManagerFactoryBean blCacheManager() {
        return new MergeJCacheManagerFactoryBean();
    }

    @Bean(name = "blMergedCacheConfigLocations")
    public ListFactoryBean blMergedCacheConfigLocations() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("classpath:bl-common-ehcache.xml"));
        return listFactoryBean;
    }

    @Bean(name = "blMergedPersistenceXmlLocations")
    public ListFactoryBean blMergedPersistenceXmlLocations() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("classpath*:/META-INF/persistence-common.xml"));
        return listFactoryBean;
    }

    @Bean(name = "blMergedEntityContexts")
    public ListFactoryBean blMergedEntityContexts() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("classpath:bl-common-applicationContext-entity.xml"));
        return listFactoryBean;
    }

    @Bean(name = "blPersistenceUnitManager")
    public MergePersistenceUnitManager blPersistenceUnitManager(
            @Qualifier("blPersistenceUnitPostProcessors") List<?> persistenceUnitPostProcessors) {
        MergePersistenceUnitManager manager = new MergePersistenceUnitManager();
        manager.setPersistenceUnitPostProcessors(
                persistenceUnitPostProcessors.toArray(
                        new org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor[0]));
        return manager;
    }

    @Bean(name = "blPersistenceUnitPostProcessors")
    public ListFactoryBean blPersistenceUnitPostProcessors() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(new JPAPropertiesPersistenceUnitPostProcessor());
        sourceList.add(new ORMConfigPersistenceUnitPostProcessor());
        sourceList.add(new JCachePersistenceUnitPostProcessor());
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blEntityConfiguration")
    public EntityConfiguration blEntityConfiguration() {
        return new EntityConfiguration();
    }
}
