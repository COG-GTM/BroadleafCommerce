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

import org.broadleafcommerce.common.cache.NoOpStatisticsServiceLogAdapter;
import org.broadleafcommerce.common.cache.StatisticsServiceLogAdapter;
import org.broadleafcommerce.common.email.service.LoggingMailSender;
import org.broadleafcommerce.common.email.service.info.EmailInfo;
import org.broadleafcommerce.common.email.service.info.NullEmailInfo;
import org.broadleafcommerce.common.email.service.info.ServerInfo;
import org.broadleafcommerce.common.email.service.message.NullMessageCreator;
import org.broadleafcommerce.common.event.BroadleafApplicationEventMulticaster;
import org.broadleafcommerce.common.extensibility.jpa.convert.EntityMarkerClassTransformer;
import org.broadleafcommerce.common.extensibility.jpa.copy.ConditionalFieldAnnotationsClassTransformer;
import org.broadleafcommerce.common.extensibility.jpa.copy.DirectCopyClassTransformer;
import org.broadleafcommerce.common.extensibility.jpa.copy.DirectCopyIgnorePattern;
import org.broadleafcommerce.common.util.BroadleafMergeResourceBundleMessageSource;
import org.broadleafcommerce.common.web.BaseUrlResolverImpl;
import org.broadleafcommerce.common.web.NullBroadleafSiteResolver;
import org.broadleafcommerce.common.web.NullBroadleafThemeResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ListFactoryBean;
import org.springframework.beans.factory.config.MapFactoryBean;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.cache.CacheManager;

/**
 * Java configuration equivalent of bl-common-applicationContext.xml.
 * Defines core common beans including component scanning, email, resource handling,
 * class transformers, and various collection-based merge beans.
 *
 * <p>The original XML configuration is kept in place for backward compatibility
 * until all modules are migrated to Java configuration.</p>
 */
@Configuration
@Import({CommonPersistenceConfiguration.class, CommonWrapperConfiguration.class})
@ComponentScan(
    basePackages = "org.broadleafcommerce.common",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.broadleafcommerce\\.common\\.web\\.controller\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.broadleafcommerce\\.common\\.web\\.site\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.broadleafcommerce\\.common\\.web\\.api\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.broadleafcommerce\\.common\\.web\\.config\\..*")
    }
)
public class CommonConfiguration {

