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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.extensibility.cache.DefaultJCacheUriProvider;
import org.broadleafcommerce.common.extensibility.context.merge.MergeXmlConfigResource;
import org.broadleafcommerce.common.extensibility.context.merge.ResourceInputStream;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Merges the {@code blMergedCacheConfigLocations} Ehcache XML files and writes the result to a file that is
 * only readable and writable by the JVM user. The file gets an unpredictable name inside a directory with
 * owner-only permissions, is created with {@code CREATE_NEW} so that a pre-existing file or symlink is never
 * reused or written through, and is removed on shutdown.
 * <p>
 * By default the directory is a fresh {@link Files#createTempDirectory} under {@code java.io.tmpdir}. Set
 * {@code broadleaf.jcache.merged.config.location} to keep it in an application-owned directory instead; that
 * directory must be owned by the JVM user and must not be writable by group or others.
 */
@Component("blJCacheUriProvider")
@ConditionalOnEhCache
public class DefaultEhCacheUriProvider extends DefaultJCacheUriProvider
        implements ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Log LOG = LogFactory.getLog(DefaultEhCacheUriProvider.class);

    protected static final String MERGED_FILE_PREFIX = "broadleaf-merged-jcache-";
    protected static final String MERGED_FILE_SUFFIX = ".xml";
    protected static final String TEMP_DIRECTORY_PREFIX = "broadleaf-jcache-";

    private static final SecureRandom RANDOM = new SecureRandom();

    @jakarta.annotation.Resource(name = "blMergedCacheConfigLocations")
    protected Set<String> mergedCacheConfigLocations;

    @Value("${broadleaf.jcache.merged.config.location:}")
    protected String mergedConfigLocation;

    protected URI cacheManagerUri;

    protected List<Resource> configLocations;

    protected Path mergedConfigDirectory;

    protected boolean mergedConfigDirectoryCreated;

    private ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() throws Exception {
        mergeCacheLocations();
    }

    @Override
    public void destroy() {
        if (cacheManagerUri != null) {
            deleteQuietly(Paths.get(cacheManagerUri));
        }
        if (mergedConfigDirectoryCreated && mergedConfigDirectory != null) {
            deleteQuietly(mergedConfigDirectory);
        }
    }

    @Override
    public URI getJCacheUri() {
        return cacheManagerUri;
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

    protected File createTemporaryMergeXml(Resource mergedJcacheResource) throws IOException {
        Path directory = resolveMergedConfigDirectory();
        for (int attempt = 0; attempt < 10; attempt++) {
            Path file = directory.resolve(MERGED_FILE_PREFIX + randomToken() + MERGED_FILE_SUFFIX);
            SeekableByteChannel channel;
            try {
                channel = newPrivateFileChannel(file);
            } catch (FileAlreadyExistsException e) {
                // never reuse an existing file or symlink
                continue;
            }
            try (OutputStream outputStream = Channels.newOutputStream(channel);
                 InputStream inputStream = mergedJcacheResource.getInputStream()) {
                IOUtils.copy(inputStream, outputStream);
            } catch (IOException | RuntimeException e) {
                deleteQuietly(file);
                throw e;
            }
            if (cacheManagerUri != null && !cacheManagerUri.equals(file.toUri())) {
                deleteQuietly(Paths.get(cacheManagerUri));
            }
            cacheManagerUri = file.toUri();
            return file.toFile();
        }
        throw new IOException("Unable to create a unique merged JCache configuration file in " + directory);
    }

    protected Path resolveMergedConfigDirectory() throws IOException {
        if (mergedConfigDirectory != null) {
            return mergedConfigDirectory;
        }
        Path directory;
        if (StringUtils.isNotBlank(mergedConfigLocation)) {
            directory = Paths.get(mergedConfigLocation.trim()).toAbsolutePath().normalize();
            if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Path parent = directory.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try {
                    if (isPosix()) {
                        Files.createDirectory(directory, permissions("rwx------"));
                    } else {
                        Files.createDirectory(directory);
                    }
                    mergedConfigDirectoryCreated = true;
                } catch (FileAlreadyExistsException e) {
                    // created concurrently; validated below
                }
            }
            validateMergedConfigDirectory(directory);
        } else {
            if (isPosix()) {
                directory = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX, permissions("rwx------"));
            } else {
                directory = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX);
            }
            mergedConfigDirectoryCreated = true;
        }
        mergedConfigDirectory = directory;
        return directory;
    }

    protected void validateMergedConfigDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("broadleaf.jcache.merged.config.location must be a directory and not a symbolic link: "
                    + directory);
        }
        if (!isPosix()) {
            return;
        }
        UserPrincipal owner = Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS);
        UserPrincipal currentUser = directory.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (!owner.equals(currentUser)) {
            throw new IOException("broadleaf.jcache.merged.config.location must be owned by " + currentUser.getName()
                    + " but is owned by " + owner.getName() + ": " + directory);
        }
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS);
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)
                || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            throw new IOException("broadleaf.jcache.merged.config.location must not be writable by group or others: "
                    + directory);
        }
    }

    protected SeekableByteChannel newPrivateFileChannel(Path file) throws IOException {
        Set<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        if (isPosix()) {
            return Files.newByteChannel(file, options, permissions("rw-------"));
        }
        return Files.newByteChannel(file, options);
    }

    protected String randomToken() {
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        return HexFormat.of().formatHex(random);
    }

    protected boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    protected FileAttribute<Set<PosixFilePermission>> permissions(String permissions) {
        return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions));
    }

    protected void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("Unable to delete merged JCache configuration path " + path, e);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setConfigLocations(List<Resource> configLocations) throws BeansException {
        this.configLocations = configLocations;
    }

    public void setMergedConfigLocation(String mergedConfigLocation) {
        this.mergedConfigLocation = mergedConfigLocation;
    }

}
