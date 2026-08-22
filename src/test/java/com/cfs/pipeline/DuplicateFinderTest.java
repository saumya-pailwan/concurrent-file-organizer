package com.cfs.pipeline;

import com.cfs.cache.LRUSegmentCache;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.FileEntry;
import com.cfs.hash.RollingHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateFinderTest {

    private static final int WORKERS = 4;
    private static final int PARTIAL_BYTES = 4096;

    private LRUSegmentCache cache;
    private DuplicateRegistry registry;

    @Test
    void detectsKnownDuplicates(@TempDir Path root) throws IOException {
        byte[] content = "duplicate content here".getBytes();
        Files.createDirectories(root.resolve("dirA"));
        Files.createDirectories(root.resolve("dirB"));

        Path dup1 = root.resolve("dirA/file1.txt");
        Path dup2 = root.resolve("dirB/file2.txt");
        Files.write(dup1, content);
        Files.write(dup2, content);
        Files.write(root.resolve("dirA/unique.txt"), "this is unique".getBytes());

        ScanStats stats = run(root);
        Map<String, List<FileEntry>> duplicates = registry.getDuplicates();

        assertEquals(1, duplicates.size(), "exactly one duplicate group");
        List<Path> paths = duplicates.values().iterator().next().stream()
                .map(FileEntry::path).toList();
        assertTrue(paths.contains(dup1));
        assertTrue(paths.contains(dup2));
        assertEquals(2, stats.duplicateFiles());
    }

    @Test
    void noFalsePositivesForDifferentContent(@TempDir Path root) throws IOException {
        Files.write(root.resolve("a.txt"), "apple pie".getBytes());
        Files.write(root.resolve("b.txt"), "banana".getBytes());
        Files.write(root.resolve("c.txt"), "cherry tart!".getBytes());

        run(root);

        assertTrue(registry.getDuplicates().isEmpty());
    }

    @Test
    void handlesNestedDirectories(@TempDir Path root) throws IOException {
        byte[] content = "same content everywhere".getBytes();
        Files.createDirectories(root.resolve("a/b/c/d"));
        Files.write(root.resolve("a/b/file.txt"), content);
        Files.write(root.resolve("a/b/c/d/file.txt"), content);

        run(root);

        assertEquals(1, registry.getDuplicates().size());
    }

    // --- tier behavior ---

    @Test
    void fileWithUniqueSizeNeverReachesTheHashTiers(@TempDir Path root) throws IOException {
        byte[] content = "identical".getBytes();
        Files.write(root.resolve("dup1.txt"), content);
        Files.write(root.resolve("dup2.txt"), content);
        Files.write(root.resolve("odd-size.txt"), "a much longer, differently sized file".getBytes());

        ScanStats stats = run(root);

        assertEquals(3, stats.totalFiles());
        assertEquals(2, stats.afterSizeFilter(), "the uniquely sized file must be filtered before hashing");
    }

    @Test
    void sameSizeDifferentContentIsEliminatedByPartialHash(@TempDir Path root) throws IOException {
        Files.write(root.resolve("a.txt"), "apple".getBytes());
        Files.write(root.resolve("b.txt"), "peach".getBytes()); // same length, different bytes

        ScanStats stats = run(root);

        assertEquals(2, stats.afterSizeFilter(), "same size, so both survive tier 1");
        assertEquals(0, stats.afterPartialFilter(), "differing prefixes must be dropped at tier 2");
        assertTrue(registry.getDuplicates().isEmpty());
    }

    @Test
    void sharedPrefixSurvivesPartialHashAndIsRejectedBySha256(@TempDir Path root) throws IOException {
        byte[] a = new byte[PARTIAL_BYTES * 2];
        byte[] b = new byte[PARTIAL_BYTES * 2];
        java.util.Arrays.fill(a, 0, PARTIAL_BYTES, (byte) 9);
        java.util.Arrays.fill(b, 0, PARTIAL_BYTES, (byte) 9);
        a[PARTIAL_BYTES] = 1;
        b[PARTIAL_BYTES] = 2;

        Files.write(root.resolve("a.bin"), a);
        Files.write(root.resolve("b.bin"), b);

        ScanStats stats = run(root);

        assertEquals(2, stats.afterSizeFilter());
        assertEquals(2, stats.afterPartialFilter(), "identical prefixes must survive tier 2");
        assertTrue(registry.getDuplicates().isEmpty(), "tier 3 must reject them");
        assertEquals(0, stats.duplicateFiles());
    }

    // --- empty files ---

    @Test
    void emptyFilesAreSkippedRatherThanReportedAsDuplicates(@TempDir Path root) throws IOException {
        Files.write(root.resolve("empty1.txt"), new byte[0]);
        Files.write(root.resolve("empty2.txt"), new byte[0]);
        byte[] content = "real content".getBytes();
        Files.write(root.resolve("dup1.txt"), content);
        Files.write(root.resolve("dup2.txt"), content);

        ScanStats stats = run(root);

        assertEquals(4, stats.totalFiles());
        assertEquals(2, stats.emptyFilesSkipped());
        assertEquals(1, registry.getDuplicates().size(), "only the non-empty pair is a duplicate group");
        assertEquals(2, stats.duplicateFiles());
    }

    // --- cache coverage ---

    @Test
    void cacheHoldsMetadataForEveryFileIncludingEmptyOnes(@TempDir Path root) throws IOException {
        Path empty = root.resolve("empty.txt");
        Path unique = root.resolve("unique.txt");
        Files.write(empty, new byte[0]);
        Files.write(unique, "only file of this size".getBytes());

        run(root);

        assertNotNull(cache.get(empty), "empty files must still be cached");
        assertNotNull(cache.get(unique), "size-filtered files must still be cached");
        assertEquals(2, cache.totalSize());
    }

    private ScanStats run(Path root) {
        cache = new LRUSegmentCache(16, 64);
        registry = new DuplicateRegistry();
        RollingHasher hasher = new RollingHasher(PARTIAL_BYTES);
        return new DuplicateFinder(WORKERS, hasher, cache, registry).find(root);
    }
}
