package com.cfs.organizer;

import com.cfs.hash.FileEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

// Applies REPORT / MOVE / DELETE policy to discovered duplicate groups.
public class Organizer {

    private static final Logger LOG = Logger.getLogger(Organizer.class.getName());

    public enum Policy { REPORT, MOVE, DELETE }

    private final Policy policy;
    private final Path outputDir;

    public Organizer(Policy policy, Path outputDir) {
        this.policy = policy;
        this.outputDir = outputDir;
    }

    public void run(Map<String, List<FileEntry>> duplicates) {
        if (duplicates.isEmpty()) {
            System.out.println("No duplicates found.");
            return;
        }

        System.out.printf("Found %d duplicate group(s):%n", duplicates.size());

        int groupNum = 1;
        for (Map.Entry<String, List<FileEntry>> group : duplicates.entrySet()) {
            List<FileEntry> entries = group.getValue();
            // Sort by lastModified ascending: index 0 is the keeper (oldest = original)
            entries.sort(Comparator.comparingLong(FileEntry::sizeBytes)
                    .thenComparing(e -> e.path().toString()));

            System.out.printf("%n[Group %d] SHA-256: %s (%d files, %s bytes each)%n",
                    groupNum++, group.getKey(), entries.size(), entries.get(0).sizeBytes());

            for (int i = 0; i < entries.size(); i++) {
                FileEntry e = entries.get(i);
                String tag = (i == 0) ? " [KEEP]" : " [DUPLICATE]";
                System.out.printf("  %s%s%n", e.path(), tag);
            }

            if (policy != Policy.REPORT) {
                for (int i = 1; i < entries.size(); i++) {
                    actOn(entries.get(i));
                }
            }
        }

        printSummary(duplicates);
    }

    private void actOn(FileEntry entry) {
        switch (policy) {
            case MOVE -> moveTo(entry.path(), outputDir);
            case DELETE -> delete(entry.path());
            default -> { /* REPORT already printed above */ }
        }
    }

    private void moveTo(Path source, Path dir) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(source.getFileName());
            // avoid name collisions by appending a counter suffix
            int counter = 1;
            while (Files.exists(target)) {
                String name = source.getFileName().toString();
                int dot = name.lastIndexOf('.');
                target = dir.resolve(dot >= 0
                        ? name.substring(0, dot) + "_" + counter++ + name.substring(dot)
                        : name + "_" + counter++);
            }
            Files.move(source, target);
            LOG.info("Moved " + source + " → " + target);
        } catch (IOException e) {
            LOG.warning("Failed to move " + source + ": " + e.getMessage());
        }
    }

    private void delete(Path path) {
        try {
            Files.delete(path);
            LOG.info("Deleted " + path);
        } catch (IOException e) {
            LOG.warning("Failed to delete " + path + ": " + e.getMessage());
        }
    }

    private void printSummary(Map<String, List<FileEntry>> duplicates) {
        long wasted = duplicates.values().stream()
                .mapToLong(group -> {
                    long size = group.get(0).sizeBytes();
                    return size * (group.size() - 1);
                }).sum();
        System.out.printf("%nSummary: %d duplicate groups, %.2f MB wasted by duplicates.%n",
                duplicates.size(), wasted / (1024.0 * 1024.0));
    }
}
