package com.cfs;

import com.cfs.cache.LRUSegmentCache;
import com.cfs.config.Config;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.RollingHasher;
import com.cfs.organizer.Organizer;
import com.cfs.queue.BoundedWorkQueue;
import com.cfs.traversal.BFSTraverser;
import com.cfs.worker.WorkerPool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

// Usage: java -jar cfs.jar <rootDirectory> [REPORT|MOVE|DELETE]
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: cfs <rootDirectory> [REPORT|MOVE|DELETE]");
            System.exit(1);
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        Organizer.Policy policy = Organizer.Policy.REPORT;
        if (args.length >= 2) {
            try {
                policy = Organizer.Policy.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Unknown policy '" + args[1] + "'. Use REPORT, MOVE, or DELETE.");
                System.exit(1);
            }
        }

        System.out.printf("Scanning: %s%n", root);
        System.out.printf("Workers:  %d%n", Config.NUM_WORKER_THREADS);
        System.out.printf("Policy:   %s%n", policy);
        System.out.println();

        long startMs = System.currentTimeMillis();

        // Wire up the pipeline
        BoundedWorkQueue  workQueue = new BoundedWorkQueue(Config.QUEUE_CAPACITY);
        LRUSegmentCache   cache     = new LRUSegmentCache(Config.NUM_CACHE_SEGMENTS, Config.CACHE_SEGMENT_SIZE);
        DuplicateRegistry registry  = new DuplicateRegistry();
        RollingHasher     hasher    = new RollingHasher(Config.HASH_WINDOW_SIZE);

        // Start consumer workers before the producer so they are ready immediately
        WorkerPool workerPool = new WorkerPool(
                Config.NUM_WORKER_THREADS, workQueue, hasher, cache, registry);

        // Start BFS traversal (producer) on a dedicated thread
        Thread traverserThread = new Thread(
                new BFSTraverser(root, workQueue, Config.NUM_WORKER_THREADS),
                "cfs-bfs-traverser");
        traverserThread.start();

        // Wait for traversal to complete (traverser sends poison pills when done)
        traverserThread.join();

        // Wait for all workers to drain the queue and finish
        workerPool.awaitTermination(Config.SHUTDOWN_TIMEOUT_SECONDS);

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("Scan complete: %d files indexed, %d unique hashes in %.2fs%n",
                registry.totalFilesRegistered(),
                registry.totalUniqueHashes(),
                elapsedMs / 1000.0);
        System.out.printf("Cache size:    %d entries%n%n", cache.totalSize());

        // Apply organizer policy to the discovered duplicates
        Organizer organizer = new Organizer(policy, Config.OUTPUT_DIR);
        organizer.run(registry.getDuplicates());
    }
}
