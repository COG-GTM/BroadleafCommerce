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

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.ByteArrayResource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DefaultEhCacheUriProviderTest {

    private static final String MERGED = "<config>merged</config>";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void defaultUriIsInAFreshOwnerOnlyDirectory() throws Exception {
        DefaultEhCacheUriProvider first = new DefaultEhCacheUriProvider();
        DefaultEhCacheUriProvider second = new DefaultEhCacheUriProvider();

        Path firstFile = Paths.get(first.getJCacheUri());
        Path secondFile = Paths.get(second.getJCacheUri());

        assertEquals(firstFile, Paths.get(first.getJCacheUri()));
        assertNotEquals(firstFile.getParent(), secondFile.getParent());
        assertNotEquals(Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath(), firstFile.getParent());
        assertTrue(firstFile.getParent().getFileName().toString().startsWith("broadleaf-jcache"));
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(firstFile.getParent())));
        }

        File written = first.createTemporaryMergeXml(resource(MERGED));
        assertEquals(firstFile.toFile(), written);
        assertEquals(MERGED, new String(Files.readAllBytes(firstFile), StandardCharsets.UTF_8));
    }

    @Test
    public void ignoresPlantedFileAtTheLegacyFixedPath() throws Exception {
        Path legacy = Paths.get(System.getProperty("java.io.tmpdir"), DefaultEhCacheUriProvider.MERGED_CONFIG_FILE_NAME);
        Assume.assumeFalse(Files.exists(legacy, LinkOption.NOFOLLOW_LINKS));
        try {
            Files.write(legacy, "<attacker/>".getBytes(StandardCharsets.UTF_8));
            DefaultEhCacheUriProvider provider = new DefaultEhCacheUriProvider();
            provider.createTemporaryMergeXml(resource(MERGED));

            assertNotEquals(legacy, Paths.get(provider.getJCacheUri()));
            assertEquals("<attacker/>", new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8));
            assertEquals(MERGED, new String(Files.readAllBytes(Paths.get(provider.getJCacheUri())), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(legacy);
        }
    }

    @Test
    public void doesNotFollowSymlinkAtConfiguredLocation() throws Exception {
        Path victim = tmp.newFile("victim.txt").toPath();
        Files.write(victim, "precious".getBytes(StandardCharsets.UTF_8));
        Path target = tmp.getRoot().toPath().resolve(DefaultEhCacheUriProvider.MERGED_CONFIG_FILE_NAME);
        try {
            Files.createSymbolicLink(target, victim);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            Assume.assumeNoException(e);
        }

        DefaultEhCacheUriProvider provider = new DefaultEhCacheUriProvider();
        provider.cacheManagerUri = target.toUri();
        provider.createTemporaryMergeXml(resource(MERGED));

        assertEquals("precious", new String(Files.readAllBytes(victim), StandardCharsets.UTF_8));
        assertFalse(Files.isSymbolicLink(target));
        assertEquals(MERGED, new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }

    @Test
    public void replacesExistingFileAtConfiguredLocation() throws Exception {
        Path target = tmp.newFile(DefaultEhCacheUriProvider.MERGED_CONFIG_FILE_NAME).toPath();
        Files.write(target, "<stale/>".getBytes(StandardCharsets.UTF_8));

        DefaultEhCacheUriProvider provider = new DefaultEhCacheUriProvider();
        provider.cacheManagerUri = target.toUri();
        provider.createTemporaryMergeXml(resource(MERGED));

        assertEquals(MERGED, new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        try (java.util.stream.Stream<Path> files = Files.list(tmp.getRoot().toPath())) {
            assertEquals(1, files.count());
        }
    }

    private static ByteArrayResource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
