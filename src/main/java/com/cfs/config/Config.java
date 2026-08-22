package com.cfs.config;

import java.nio.file.Path;

public final class Config {
    private Config() {}

    public static final int NUM_WORKER_THREADS =
            Runtime.getRuntime().availableProcessors() * 2;

    // max file tasks in flight between the feeder and the workers
    public static final int QUEUE_CAPACITY = 1000;

    // bytes read per file for the cheap partial-hash tier (4KB)
    public static final int PARTIAL_HASH_BYTES = 4096;

    // number of LRU cache shards
    public static final int NUM_CACHE_SEGMENTS = 16;

    // 8192 metadata entries
    public static final int CACHE_SEGMENT_SIZE = 512;

    public static final Path OUTPUT_DIR = Path.of("./cfs-output");

    // graceful shutdown timeout (seconds)
    public static final long SHUTDOWN_TIMEOUT_SECONDS = 60;
}
