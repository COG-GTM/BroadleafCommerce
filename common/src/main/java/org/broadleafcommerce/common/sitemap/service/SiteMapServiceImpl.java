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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.config.domain.ModuleConfiguration;
import org.broadleafcommerce.common.config.service.ModuleConfigurationService;
import org.broadleafcommerce.common.config.service.type.ModuleConfigurationType;
import org.broadleafcommerce.common.file.domain.FileWorkArea;
import org.broadleafcommerce.common.file.service.BroadleafFileService;
import org.broadleafcommerce.common.site.domain.Site;
import org.broadleafcommerce.common.sitemap.domain.SiteMapConfiguration;
import org.broadleafcommerce.common.sitemap.domain.SiteMapGeneratorConfiguration;
import org.broadleafcommerce.common.sitemap.exception.SiteMapException;
import org.broadleafcommerce.common.util.BLCSystemProperty;
import org.broadleafcommerce.common.web.BaseUrlResolver;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import jakarta.annotation.Resource;

/**
 * Component responsible for generating a sitemap.   Relies on SiteMapGenerators to
 * produce the actual url entries within the sitemap.
 * <p>
 * Create a sitemap index file and at least one sitemap file with the URL elements.
 *
 * @author bpolster
 */
@Service("blSiteMapService")
public class SiteMapServiceImpl implements SiteMapService {

    protected static final Log LOG = LogFactory.getLog(SiteMapServiceImpl.class);

    protected static final String ENCODING_EXTENSION = ".gz";

    protected static final String INDEX_FILE_TOKEN = "###";

    protected static final String INDEX_FILE_NUMBER_REGEX = "[1-9][0-9]{0,8}";

    protected static final String DEFAULT_GENERATION_KEY = "default";

    protected Boolean gzipSiteMapFiles;

    /**
     * Serializes sitemap generation so that concurrent callers never run more than one generation at a time.
     */
    protected final ReentrantLock siteMapGenerationLock = new ReentrantLock();

    /**
     * Time (millis) of the last on-demand generation triggered from {@link #getSiteMapFile(String)}, keyed by site and
     * requested file name (all numbered index files share one key).
     */
    protected final Map<String, Long> lastOnDemandGenerationTimes = new ConcurrentHashMap<>();

    @Resource(name = "blModuleConfigurationService")
    protected ModuleConfigurationService moduleConfigurationService;

    @Resource(name = "blSiteMapGenerators")
    protected List<SiteMapGenerator> siteMapGenerators = new ArrayList<SiteMapGenerator>();

    @Resource(name = "blFileService")
    protected BroadleafFileService broadleafFileService;

    @Resource(name = "blBaseUrlResolver")
    protected BaseUrlResolver baseUrlResolver;

    @Override
    public SiteMapGenerationResponse generateSiteMap() throws SiteMapException, IOException {
        siteMapGenerationLock.lock();
        try {
            return generateSiteMapInternal();
        } finally {
            siteMapGenerationLock.unlock();
        }
    }

    protected SiteMapGenerationResponse generateSiteMapInternal() throws SiteMapException, IOException {
        SiteMapGenerationResponse smgr = new SiteMapGenerationResponse();
        SiteMapConfiguration smc = findActiveSiteMapConfiguration();
        if (smc == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("No SiteMap generated since no active configuration was found.");
            }
            smgr.setHasError(true);
            smgr.setErrorCode("No SiteMap Configuration Found");
            return smgr;
        }

        FileWorkArea fileWorkArea = broadleafFileService.initializeWorkArea();
        SiteMapBuilder siteMapBuilder = new SiteMapBuilder(
                smc, fileWorkArea, baseUrlResolver.getSiteBaseUrl(), getGzipSiteMapFiles()
        );

