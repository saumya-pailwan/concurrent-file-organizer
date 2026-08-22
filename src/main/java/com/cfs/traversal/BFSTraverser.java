package com.cfs.traversal;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

// Walks the directory tree breadth-first and returns every regular file it finds.
// Runs to completion before hashing starts, because the size filter needs the whole list.
public class BFSTraverser {

    private static final Logger LOG = Logger.getLogger(BFSTraverser.class.getName());

    private final Path root;

    public BFSTraverser(Path root) {
        this.root = root;
    }

    public List<FileTask> traverse() {
        List<FileTask> files = new ArrayList<>();
        Queue<Path> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(root);

        while (!bfsQueue.isEmpty()) {
            Path dir = bfsQueue.poll();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path child : stream) {
                    if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        bfsQueue.add(child);
                    } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                        addFile(files, child);
                    }
                }
            } catch (AccessDeniedException e) {
                LOG.warning("Access denied, skipping: " + dir);
            } catch (IOException e) {
                LOG.warning("I/O error reading directory " + dir + ": " + e.getMessage());
            }
        }

        return files;
    }

    private void addFile(List<FileTask> files, Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            files.add(new FileTask(file, attrs.size(), attrs.lastModifiedTime().toMillis()));
        } catch (NoSuchFileException e) {
            // file vanished between directory listing and attribute read — skip silently
        } catch (IOException e) {
            LOG.warning("Cannot read attributes for " + file + ": " + e.getMessage());
        }
    }
}
