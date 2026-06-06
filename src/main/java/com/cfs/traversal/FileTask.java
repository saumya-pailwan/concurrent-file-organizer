package com.cfs.traversal;

import java.nio.file.Path;

public record FileTask(Path path, long sizeBytes, long lastModifiedMs) {

    // Sentinel value: workers shut down when they receive this
    public static final FileTask POISON_PILL = new FileTask(null, -1L, -1L);

    public boolean isPoisonPill() {
        return path == null;
    }
}
