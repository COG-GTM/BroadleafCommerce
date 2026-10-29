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
package org.broadleafcommerce.common.sitemap.service;

import org.broadleafcommerce.common.config.domain.ModuleConfiguration;
import org.broadleafcommerce.common.config.service.ModuleConfigurationService;
import org.broadleafcommerce.common.config.service.type.ModuleConfigurationType;
import org.broadleafcommerce.common.file.service.BroadleafFileServiceImpl;
import org.broadleafcommerce.common.file.service.FileSystemFileServiceProvider;
import org.broadleafcommerce.common.sitemap.domain.CustomUrlSiteMapGeneratorConfiguration;
import org.broadleafcommerce.common.sitemap.domain.CustomUrlSiteMapGeneratorConfigurationImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapConfiguration;
import org.broadleafcommerce.common.sitemap.domain.SiteMapConfigurationImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapGeneratorConfiguration;
import org.broadleafcommerce.common.sitemap.domain.SiteMapUrlEntry;
import org.broadleafcommerce.common.sitemap.domain.SiteMapUrlEntryImpl;
import org.broadleafcommerce.common.sitemap.service.type.SiteMapChangeFreqType;
import org.broadleafcommerce.common.sitemap.service.type.SiteMapGeneratorType;
import org.broadleafcommerce.common.sitemap.service.type.SiteMapPriorityType;
import org.broadleafcommerce.common.web.BaseUrlResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link SiteMapServiceImpl#getSiteMapFile(String)} only regenerates the sitemap for file names the
 * generator produces, and never runs concurrent or back-to-back on-demand generations.
 */
public class SiteMapServiceImplTest {

    protected TestSiteMapService siteMapService;
    protected BroadleafFileServiceImpl fileService;
    protected CountingSiteMapGenerator generator;

    @Before
    public void setup() {
        fileService = new BroadleafFileServiceImpl();
        fileService.setDefaultFileServiceProvider(new FileSystemFileServiceProvider());
        deleteSiteMapFiles();

        CustomUrlSiteMapGeneratorConfiguration smgc = new CustomUrlSiteMapGeneratorConfigurationImpl();
        smgc.setDisabled(false);
        smgc.setSiteMapGeneratorType(SiteMapGeneratorType.CUSTOM);
        SiteMapUrlEntry urlEntry = new SiteMapUrlEntryImpl();
        urlEntry.setLastMod(new Date());
        urlEntry.setLocation("http://www.heatclinic.com/1");
        urlEntry.setSiteMapChangeFreq(SiteMapChangeFreqType.HOURLY);
        urlEntry.setSiteMapPriority(SiteMapPriorityType.POINT5);
        smgc.setCustomURLEntries(new ArrayList<>(Collections.singletonList(urlEntry)));

        SiteMapConfiguration smc = new SiteMapConfigurationImpl();
        smc.setIndexedSiteMapFileName("sitemap_index.xml");
        List<SiteMapGeneratorConfiguration> smgcList = new ArrayList<>();
        smgcList.add(smgc);
        smc.setSiteMapGeneratorConfigurations(smgcList);
        smgc.setSiteMapConfiguration(smc);

        List<ModuleConfiguration> mcList = new ArrayList<>();
        mcList.add(smc);
        ModuleConfigurationService mcs = new StubModuleConfigurationService(mcList);

        generator = new CountingSiteMapGenerator();
        List<SiteMapGenerator> generators = new ArrayList<>();
        generators.add(generator);

        siteMapService = new TestSiteMapService();
        siteMapService.broadleafFileService = fileService;
        siteMapService.baseUrlResolver = new BaseUrlResolver() {

            @Override
            public String getSiteBaseUrl() {
                return "http://www.heatclinic.com";
            }

            @Override
            public String getAdminBaseUrl() {
                return "http://www.heatclinic.com/admin";
            }
        };
        siteMapService.setGzipSiteMapFiles(false);
        siteMapService.setModuleConfigurationService(mcs);
        siteMapService.setSiteMapGenerators(generators);
    }

    @After
    public void deleteSiteMapFiles() {
        fileService.removeResource("/sitemap.xml");
        fileService.removeResource("/sitemap_index.xml");
        fileService.removeResource("/sitemap1.xml");
    }

    @Test
    public void testUnknownFileNameNeverTriggersGeneration() throws Exception {
        for (String name : new String[] { "/sitemap-abc.xml", "/sitemap-def.xml", "/sitemap-abc.xml", "/robots.txt" }) {
            assertNull(name, siteMapService.getSiteMapFile(name));
        }
        assertEquals(0, generator.invocations.get());
        assertFalse(fileService.getResource("/sitemap.xml").exists());
    }

    @Test
    public void testConfiguredFileNameGeneratesOnceAndIsServed() throws Exception {
        File first = siteMapService.getSiteMapFile("/sitemap.xml");
        assertNotNull(first);
        assertTrue(first.exists());
        assertEquals(1, generator.invocations.get());

        File second = siteMapService.getSiteMapFile("/sitemap.xml");
        assertTrue(second.exists());
        assertEquals(1, generator.invocations.get());
    }

    @Test
    public void testMissingIndexedFileIsThrottledByMinimumInterval() throws Exception {
        File missing = siteMapService.getSiteMapFile("/sitemap99.xml");
        assertFalse(missing.exists());
        assertEquals(1, generator.invocations.get());

        for (int i = 0; i < 5; i++) {
            assertFalse(siteMapService.getSiteMapFile("/sitemap99.xml").exists());
            assertFalse(siteMapService.getSiteMapFile("/sitemap98.xml.gz").exists());
        }
        assertEquals(1, generator.invocations.get());

        siteMapService.minIntervalMillis = 0L;
        siteMapService.getSiteMapFile("/sitemap99.xml");
        assertEquals(2, generator.invocations.get());
    }

