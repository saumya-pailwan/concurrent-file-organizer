package com.cfs.config;

import java.nio.file.Path;

public final class Config {
    private Config() {}

    public static final int NUM_WORKER_THREADS =
            Runtime.getRuntime().availableProcessors() * 2;

    // max file tasks in flight between BFSTraverser and workers
    public static final int QUEUE_CAPACITY = 1000;

    // rolling hash chunk size in bytes (4KB)
    public static final int HASH_WINDOW_SIZE = 4096;

    // number of LRU cache shards (must be power of 2)
    public static final int NUM_CACHE_SEGMENTS = 16;

    // entries per segment → total cache = 16 * 512 = 8192 metadata entries
    public static final int CACHE_SEGMENT_SIZE = 512;

    // REPORT | MOVE | DELETE
    public static final String DUPLICATE_POLICY = "REPORT";

    public static final Path OUTPUT_DIR = Path.of("./cfs-output");

    // graceful shutdown timeout (seconds)
    public static final long SHUTDOWN_TIMEOUT_SECONDS = 60;
}
