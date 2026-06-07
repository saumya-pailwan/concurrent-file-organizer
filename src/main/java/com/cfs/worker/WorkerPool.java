package com.cfs.worker;

import com.cfs.cache.LRUSegmentCache;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.RollingHasher;
import com.cfs.queue.BoundedWorkQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

// Fixed thread pool of FileWorkers with named threads and crash logging.
public class WorkerPool {

    private static final Logger LOG = Logger.getLogger(WorkerPool.class.getName());

    private final ExecutorService executor;
    private final int numWorkers;

    public WorkerPool(int numWorkers,
                      BoundedWorkQueue workQueue,
                      RollingHasher hasher,
                      LRUSegmentCache cache,
                      DuplicateRegistry registry) {
        this.numWorkers = numWorkers;
        this.executor = Executors.newFixedThreadPool(numWorkers, r -> {
            Thread t = new Thread(r);
            t.setName("cfs-worker-" + t.getId());
            t.setUncaughtExceptionHandler((thread, ex) ->
                    LOG.severe("Worker " + thread.getName() + " died unexpectedly: " + ex));
            return t;
        });

        for (int i = 0; i < numWorkers; i++) {
            executor.submit(new FileWorker(workQueue, hasher, cache, registry));
        }
    }

    /** Signals no more tasks will be submitted and waits for all workers to finish. */
    public void awaitTermination(long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                LOG.warning("Worker pool did not terminate within " + timeoutSeconds + "s; forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public int numWorkers() {
        return numWorkers;
    }
}