    @Test
    public void testMissingIndexedFileDoesNotThrottleConfiguredFileName() throws Exception {
        assertFalse(siteMapService.getSiteMapFile("/sitemap99.xml").exists());
        assertEquals(1, generator.invocations.get());
        deleteSiteMapFiles();

        assertTrue(siteMapService.getSiteMapFile("/sitemap.xml").exists());
        assertEquals(2, generator.invocations.get());

        assertFalse(siteMapService.getSiteMapFile("/sitemap_index.xml").exists());
        assertEquals(3, generator.invocations.get());
        assertFalse(siteMapService.getSiteMapFile("/sitemap_index.xml.gz").exists());
        assertEquals(3, generator.invocations.get());
    }

    @Test
    public void testFailedGenerationIsNotThrottled() throws Exception {
        generator.failuresRemaining.set(1);
        try {
            siteMapService.getSiteMapFile("/sitemap.xml");
            fail("Expected the generator failure to propagate");
        } catch (RuntimeException e) {
            assertEquals("simulated generator failure", e.getMessage());
        }
        assertEquals(1, generator.invocations.get());

        assertTrue(siteMapService.getSiteMapFile("/sitemap.xml").exists());
        assertEquals(2, generator.invocations.get());
    }

    @Test
    public void testConcurrentRequestDoesNotStartSecondGeneration() throws Exception {
        generator.release = new CountDownLatch(1);
        generator.entered = new CountDownLatch(1);
        Thread first = new Thread(() -> {
            try {
                siteMapService.getSiteMapFile("/sitemap.xml");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        first.start();
        assertTrue(generator.entered.await(10, TimeUnit.SECONDS));

        siteMapService.minIntervalMillis = 0L;
        File whileRunning = siteMapService.getSiteMapFile("/sitemap.xml");
        assertFalse(whileRunning.exists());
        assertEquals(1, generator.invocations.get());

        generator.release.countDown();
        first.join(10000);
        assertTrue(siteMapService.getSiteMapFile("/sitemap.xml").exists());
        assertEquals(1, generator.invocations.get());
    }

    @Test
    public void testCreateIfNotFoundDisabledNeverGenerates() throws Exception {
        siteMapService.createIfNotFound = false;
        assertNull(siteMapService.getSiteMapFile("/sitemap.xml"));
        assertEquals(0, generator.invocations.get());
    }

    @Test
    public void testIsGeneratedSiteMapFileName() {
        for (String valid : new String[] { "/sitemap.xml", "sitemap.xml", "/sitemap.xml.gz", "/sitemap_index.xml",
                "/sitemap_index.xml.gz", "/sitemap1.xml", "/sitemap12.xml.gz", "//sitemap3.xml" }) {
            assertTrue(valid, siteMapService.isGeneratedSiteMapFileName(valid));
        }
        for (String invalid : new String[] { null, "", "/", "/sitemap-abc.xml", "/sitemap0.xml", "/sitemap01.xml",
                "/sitemap.xml.gz.gz", "/sitemapx.xml", "/sitemap1.xmlx", "/../sitemap.xml", "/foo/sitemap.xml",
                "/sitemap1234567890.xml", "/SITEMAP.XML" }) {
            assertFalse(String.valueOf(invalid), siteMapService.isGeneratedSiteMapFileName(invalid));
        }
    }

    protected static class StubModuleConfigurationService implements ModuleConfigurationService {

        protected final List<ModuleConfiguration> siteMapConfigurations;

        protected StubModuleConfigurationService(List<ModuleConfiguration> siteMapConfigurations) {
            this.siteMapConfigurations = siteMapConfigurations;
        }

        @Override
        public List<ModuleConfiguration> findActiveConfigurationsByType(ModuleConfigurationType type) {
            return ModuleConfigurationType.SITE_MAP.equals(type) ? siteMapConfigurations : new ArrayList<>();
        }

        @Override
        public ModuleConfiguration findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModuleConfiguration save(ModuleConfiguration config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(ModuleConfiguration config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ModuleConfiguration> findAllConfigurationByType(ModuleConfigurationType type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ModuleConfiguration> findByType(Class<? extends ModuleConfiguration> type) {
            throw new UnsupportedOperationException();
        }
    }

    protected static class TestSiteMapService extends SiteMapServiceImpl {

        protected boolean createIfNotFound = true;
        protected long minIntervalMillis = 60000L;

        @Override
        public boolean getCreateSiteMapIfNotFound() {
            return createIfNotFound;
        }

        @Override
        public boolean getAutoGenerateSiteMapAfterTimeout() {
            return false;
        }

        @Override
        public Long getSiteMapTimeoutInMillis() {
            return 3600000L;
        }

        @Override
        public long getOnDemandGenerationMinIntervalInMillis() {
            return minIntervalMillis;
        }
    }

    protected static class CountingSiteMapGenerator extends CustomUrlSiteMapGenerator {

        protected final AtomicInteger invocations = new AtomicInteger();
        protected final AtomicInteger failuresRemaining = new AtomicInteger();
        protected CountDownLatch entered;
        protected CountDownLatch release;

        @Override
        public void addSiteMapEntries(SiteMapGeneratorConfiguration smgc, SiteMapBuilder siteMapBuilder) {
            invocations.incrementAndGet();
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new RuntimeException("simulated generator failure");
            }
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            super.addSiteMapEntries(smgc, siteMapBuilder);
        }
    }
}
