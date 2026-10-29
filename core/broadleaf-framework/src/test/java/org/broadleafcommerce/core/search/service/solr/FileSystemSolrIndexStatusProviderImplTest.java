/*-
 * #%L
 * BroadleafCommerce Framework
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
package org.broadleafcommerce.core.search.service.solr;

import org.broadleafcommerce.core.search.service.solr.index.IndexStatusInfo;
import org.broadleafcommerce.core.search.service.solr.index.IndexStatusInfoImpl;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileSystemSolrIndexStatusProviderImplTest {

    private static final String VICTIM_CONTENT = "do-not-overwrite";

    private Path workDir;
    private FileSystemSolrIndexStatusProviderImpl provider;

    @Before
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("blc-solr-status-test-");
        provider = new FileSystemSolrIndexStatusProviderImpl();
        provider.searchService = EasyMock.createMock(SolrSearchServiceImpl.class);
        provider.solrConfiguration = new SolrConfiguration();
        provider.deadEventTTLSeconds = 165600;
        provider.deadEventPurgeCycleSeconds = 3600;
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(workDir);
        if (provider.privateStatusDirectory != null) {
            deleteRecursively(provider.privateStatusDirectory);
        }
    }

    @Test
    public void testDefaultStatusDirectoryIsPrivateAndNotSharedTemp() throws IOException {
        provider.handleUpdateIndexStatus(status(new Date()));

        Path statusDir = Paths.get(provider.getStatusDirectory((SolrSearchServiceImpl) provider.searchService));
        assertNotEquals(Paths.get(System.getProperty("java.io.tmpdir")).toRealPath(), statusDir.toRealPath());
        assertTrue(Files.isRegularFile(statusDir.resolve("solr_status.xml"), LinkOption.NOFOLLOW_LINKS));
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(statusDir)));
            assertEquals("rw-------", PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(statusDir.resolve("solr_status.xml"))));
        }
    }

    @Test
    public void testPrivateStatusDirectoryIsDeletedOnDestroy() throws IOException {
        provider.handleUpdateIndexStatus(status(new Date()));
        Path statusDir = provider.privateStatusDirectory;
        assertTrue(Files.isDirectory(statusDir));

        provider.deletePrivateStatusDirectory();

        assertFalse(Files.exists(statusDir, LinkOption.NOFOLLOW_LINKS));
        assertNull(provider.privateStatusDirectory);
    }

    @Test
    public void testConfiguredStatusDirectoryIsNotDeletedOnDestroy() throws IOException {
        provider.statusDirectoryPath = workDir.toString();
        provider.handleUpdateIndexStatus(status(new Date()));

        provider.deletePrivateStatusDirectory();

        assertTrue(Files.isRegularFile(workDir.resolve("solr_status.xml"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void testSymlinkInSharedTempIsNotFollowedByDefault() throws IOException {
        Path planted = Paths.get(System.getProperty("java.io.tmpdir"), "solr_status.xml");
        Assume.assumeFalse(Files.exists(planted, LinkOption.NOFOLLOW_LINKS));
        Path victim = victimFile();
        Files.createSymbolicLink(planted, victim);
        try {
            provider.handleUpdateIndexStatus(status(new Date()));
            assertEquals(VICTIM_CONTENT, new String(Files.readAllBytes(victim), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(planted);
        }
    }

    @Test
    public void testSymlinkAtConfiguredStatusPathIsReplacedNotFollowed() throws IOException {
        provider.statusDirectoryPath = workDir.toString();
        Path victim = victimFile();
        Path statusPath = workDir.resolve("solr_status.xml");
        Files.createSymbolicLink(statusPath, victim);

        provider.handleUpdateIndexStatus(status(new Date()));

        assertEquals(VICTIM_CONTENT, new String(Files.readAllBytes(victim), StandardCharsets.UTF_8));
        assertFalse(Files.isSymbolicLink(statusPath));
        assertTrue(new String(Files.readAllBytes(statusPath), StandardCharsets.UTF_8).contains("<status>"));
        try (Stream<Path> files = Files.list(workDir)) {
            assertEquals("no temp files left behind", 0, files.filter(f -> f.toString().endsWith(".tmp")).count());
        }
    }

    @Test
    public void testSymlinkedStatusFileIsNotRead() throws IOException {
        provider.statusDirectoryPath = workDir.toString();
        Path planted = Files.createTempFile("blc-planted-status-", ".xml");
        try {
            Files.write(planted, ("<status><index dateProcessed=\"2001-01-01T00:00:00+0000\"/></status>")
                    .getBytes(StandardCharsets.UTF_8));
            Files.createSymbolicLink(workDir.resolve("solr_status.xml"), planted);

            IndexStatusInfo read = provider.readIndexStatus(new IndexStatusInfoImpl());

            assertNull(read.getLastIndexDate());
        } finally {
            Files.deleteIfExists(planted);
        }
    }

    @Test
    public void testStatusRoundTrip() {
        provider.solrConfiguration.setSolrHomePath(workDir.toString());
        Date indexDate = new Date((System.currentTimeMillis() / 1000) * 1000);

        provider.handleUpdateIndexStatus(status(indexDate));
        IndexStatusInfo read = provider.readIndexStatus(new IndexStatusInfoImpl());

        assertEquals(indexDate, read.getLastIndexDate());
        assertEquals("v", read.getAdditionalInfo().get("k"));
        assertTrue(Files.isRegularFile(workDir.resolve("solr_status.xml"), LinkOption.NOFOLLOW_LINKS));
    }

    private IndexStatusInfo status(Date lastIndexDate) {
        IndexStatusInfo status = new IndexStatusInfoImpl();
        status.setLastIndexDate(lastIndexDate);
        status.getAdditionalInfo().put("k", "v");
        return status;
    }

    private Path victimFile() throws IOException {
        Path victim = workDir.resolve("victim.properties");
        Files.write(victim, VICTIM_CONTENT.getBytes(StandardCharsets.UTF_8));
        return victim;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
