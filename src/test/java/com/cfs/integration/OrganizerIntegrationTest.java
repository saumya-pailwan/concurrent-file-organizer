package com.cfs.integration;

import com.cfs.cache.LRUSegmentCache;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.FileEntry;
import com.cfs.hash.RollingHasher;
import com.cfs.organizer.Organizer;
import com.cfs.queue.BoundedWorkQueue;
import com.cfs.traversal.BFSTraverser;
import com.cfs.worker.WorkerPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: creates a temp tree with known duplicates,
 * runs the full pipeline, and asserts the registry identifies exactly them.
 */
class OrganizerIntegrationTest {

    private static final int WORKERS = 4;

    @Test
    void detectsKnownDuplicates(@TempDir Path root) throws Exception {
        // Create two identical files and one unique file
        byte[] content = "duplicate content here".getBytes();
        byte[] unique = "this is unique".getBytes();

        Path dir1 = root.resolve("dirA");
        Path dir2 = root.resolve("dirB");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        Path dup1 = dir1.resolve("file1.txt");
        Path dup2 = dir2.resolve("file2.txt");
        Path uniq = dir1.resolve("unique.txt");

        Files.write(dup1, content);
        Files.write(dup2, content);
        Files.write(uniq, unique);

        DuplicateRegistry registry = runPipeline(root);
        Map<String, List<FileEntry>> duplicates = registry.getDuplicates();

        assertEquals(1, duplicates.size(), "Exactly one duplicate group should be found");

        List<FileEntry> group = duplicates.values().iterator().next();
        assertEquals(2, group.size(), "Duplicate group should contain exactly 2 files");

        List<Path> paths = group.stream().map(FileEntry::path).toList();
        assertTrue(paths.contains(dup1.toAbsolutePath()) || paths.contains(dup1));
        assertTrue(paths.contains(dup2.toAbsolutePath()) || paths.contains(dup2));

        assertEquals(3, registry.totalFilesRegistered(), "All 3 files should be indexed");
    }

    @Test
    void noFalsePositivesForDifferentContent(@TempDir Path root) throws Exception {
        Files.write(root.resolve("a.txt"), "apple".getBytes());
        Files.write(root.resolve("b.txt"), "banana".getBytes());
        Files.write(root.resolve("c.txt"), "cherry".getBytes());

        DuplicateRegistry registry = runPipeline(root);
        assertTrue(registry.getDuplicates().isEmpty(), "No duplicates should be found");
    }

    @Test
    void handlesNestedDirectories(@TempDir Path root) throws Exception {
        byte[] content = "same content everywhere".getBytes();
        Path deep = root.resolve("a/b/c/d");
        Files.createDirectories(deep);
        Files.write(root.resolve("a/b/file.txt"), content);
        Files.write(deep.resolve("file.txt"), content);

        DuplicateRegistry registry = runPipeline(root);
        assertEquals(1, registry.getDuplicates().size(), "Nested duplicates should be found");
    }

    private DuplicateRegistry runPipeline(Path root) throws Exception {
        BoundedWorkQueue workQueue = new BoundedWorkQueue(200);
        LRUSegmentCache cache = new LRUSegmentCache(16, 64);
        DuplicateRegistry registry = new DuplicateRegistry();
        RollingHasher hasher = new RollingHasher(4096);

        WorkerPool pool = new WorkerPool(WORKERS, workQueue, hasher, cache, registry);

        Thread traverser = new Thread(new BFSTraverser(root, workQueue, WORKERS), "test-bfs");
        traverser.start();
        traverser.join();
        pool.awaitTermination(30);

        return registry;
    }
}