        if (LOG.isTraceEnabled()) {
            LOG.trace("File work area initalized with path " + fileWorkArea.getFilePathLocation());
        }
        for (SiteMapGeneratorConfiguration currentConfiguration : smc.getSiteMapGeneratorConfigurations()) {
            if (currentConfiguration.isDisabled()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Skipping disabled sitemap generator configuration" + currentConfiguration.getId());
                }
                continue;
            }
            SiteMapGenerator generator = selectSiteMapGenerator(currentConfiguration);
            if (generator != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("SiteMapGenerator found, adding entries" + generator.getClass());
                }
                generator.addSiteMapEntries(currentConfiguration, siteMapBuilder);
            } else {
                LOG.warn("No site map generator found to process generator configuration for "
                        + currentConfiguration.getSiteMapGeneratorType());
            }
        }

        siteMapBuilder.persistSiteMap();

        // Check for GZip
        if (getGzipSiteMapFiles()) {
            gzipAndDeleteFiles(fileWorkArea, siteMapBuilder.getIndexedFileNames(), false);
            List<String> indexFileNames = new ArrayList<>();
            for (String fileName : siteMapBuilder.getIndexedFileNames()) {
                indexFileNames.add(fileName + ENCODING_EXTENSION);
            }
            smgr.setSiteMapFilePaths(indexFileNames);
        } else {
            smgr.setSiteMapFilePaths(siteMapBuilder.getIndexedFileNames());
        }

        // Move the generated files to their permanent location
        broadleafFileService.addOrUpdateResources(fileWorkArea, true);
        broadleafFileService.closeWorkArea(fileWorkArea);

        return smgr;
    }

    @Override
    public File getSiteMapFile(String fileName) throws SiteMapException, IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Method getSiteMapFile() invoked for " + fileName);
        }
        if (!isGeneratedSiteMapFileName(fileName)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Ignoring request for " + fileName + " since it is not a file the sitemap generator produces");
            }
            return null;
        }
        File siteMapFile = broadleafFileService.getResource(fileName, getSiteMapTimeoutInMillis());
        if (siteMapFile.exists()) {
            if (getAutoGenerateSiteMapAfterTimeout() && isSiteMapFileExpired(siteMapFile)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Generating new SiteMap after timeout");
                }
                siteMapFile = generateSiteMapOnDemand(fileName, true);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Returning existing SiteMap");
            }
            return siteMapFile;

        } else {
            if (getCreateSiteMapIfNotFound()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Generating SiteMap");
                }
                siteMapFile = generateSiteMapOnDemand(fileName, false);
                if (siteMapFile.exists()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Returning SiteMap file " + fileName);
                    }
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Sitemap file " + fileName + " not found after call to generate siteMap.xml");
                    }
                }
                return siteMapFile;
            } else {
                return null;
            }
        }
    }

    /**
     * Runs a sitemap generation on behalf of a request for {@code fileName}, unless one is already running,
     * another caller already produced the file, or the last on-demand generation for the current site and requested
     * name happened less than {@link #getOnDemandGenerationMinIntervalInMillis()} ago. Requests never queue up behind a running
     * generation; they get whatever is currently on disk.
     *
     * @param fileName the requested sitemap file name
     * @param regenerateIfExpired true when the file exists but is stale, false when the file is missing
     * @return the sitemap file after any generation (may not exist)
     */
    protected File generateSiteMapOnDemand(String fileName, boolean regenerateIfExpired)
            throws SiteMapException, IOException {
        if (!siteMapGenerationLock.tryLock()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("SiteMap generation already in progress, not starting another for " + fileName);
            }
            return broadleafFileService.getResource(fileName, getSiteMapTimeoutInMillis());
        }
        try {
            File siteMapFile = broadleafFileService.getResource(fileName, getSiteMapTimeoutInMillis());
            boolean generationNeeded = regenerateIfExpired
                    ? siteMapFile.exists() && isSiteMapFileExpired(siteMapFile)
                    : !siteMapFile.exists();
            if (!generationNeeded) {
                return siteMapFile;
            }

            String generationKey = getSiteMapGenerationKey(fileName);
            long now = System.currentTimeMillis();
            Long lastGeneration = lastOnDemandGenerationTimes.get(generationKey);
            if (lastGeneration != null && (now - lastGeneration) < getOnDemandGenerationMinIntervalInMillis()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping on-demand SiteMap generation for " + fileName
                            + " since the last one ran less than the minimum interval ago");
                }
                return siteMapFile;
            }
            lastOnDemandGenerationTimes.put(generationKey, now);

            boolean generated = false;
            try {
                generateSiteMap();
                generated = true;
            } finally {
                if (!generated) {
                    lastOnDemandGenerationTimes.remove(generationKey, now);
                }
            }
            return broadleafFileService.getResource(fileName, getSiteMapTimeoutInMillis());
        } finally {
            siteMapGenerationLock.unlock();
        }
    }

    /**
     * Whether {@code fileName} is a name that {@link SiteMapBuilder} produces for the active configuration: the
     * sitemap file name, the indexed sitemap file name, or the index file pattern with a numeric index, each with
     * an optional ".gz" suffix. Leading slashes (as passed through from the request URI) are ignored.
     */
    protected boolean isGeneratedSiteMapFileName(String fileName) {
        return getGeneratedSiteMapFileKey(fileName) != null;
    }

    /**
     * Normalized form of {@code fileName} if it is a name {@link SiteMapBuilder} produces (see
     * {@link #isGeneratedSiteMapFileName(String)}), otherwise null. Leading slashes and ".gz" are removed, and every
     * numbered index file maps to the configured index file pattern.
     */
    protected String getGeneratedSiteMapFileKey(String fileName) {
        String candidate = stripLeadingSlashes(fileName);
        if (StringUtils.isEmpty(candidate)) {
            return null;
        }
        if (candidate.endsWith(ENCODING_EXTENSION)) {
            candidate = candidate.substring(0, candidate.length() - ENCODING_EXTENSION.length());
        }

        SiteMapConfiguration smc = findActiveSiteMapConfiguration();
        if (smc == null) {
            return null;
        }
        if (candidate.equals(stripLeadingSlashes(smc.getSiteMapFileName()))
                || candidate.equals(stripLeadingSlashes(smc.getIndexedSiteMapFileName()))) {
            return candidate;
        }

        String pattern = stripLeadingSlashes(smc.getSiteMapIndexFilePattern());
        int tokenPos = pattern == null ? -1 : pattern.indexOf(INDEX_FILE_TOKEN);
        if (tokenPos < 0) {
            return null;
        }
        String regex = Pattern.quote(pattern.substring(0, tokenPos))
                + INDEX_FILE_NUMBER_REGEX
                + Pattern.quote(pattern.substring(tokenPos + INDEX_FILE_TOKEN.length()));
        return candidate.matches(regex) ? pattern : null;
    }

    protected String stripLeadingSlashes(String fileName) {
        return StringUtils.stripStart(fileName, "/");
    }

    protected boolean isSiteMapFileExpired(File siteMapFile) {
        return (System.currentTimeMillis() - siteMapFile.lastModified()) > getSiteMapTimeoutInMillis().longValue();
    }

    protected String getSiteMapGenerationKey(String fileName) {
        BroadleafRequestContext brc = BroadleafRequestContext.getBroadleafRequestContext();
        Site site = brc == null ? null : brc.getNonPersistentSite();
        String siteKey = site == null || site.getId() == null ? DEFAULT_GENERATION_KEY : String.valueOf(site.getId());
        return siteKey + ":" + getGeneratedSiteMapFileKey(fileName);
    }

    protected SiteMapConfiguration findActiveSiteMapConfiguration() {
        List<ModuleConfiguration> configurations = moduleConfigurationService.findActiveConfigurationsByType(
                ModuleConfigurationType.SITE_MAP
        );

        SiteMapConfiguration smc = null;
        if (configurations != null && !configurations.isEmpty()) {
            //Try to find a default configuration           
            for (ModuleConfiguration configuration : configurations) {
                if (configuration.getIsDefault()) {
                    smc = (SiteMapConfiguration) configuration;
                    break;
                }
            }
            if (smc == null) {
                //if there wasn't a default one, use the first active one...
                smc = (SiteMapConfiguration) configurations.get(0);
            }
        }

        return smc;
    }

    /**
     * Returns the siteMapGenerator most qualified to handle the given configuration.
     *
     * @param smgc
     * @return
     */
    protected SiteMapGenerator selectSiteMapGenerator(SiteMapGeneratorConfiguration smgc) {
        for (SiteMapGenerator siteMapGenerator : siteMapGenerators) {
            if (siteMapGenerator.canHandleSiteMapConfiguration(smgc)) {
                return siteMapGenerator;
            }
        }
        return null;
    }

    /**
     * @param fileWorkArea
     * @param fileNames
     */
    protected void gzipAndDeleteFiles(FileWorkArea fileWorkArea, List<String> fileNames, boolean shouldDeleteOriginal) {
        for (String fileName : fileNames) {
            try {
                String fileNameWithPath = FilenameUtils.normalize(
                        fileWorkArea.getFilePathLocation() + File.separator + fileName
                );

                FileInputStream fis = new FileInputStream(fileNameWithPath);
                FileOutputStream fos = new FileOutputStream(fileNameWithPath + ENCODING_EXTENSION);
                GZIPOutputStream gzipOS = new GZIPOutputStream(fos);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    gzipOS.write(buffer, 0, len);
                }
                //close resources
                gzipOS.close();
                fos.close();
                fis.close();

                if (shouldDeleteOriginal) {
                    File originalFile = new File(fileNameWithPath);
                    originalFile.delete();
                }

            } catch (IOException e) {
                LOG.error("Error writing zip file.", e);
            }
        }
    }

    /**
     * GZip a file, Then delete it
     *
     * @param fileWorkArea
     * @param fileNames
     */
    protected void gzipAndDeleteFiles(FileWorkArea fileWorkArea, List<String> fileNames) {
        gzipAndDeleteFiles(fileWorkArea, fileNames, true);
    }

    public List<SiteMapGenerator> getSiteMapGenerators() {
        return siteMapGenerators;
    }

    public void setSiteMapGenerators(List<SiteMapGenerator> siteMapGenerators) {
        this.siteMapGenerators = siteMapGenerators;
    }

    public ModuleConfigurationService getModuleConfigurationService() {
        return moduleConfigurationService;
    }

    public void setModuleConfigurationService(ModuleConfigurationService moduleConfigurationService) {
        this.moduleConfigurationService = moduleConfigurationService;
    }

    protected boolean getGzipSiteMapFilesDefault() {
        return BLCSystemProperty.resolveBooleanSystemProperty("sitemap.gzip.files");
    }

    public boolean getCreateSiteMapIfNotFound() {
        return BLCSystemProperty.resolveBooleanSystemProperty("sitemap.createIfNotFound");
    }

    public boolean getAutoGenerateSiteMapAfterTimeout() {
        return BLCSystemProperty.resolveBooleanSystemProperty("sitemap.createIfTimeoutExpired", false);
    }

    public long getOnDemandGenerationMinIntervalInMillis() {
        return BLCSystemProperty.resolveLongSystemProperty("sitemap.onDemandGeneration.minIntervalSeconds", 60L) * 1000;
    }

    public Long getSiteMapTimeoutInMillis() {
        Long cacheSeconds = BLCSystemProperty.resolveLongSystemProperty("sitemap.cache.seconds");
        return cacheSeconds * 1000;
    }

    public boolean getGzipSiteMapFiles() {
        if (this.gzipSiteMapFiles != null) {
            return this.gzipSiteMapFiles.booleanValue();
        } else {
            return getGzipSiteMapFilesDefault();
        }
    }

    public void setGzipSiteMapFiles(Boolean gzipSiteMapFiles) {
        this.gzipSiteMapFiles = gzipSiteMapFiles;
    }

}