    @Bean
    public static PropertySourcesPlaceholderConfigurer blPropertyPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setIgnoreUnresolvablePlaceholders(true);
        return configurer;
    }

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster applicationEventMulticaster() {
        return new BroadleafApplicationEventMulticaster();
    }

    @Bean(name = "blMessageCreator")
    public NullMessageCreator blMessageCreator(@Qualifier("blMailSender") JavaMailSender mailSender) {
        return new NullMessageCreator(mailSender);
    }

    @Bean(name = "blDirectCopyIgnorePatterns")
    public ListFactoryBean blDirectCopyIgnorePatterns() {
        DirectCopyIgnorePattern pattern = new DirectCopyIgnorePattern();
        pattern.setPatterns(new String[]{
            ".*HibernateAccessOptimizer.*",
            "org\\.apache.*",
            "org\\.jboss.*",
            "org\\.ehcache.*",
            "com\\.ctc.*",
            "org\\.reactivestreams.*",
            "jdk.*",
            "org\\.springframework.*",
            "javassist.*",
            "javax.*",
            "jakarta.*",
            "org.broadleafcommerce.openadmin.web.compatibility.JSCompatibilityRequestWrapper",
            "org\\.hibernate.*",
            "org\\.quartz.*",
            "org\\.terracotta.*",
            "org\\.ehcache.*",
            "java.*",
            "com\\.fasterxml.*",
            "com\\.mysql.*",
            "org\\.antlr.*",
            "net\\.bytebuddy.*",
            "org\\.owasp.*",
            "org\\.htmlunit.*",
            "io\\.netty.*",
            "org\\.mockito.*"
        });

        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(pattern);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blBaseUrlResolver")
    public BaseUrlResolverImpl blBaseUrlResolver() {
        return new BaseUrlResolverImpl();
    }

    @Bean(name = "blDirectCopyTransformTokenMap")
    public MapFactoryBean blDirectCopyTransformTokenMap() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        Map<String, String> sourceMap = new HashMap<>();
        sourceMap.put("archiveOnly", "org.broadleafcommerce.common.weave.WeaveArchiveStatus");
        mapFactoryBean.setSourceMap(sourceMap);
        return mapFactoryBean;
    }

    @Bean(name = "blDirectCopyClassPreLoadPatterns")
    public ListFactoryBean blDirectCopyClassPreLoadPatterns() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList(
            "javassist.ClassPool",
            "javassist.CtClass",
            "javassist.CtConstructor",
            "javassist.CtField",
            "javassist.CtMethod",
            "javassist.LoaderClassPath",
            "javassist.NotFoundException",
            "javassist.bytecode.AnnotationsAttribute",
            "javassist.bytecode.ClassFile",
            "javassist.bytecode.ConstPool",
            "javassist.bytecode.annotation.Annotation",
            "javassist.bytecode.annotation.AnnotationMemberValue",
            "javassist.bytecode.annotation.ArrayMemberValue",
            "javassist.bytecode.annotation.BooleanMemberValue",
            "javassist.bytecode.annotation.MemberValue",
            "javassist.bytecode.annotation.StringMemberValue",
            "jakarta.annotation.Resource",
            "jakarta.persistence.EntityListeners",
            "jakarta.persistence.Embeddable",
            "jakarta.persistence.Entity",
            "jakarta.persistence.MappedSuperclass",
            "org.apache.commons.lang3.StringUtils",
            "org.broadleafcommerce.common.logging.LifeCycleEvent",
            "org.broadleafcommerce.common.logging.SupportLogManager",
            "org.broadleafcommerce.common.logging.SupportLogger",
            "org.broadleafcommerce.common.extensibility.jpa.copy.NonCopied"
        ));
        return listFactoryBean;
    }

    @Bean(name = "blAnnotationDirectCopyClassTransformer")
    public DirectCopyClassTransformer blAnnotationDirectCopyClassTransformer(
            @Qualifier("blDirectCopyTransformTokenMap") Map<String, String> templateTokens,
            @Qualifier("blDirectCopyClassPreLoadPatterns") List<String> preLoadPatterns) {
        DirectCopyClassTransformer transformer = new DirectCopyClassTransformer("Annotated Transformation");
        transformer.setTemplateTokens(templateTokens);
        transformer.setPreLoadClassNamePatterns(preLoadPatterns);
        return transformer;
    }

    @Bean(name = "blEntityMarkerClassTransformer")
    public EntityMarkerClassTransformer blEntityMarkerClassTransformer(
            @Qualifier("blDirectCopyClassPreLoadPatterns") List<String> preLoadPatterns) {
        EntityMarkerClassTransformer transformer = new EntityMarkerClassTransformer();
        transformer.setPreLoadClassNamePatterns(preLoadPatterns);
        return transformer;
    }

    @Bean(name = "blConditionalFieldAnnotationClassTransformer")
    public ConditionalFieldAnnotationsClassTransformer blConditionalFieldAnnotationClassTransformer() {
        return new ConditionalFieldAnnotationsClassTransformer("Conditional Field Annotation Transformation");
    }

    @Bean(name = "blMergedClassTransformers")
    public ListFactoryBean blMergedClassTransformers(
            @Qualifier("blAnnotationDirectCopyClassTransformer") DirectCopyClassTransformer annotationTransformer,
            @Qualifier("blEntityMarkerClassTransformer") EntityMarkerClassTransformer entityMarkerTransformer,
            @Qualifier("blConditionalFieldAnnotationClassTransformer") ConditionalFieldAnnotationsClassTransformer conditionalTransformer) {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(annotationTransformer);
        sourceList.add(entityMarkerTransformer);
        sourceList.add(conditionalTransformer);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blServerInfo")
    public ServerInfo blServerInfo() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setServerName("localhost");
        serverInfo.setServerPort(8080);
        return serverInfo;
    }

    @Bean(name = "blMailSender")
    public JavaMailSenderImpl blMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(25);
        mailSender.setProtocol("smtp");
        Properties javaMailProperties = new Properties();
        javaMailProperties.put("mail.smtp.starttls.enable", "true");
        javaMailProperties.put("mail.smtp.timeout", "25000");
        mailSender.setJavaMailProperties(javaMailProperties);
        return mailSender;
    }

    @Bean(name = "blSpringCacheManager")
    public JCacheCacheManager blSpringCacheManager(@Qualifier("blCacheManager") CacheManager cacheManager) {
        JCacheCacheManager springCacheManager = new JCacheCacheManager();
        springCacheManager.setCacheManager(cacheManager);
        return springCacheManager;
    }

    @Bean(name = "messageSource")
    public BroadleafMergeResourceBundleMessageSource messageSource(
            @Value("${messages.useCodeAsDefaultMessage}") boolean useCodeAsDefaultMessage,
            @Value("${messages.cacheSeconds}") int cacheSeconds) {
        BroadleafMergeResourceBundleMessageSource messageSource = new BroadleafMergeResourceBundleMessageSource();
        messageSource.setUseCodeAsDefaultMessage(useCodeAsDefaultMessage);
        messageSource.setCacheSeconds(cacheSeconds);
        return messageSource;
    }

    @Bean(name = "blMessageSourceBaseNames")
    @Scope("prototype")
    public ListFactoryBean blMessageSourceBaseNames() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blLoggingMailSender")
    public LoggingMailSender blLoggingMailSender() {
        return new LoggingMailSender();
    }

    @Bean(name = "blEmailInfo")
    public EmailInfo blEmailInfo() {
        return new EmailInfo();
    }

    @Bean(name = "blNullEmailInfo")
    public NullEmailInfo blNullEmailInfo() throws java.io.IOException {
        return new NullEmailInfo();
    }

    @Bean(name = "blSiteResolver")
    public NullBroadleafSiteResolver blSiteResolver() {
        return new NullBroadleafSiteResolver();
    }

    @Bean(name = "blThemeResolver")
    public NullBroadleafThemeResolver blThemeResolver() {
        return new NullBroadleafThemeResolver();
    }

    @Bean(name = "blSiteResourceResolvers")
    public ListFactoryBean blSiteResourceResolvers(
            @Qualifier("blBLCJSUrlPathResolver") Object blBLCJSUrlPathResolver,
            @Qualifier("blCacheResourceResolver") Object blCacheResourceResolver,
            @Qualifier("blVersionResourceResolver") Object blVersionResourceResolver,
            @Qualifier("blBundleResourceResolver") Object blBundleResourceResolver,
            @Qualifier("blBLCJSResolver") Object blBLCJSResolver,
            @Qualifier("blSystemPropertyJSResolver") Object blSystemPropertyJSResolver,
            @Qualifier("blPathResourceResolver") Object blPathResourceResolver) {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(blBLCJSUrlPathResolver);
        sourceList.add(blCacheResourceResolver);
        sourceList.add(blVersionResourceResolver);
        sourceList.add(blBundleResourceResolver);
        sourceList.add(blBLCJSResolver);
        sourceList.add(blSystemPropertyJSResolver);
        sourceList.add(blPathResourceResolver);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blJsLocations")
    public ListFactoryBean blJsLocations() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList(
            "classpath:/common_style/js/",
            "classpath:/extensions/js/"
        ));
        return listFactoryBean;
    }

    @Bean(name = "blCssLocations")
    public ListFactoryBean blCssLocations() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("classpath:/common_style/css/"));
        return listFactoryBean;
    }

    @Bean(name = "blFontLocations")
    public ListFactoryBean blFontLocations() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("classpath:/common_style/fonts/"));
        return listFactoryBean;
    }

    @Bean(name = "blImageLocations")
    public ListFactoryBean blImageLocations() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("classpath:/common_style/img/"));
        return listFactoryBean;
    }

    @Bean(name = "blJsFileList")
    public ListFactoryBean blJsFileList() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blAdminJsLibFileList")
    public ListFactoryBean blAdminJsLibFileList() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blCssFileList")
    public ListFactoryBean blCssFileList() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blAdditionalBundleFiles")
    public MapFactoryBean blAdditionalBundleFiles() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(new HashMap<String, List<?>>());
        return mapFactoryBean;
    }

    @Bean(name = "blJsResourceResolvers")
    public ListFactoryBean blJsResourceResolvers() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blCssResourceResolvers")
    public ListFactoryBean blCssResourceResolvers() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blJsResourceTransformers")
    public ListFactoryBean blJsResourceTransformers(
            @Qualifier("blCachingResourceTransformer") Object blCachingResourceTransformer,
            @Qualifier("blMinifyResourceTransformer") Object blMinifyResourceTransformer) {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(blCachingResourceTransformer);
        sourceList.add(blMinifyResourceTransformer);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blCssResourceTransformers")
    public ListFactoryBean blCssResourceTransformers(
            @Qualifier("blCachingResourceTransformer") Object blCachingResourceTransformer,
            @Qualifier("blMinifyResourceTransformer") Object blMinifyResourceTransformer) {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(blCachingResourceTransformer);
        sourceList.add(blMinifyResourceTransformer);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blFileServiceProviders")
    public ListFactoryBean blFileServiceProviders() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blPaymentGatewayConfigurationServices")
    public ListFactoryBean blPaymentGatewayConfigurationServices() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blSiteMapGenerators")
    public ListFactoryBean blSiteMapGenerators(
            @Qualifier("blCustomSiteMapGenerator") Object blCustomSiteMapGenerator) {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(blCustomSiteMapGenerator);
        listFactoryBean.setSourceList(sourceList);
        return listFactoryBean;
    }

    @Bean(name = "blLinkedDataGenerators")
    public ListFactoryBean blLinkedDataGenerators() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(new ArrayList<>());
        return listFactoryBean;
    }

    @Bean(name = "blStatisticsServiceLogAdapter")
    public StatisticsServiceLogAdapter blStatisticsServiceLogAdapter() {
        return new NoOpStatisticsServiceLogAdapter();
    }

    @Bean(name = "blEntityExtensionManagers")
    public MapFactoryBean blEntityExtensionManagers() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(new HashMap<>());
        return mapFactoryBean;
    }

    @Bean(name = "blTranslationExceptionProperties")
    public ListFactoryBean blTranslationExceptionProperties() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList("pageTemplate.*"));
        return listFactoryBean;
    }

    @Bean(name = "blConditionalDirectCopyTransformers")
    public MapFactoryBean blConditionalDirectCopyTransformers() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(new HashMap<>());
        return mapFactoryBean;
    }

    @Bean(name = "blConditionalEntities")
    public MapFactoryBean blConditionalEntities() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(new HashMap<>());
        return mapFactoryBean;
    }

    @Bean(name = "blConditionalOrmFiles")
    public MapFactoryBean blConditionalOrmFiles() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(new HashMap<>());
        return mapFactoryBean;
    }

    @Bean(name = "blConditionalFieldAnnotationCopyTransformers")
    public MapFactoryBean blConditionalFieldAnnotationCopyTransformers() {
        MapFactoryBean mapFactoryBean = new MapFactoryBean();
        mapFactoryBean.setSourceMap(new HashMap<>());
        return mapFactoryBean;
    }

    @Bean(name = "blPrecompressedArtifactFileExtensionWhitelist")
    @Scope("prototype")
    public ListFactoryBean blPrecompressedArtifactFileExtensionWhitelist() {
        ListFactoryBean listFactoryBean = new ListFactoryBean();
        listFactoryBean.setSourceList(Arrays.asList(
            ".html", ".js", ".css", ".ico", ".woff", ".txt"
        ));
        return listFactoryBean;
    }
}
