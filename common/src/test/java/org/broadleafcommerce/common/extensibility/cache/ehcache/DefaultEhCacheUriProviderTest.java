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

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.ByteArrayResource;

import java.io.File;
import java.io.IOException;
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
import static org.junit.Assert.fail;

public class DefaultEhCacheUriProviderTest {

    private static final String MERGED_XML = "<config xmlns='http://www.ehcache.org/v3'><cache alias='blStandardElements'/></config>";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DefaultEhCacheUriProvider provider;

    @Before
    public void setUp() {
        provider = new DefaultEhCacheUriProvider();
    }

    @After
    public void tearDown() {
        provider.destroy();
    }

    @Test
    public void mergedConfigUsesUnpredictableNameInPrivateDirectory() throws IOException {
        File merged = provider.createTemporaryMergeXml(resource());
        Path path = merged.toPath();

        assertEquals(merged.toURI(), provider.getJCacheUri());
        assertEquals(MERGED_XML, Files.readString(path));
        assertNotEquals("broadleaf-merged-jcache.xml", merged.getName());
        assertTrue(merged.getName().matches("broadleaf-merged-jcache-[0-9a-f]{32}\\.xml"));
        assertNotEquals(Paths.get(System.getProperty("java.io.tmpdir")).toRealPath(), path.getParent().toRealPath());
        assertTrue(path.getParent().getFileName().toString().startsWith("broadleaf-jcache-"));

        DefaultEhCacheUriProvider other = new DefaultEhCacheUriProvider();
        try {
            assertNotEquals(provider.getJCacheUri(), other.createTemporaryMergeXml(resource()).toURI());
        } finally {
            other.destroy();
        }
    }

    @Test
    public void mergedConfigIsOwnerOnly() throws IOException {
        assumePosix();
        Path path = provider.createTemporaryMergeXml(resource()).toPath();

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path.getParent())));
    }

    @Test
    public void legacyFixedPathIsNeitherWrittenThroughNorLoaded() throws IOException {
        Path victim = temporaryFolder.newFile("victim.txt").toPath();
        Files.writeString(victim, "untouched");
        Path legacy = temporaryFolder.getRoot().toPath().resolve("broadleaf-merged-jcache.xml");
        Files.createSymbolicLink(legacy, victim);

        String originalTmp = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporaryFolder.getRoot().getAbsolutePath());
        try {
            provider.createTemporaryMergeXml(resource());
        } finally {
            System.setProperty("java.io.tmpdir", originalTmp);
        }

        assertEquals("untouched", Files.readString(victim));
        assertTrue(Files.isSymbolicLink(legacy));
        assertNotEquals(legacy.toUri(), provider.getJCacheUri());
    }

    @Test
    public void existingFileOrSymlinkAtGeneratedNameIsNeverReused() throws IOException {
        Path directory = temporaryFolder.newFolder("private").toPath();
        Path victim = temporaryFolder.newFile("victim.xml").toPath();
        Files.writeString(victim, "untouched");
        Path planted = directory.resolve("broadleaf-merged-jcache-planted.xml");
        Files.createSymbolicLink(planted, victim);

        DefaultEhCacheUriProvider predictable = new DefaultEhCacheUriProvider() {
            int calls;

            @Override
            protected String randomToken() {
                return calls++ == 0 ? "planted" : "fresh";
            }
        };
        predictable.setMergedConfigLocation(directory.toString());
        try {
            File merged = predictable.createTemporaryMergeXml(resource());

            assertEquals(directory.resolve("broadleaf-merged-jcache-fresh.xml").toFile(), merged);
            assertEquals(MERGED_XML, Files.readString(merged.toPath()));
            assertEquals("untouched", Files.readString(victim));
            assertFalse(Files.isSymbolicLink(merged.toPath()));
        } finally {
            predictable.destroy();
        }
    }

    @Test
    public void explicitLocationIsCreatedOwnerOnlyAndUsed() throws IOException {
        Path location = temporaryFolder.getRoot().toPath().resolve("data/jcache");
        provider.setMergedConfigLocation(location.toString());

        File merged = provider.createTemporaryMergeXml(resource());

        assertEquals(location, merged.toPath().getParent());
        if (isPosix()) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(location)));
        }
        provider.destroy();
        assertFalse(Files.exists(merged.toPath()));
        assertFalse(Files.exists(location));
    }

    @Test
    public void explicitLocationThatIsWritableByOthersIsRejected() throws IOException {
        assumePosix();
        Path location = temporaryFolder.newFolder("shared").toPath();
        Files.setPosixFilePermissions(location, PosixFilePermissions.fromString("rwxrwxrwx"));
        provider.setMergedConfigLocation(location.toString());

        try {
            provider.createTemporaryMergeXml(resource());
            fail("A group/world writable directory must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("must not be writable by group or others"));
        }
        try (var entries = Files.list(location)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    public void explicitLocationThatIsSymlinkIsRejected() throws IOException {
        Path target = temporaryFolder.newFolder("target").toPath();
        Path link = temporaryFolder.getRoot().toPath().resolve("link");
        Files.createSymbolicLink(link, target);
        provider.setMergedConfigLocation(link.toString());

        try {
            provider.createTemporaryMergeXml(resource());
            fail("A symlinked directory must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not a symbolic link"));
        }
        try (var entries = Files.list(target)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    public void destroyRemovesGeneratedFileAndDirectory() throws IOException {
        Path path = provider.createTemporaryMergeXml(resource()).toPath();

        provider.destroy();

        assertFalse(Files.exists(path, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(path.getParent(), LinkOption.NOFOLLOW_LINKS));
    }

    private ByteArrayResource resource() {
        return new ByteArrayResource(MERGED_XML.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static void assumePosix() {
        Assume.assumeTrue(isPosix());
    }

}
