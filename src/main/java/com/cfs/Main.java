package com.cfs;

import com.cfs.cache.LRUSegmentCache;
import com.cfs.config.Config;
import com.cfs.hash.DuplicateRegistry;
import com.cfs.hash.RollingHasher;
import com.cfs.organizer.Organizer;
import com.cfs.pipeline.DuplicateFinder;
import com.cfs.pipeline.ScanStats;

import java.nio.file.Files;
import java.nio.file.Path;

// Usage: java -jar cfs.jar <rootDirectory> [REPORT|MOVE|DELETE]
public class Main {

    public static void main(String[] args) {
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

        LRUSegmentCache cache = new LRUSegmentCache(Config.NUM_CACHE_SEGMENTS, Config.CACHE_SEGMENT_SIZE);
        DuplicateRegistry registry = new DuplicateRegistry();
        RollingHasher hasher = new RollingHasher(Config.PARTIAL_HASH_BYTES);

        DuplicateFinder finder = new DuplicateFinder(
                Config.NUM_WORKER_THREADS, hasher, cache, registry);
        ScanStats stats = finder.find(root);

        double elapsedSec = (System.currentTimeMillis() - startMs) / 1000.0;
        printStats(stats, elapsedSec);

        if (stats.failedFiles() > 0 && policy != Organizer.Policy.REPORT) {
            System.out.printf("WARNING: %d file(s) could not be read and were excluded from "
                    + "duplicate detection. %s will not consider them.%n%n",
                    stats.failedFiles(), policy);
        }

        new Organizer(policy, Config.OUTPUT_DIR).run(registry.getDuplicates());
    }

    // Each line shows how many files survived a tier, and what the tier saved.
    private static void printStats(ScanStats stats, double elapsedSec) {
        int skippedBySize = stats.totalFiles() - stats.emptyFilesSkipped() - stats.afterSizeFilter();
        int eliminatedByPartial = stats.afterSizeFilter() - stats.afterPartialFilter();

        System.out.printf("Files scanned:          %6d%n", stats.totalFiles());
        if (stats.emptyFilesSkipped() > 0) {
            System.out.printf("Empty files skipped:    %6d%n", stats.emptyFilesSkipped());
        }
        System.out.printf("Same-size candidates:   %6d   (%d skipped, never read)%n",
                stats.afterSizeFilter(), skippedBySize);
        System.out.printf("Same-prefix candidates: %6d   (%d eliminated after %d KB each)%n",
                stats.afterPartialFilter(), eliminatedByPartial, Config.PARTIAL_HASH_BYTES / 1024);
        System.out.printf("Confirmed duplicates:   %6d%n", stats.duplicateFiles());
        if (stats.failedFiles() > 0) {
            System.out.printf("Unreadable files:       %6d%n", stats.failedFiles());
        }
        System.out.printf("%nScan completed in %.2fs%n%n", elapsedSec);
    }
}
