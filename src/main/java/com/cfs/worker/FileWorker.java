package com.cfs.worker;

import com.cfs.queue.BoundedWorkQueue;
import com.cfs.traversal.FileTask;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

// Consumer thread: pulls files from the queue one at a time and applies the given action.
// A file whose action fails is counted, since it drops out of the results entirely.
public class FileWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger(FileWorker.class.getName());

    private final BoundedWorkQueue workQueue;
    private final FileAction action;
    private final AtomicInteger failureCount;

    public FileWorker(BoundedWorkQueue workQueue, FileAction action, AtomicInteger failureCount) {
        this.workQueue = workQueue;
        this.action = action;
        this.failureCount = failureCount;
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
        try {
            action.apply(task);
        } catch (NoSuchFileException e) {
            // file was deleted after traversal listed it
            failureCount.incrementAndGet();
        } catch (IOException e) {
            LOG.warning("Failed to read " + task.path() + ": " + e.getMessage());
            failureCount.incrementAndGet();
        }
    }
}
