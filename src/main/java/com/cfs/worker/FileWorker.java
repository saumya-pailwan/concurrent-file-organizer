package com.cfs.worker;

import com.cfs.cache.FileMeta;
import com.cfs.cache.LRUSegmentCache;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.FileEntry;
import com.cfs.hash.RollingHasher;
import com.cfs.hash.RollingHasher.HashResult;
import com.cfs.queue.BoundedWorkQueue;
import com.cfs.traversal.FileTask;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.logging.Logger;

// Consumer thread: dequeues FileTasks, hashes each file, registers results in DuplicateRegistry.
public class FileWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger(FileWorker.class.getName());

    private final BoundedWorkQueue workQueue;
    private final RollingHasher hasher;
    private final LRUSegmentCache cache;
    private final DuplicateRegistry registry;

    public FileWorker(BoundedWorkQueue workQueue,
                      RollingHasher hasher,
                      LRUSegmentCache cache,
                      DuplicateRegistry registry) {
        this.workQueue = workQueue;
        this.hasher = hasher;
        this.cache = cache;
        this.registry = registry;
    }

    @Override
    public void run() {
        while (true) {
            FileTask task = workQueue.consume();
            if (task.isPoisonPill()) {
                break;
            }
            process(task);
        }
    }

    private void process(FileTask task) {
        // Populate the metadata cache if not already present
        FileMeta meta = cache.get(task.path());
        if (meta == null) {
            String ext = extensionOf(task.path().getFileName().toString());
            meta = new FileMeta(task.path(), task.sizeBytes(), task.lastModifiedMs(), ext);
            cache.put(task.path(), meta);
        }

        try {
            HashResult result = hasher.hash(task.path());
            if (result.chunkHashes().isEmpty()) {
                // empty file — skip
                return;
            }
            FileEntry entry = new FileEntry(
                    task.path(), task.sizeBytes(), result.chunkHashes(), result.sha256());
            registry.register(entry);
        } catch (NoSuchFileException e) {
            // file was deleted after it was enqueued — harmless, skip silently
        } catch (IOException e) {
            LOG.warning("Failed to hash " + task.path() + ": " + e.getMessage());
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
    }
}
