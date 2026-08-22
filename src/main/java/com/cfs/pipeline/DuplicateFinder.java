package com.cfs.pipeline;

import com.cfs.cache.FileMeta;
import com.cfs.cache.LRUSegmentCache;
import com.cfs.config.Config;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.FileEntry;
import com.cfs.hash.RollingHasher;
import com.cfs.queue.BoundedWorkQueue;
import com.cfs.traversal.BFSTraverser;
import com.cfs.traversal.FileTask;
import com.cfs.worker.FileAction;
import com.cfs.worker.WorkerPool;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateFinder {

    private final int numWorkers;
    private final RollingHasher hasher;
    private final LRUSegmentCache cache;
    private final DuplicateRegistry registry;

    public DuplicateFinder(int numWorkers,
                           RollingHasher hasher,
                           LRUSegmentCache cache,
                           DuplicateRegistry registry) {
        this.numWorkers = numWorkers;
        this.hasher = hasher;
        this.cache = cache;
        this.registry = registry;
    }

    public ScanStats find(Path root) {
        List<FileTask> all = new BFSTraverser(root).traverse();

        // Cache metadata for every file, empty ones included. Nothing here needs
        // the file contents, so it stays independent of the hashing tiers.
        for (FileTask task : all) {
            cache.put(task.path(), toMeta(task));
        }

        // Empty files are all byte-identical, so they would form one huge duplicate
        // group. Leave them out and report the count separately.
        List<FileTask> nonEmpty = new ArrayList<>();
        for (FileTask task : all) {
            if (task.sizeBytes() > 0) {
                nonEmpty.add(task);
            }
        }
        int emptyFilesSkipped = all.size() - nonEmpty.size();

        // Tier 1: a file with a unique size cannot have a duplicate.
        Map<Long, List<FileTask>> bySize = new HashMap<>();
        for (FileTask task : nonEmpty) {
            bySize.computeIfAbsent(task.sizeBytes(), k -> new ArrayList<>()).add(task);
        }
        List<FileTask> sizeCandidates = groupsWithAtLeastTwo(bySize);

        // Tier 2: hash only the first few KB. Size is part of the key because two
        // files of different sizes can still share a prefix hash.
        Map<PartialKey, List<FileTask>> byPartial = new ConcurrentHashMap<>();
        int failedPartial = runPhase(sizeCandidates, task -> {
            long partial = hasher.partialHash(task.path());
            byPartial.computeIfAbsent(new PartialKey(task.sizeBytes(), partial),
                            k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(task);
        });
        List<FileTask> partialCandidates = groupsWithAtLeastTwo(byPartial);

        // Tier 3: read the whole file and let SHA-256 decide.
        int failedFull = runPhase(partialCandidates, task ->
                registry.register(new FileEntry(
                        task.path(), task.sizeBytes(), hasher.fullHash(task.path()))));

        int duplicateFiles = registry.getDuplicates().values().stream()
                .mapToInt(List::size)
                .sum();

        return new ScanStats(
                all.size(),
                emptyFilesSkipped,
                sizeCandidates.size(),
                partialCandidates.size(),
                duplicateFiles,
                failedPartial + failedFull);
    }

    /**
     * Runs one hashing tier across the worker pool. Feeding from this thread is safe
     * because the workers are already draining, so a full queue just blocks the feeder.
     */
    private int runPhase(List<FileTask> files, FileAction action) {
        BoundedWorkQueue queue = new BoundedWorkQueue(Config.QUEUE_CAPACITY);
        WorkerPool pool = new WorkerPool(numWorkers, queue, action);

        for (FileTask task : files) {
            queue.produce(task);
        }
        for (int i = 0; i < numWorkers; i++) {
            queue.produce(FileTask.POISON_PILL);
        }

        pool.awaitTermination(Config.SHUTDOWN_TIMEOUT_SECONDS);
        return pool.failureCount();
    }

    // Flattens the groups that hold more than one file — the only possible duplicates
    private static <K> List<FileTask> groupsWithAtLeastTwo(Map<K, List<FileTask>> groups) {
        List<FileTask> candidates = new ArrayList<>();
        for (List<FileTask> group : groups.values()) {
            if (group.size() > 1) {
                candidates.addAll(group);
            }
        }
        return candidates;
    }

    private static FileMeta toMeta(FileTask task) {
        String filename = task.path().getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String extension = (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
        return new FileMeta(task.path(), task.sizeBytes(), task.lastModifiedMs(), extension);
    }

    private record PartialKey(long size, long partialHash) {}
}
