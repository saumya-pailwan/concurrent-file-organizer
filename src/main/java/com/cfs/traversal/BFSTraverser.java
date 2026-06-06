package com.cfs.traversal;

import com.cfs.queue.BoundedWorkQueue;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Logger;

// BFS directory traverser (producer). Sends FileTask per file; poison pills on completion.
public class BFSTraverser implements Runnable {

    private static final Logger LOG = Logger.getLogger(BFSTraverser.class.getName());

    private final Path root;
    private final BoundedWorkQueue workQueue;
    private final int numWorkers;

    public BFSTraverser(Path root, BoundedWorkQueue workQueue, int numWorkers) {
        this.root = root;
        this.workQueue = workQueue;
        this.numWorkers = numWorkers;
    }

    @Override
    public void run() {
        Queue<Path> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(root);

        while (!bfsQueue.isEmpty()) {
            Path dir = bfsQueue.poll();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path child : stream) {
                    if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        bfsQueue.add(child);
                    } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                        enqueue(child);
                    }
                }
            } catch (AccessDeniedException e) {
                LOG.warning("Access denied, skipping: " + dir);
            } catch (IOException e) {
                LOG.warning("I/O error reading directory " + dir + ": " + e.getMessage());
            }
        }

        // Send one poison pill per worker so every worker eventually exits
        for (int i = 0; i < numWorkers; i++) {
            workQueue.produce(FileTask.POISON_PILL);
        }
    }

    private void enqueue(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            workQueue.produce(new FileTask(file, attrs.size(), attrs.lastModifiedTime().toMillis()));
        } catch (NoSuchFileException e) {
            // file vanished between directory listing and attribute read — skip silently
        } catch (IOException e) {
            LOG.warning("Cannot read attributes for " + file + ": " + e.getMessage());
        }
    }
}
