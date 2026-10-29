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
package org.broadleafcommerce.common.extensibility.cache.ehcache;

import org.apache.commons.io.IOUtils;
import org.broadleafcommerce.common.extensibility.cache.DefaultJCacheUriProvider;
import org.broadleafcommerce.common.extensibility.context.merge.MergeXmlConfigResource;
import org.broadleafcommerce.common.extensibility.context.merge.ResourceInputStream;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component("blJCacheUriProvider")
@ConditionalOnEhCache
public class DefaultEhCacheUriProvider extends DefaultJCacheUriProvider implements ApplicationContextAware, InitializingBean {

    @jakarta.annotation.Resource(name = "blMergedCacheConfigLocations")
    protected Set<String> mergedCacheConfigLocations;

    protected static final String MERGED_CONFIG_FILE_NAME = "broadleaf-merged-jcache.xml";

    /**
     * Location of the merged Ehcache configuration. When not set, a file inside a freshly created, owner-only
     * directory under {@code java.io.tmpdir} is used so that other local users cannot predict or pre-create it.
     */
    protected URI cacheManagerUri;

    protected List<Resource> configLocations;

    private ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() throws Exception {
        mergeCacheLocations();
    }

    @Override
    public synchronized URI getJCacheUri() {
        if (cacheManagerUri == null) {
            try {
                cacheManagerUri = createPrivateMergeDirectory().resolve(MERGED_CONFIG_FILE_NAME).toFile().toURI();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create a private directory for the merged JCache configuration", e);
            }
        }
        return cacheManagerUri;
    }

    protected Path createPrivateMergeDirectory() throws IOException {
        Path dir;
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            dir = Files.createTempDirectory("broadleaf-jcache", ownerOnly);
        } else {
            dir = Files.createTempDirectory("broadleaf-jcache");
        }
        // deleteOnExit runs in reverse registration order: the file is removed before its directory
        dir.toFile().deleteOnExit();
        dir.resolve(MERGED_CONFIG_FILE_NAME).toFile().deleteOnExit();
        return dir;
    }

    protected void mergeCacheLocations() throws IOException {
        List<Resource> resources = new ArrayList<>();
        if (mergedCacheConfigLocations != null && !mergedCacheConfigLocations.isEmpty()) {
            for (String location : mergedCacheConfigLocations) {
                resources.add(applicationContext.getResource(location));
            }
        }
        if (configLocations != null && !configLocations.isEmpty()) {
            resources.addAll(configLocations);
        }
        MergeXmlConfigResource merge = new MergeXmlConfigResource();
        ResourceInputStream[] sources = new ResourceInputStream[resources.size()];
        int j = 0;
        for (Resource resource : resources) {
            sources[j] = new ResourceInputStream(resource.getInputStream(), resource.getURL().toString());
            j++;
        }

        Resource mergeResource = merge.getMergedConfigResource(sources);
        createTemporaryMergeXml(mergeResource);
    }

    protected File createTemporaryMergeXml(Resource mergedJcacheResource) throws FileNotFoundException, IOException {
        Path target = Paths.get(getJCacheUri());
        Path parent = target.toAbsolutePath().getParent();
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(parent);
        }
        // Write to a uniquely named owner-only file (created with CREATE_NEW, so it never follows a planted link)
        // and atomically rename it over the target; a symlink at the target is replaced, not followed.
        Path staging = Files.createTempFile(parent, ".broadleaf-jcache-", ".xml");
        try {
            try (InputStream in = mergedJcacheResource.getInputStream();
                    OutputStream out = Files.newOutputStream(staging, StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                IOUtils.copy(in, out);
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
        return target.toFile();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setConfigLocations(List<Resource> configLocations) throws BeansException {
        this.configLocations = configLocations;
    }

}
