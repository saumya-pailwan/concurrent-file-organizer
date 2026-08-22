package com.cfs.traversal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BFSTraverserTest {

    @Test
    void findsFilesAtEveryDepth(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("a/b/c"));
        Files.write(root.resolve("top.txt"), "top".getBytes());
        Files.write(root.resolve("a/mid.txt"), "mid".getBytes());
        Files.write(root.resolve("a/b/c/deep.txt"), "deep".getBytes());

        List<FileTask> files = new BFSTraverser(root).traverse();

        Set<String> names = files.stream()
                .map(t -> t.path().getFileName().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("top.txt", "mid.txt", "deep.txt"), names);
    }

    @Test
    void reportsFileSizes(@TempDir Path root) throws IOException {
        Files.write(root.resolve("five.txt"), "12345".getBytes());

        List<FileTask> files = new BFSTraverser(root).traverse();

        assertEquals(1, files.size());
        assertEquals(5, files.get(0).sizeBytes());
    }

    @Test
    void returnsEmptyListForEmptyDirectory(@TempDir Path root) {
        assertTrue(new BFSTraverser(root).traverse().isEmpty());
    }

    @Test
    void directoriesAreNotReturnedAsFiles(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("subdir"));
        Files.write(root.resolve("file.txt"), "x".getBytes());

        List<FileTask> files = new BFSTraverser(root).traverse();

        assertEquals(1, files.size());
        assertEquals("file.txt", files.get(0).path().getFileName().toString());
    }
}
