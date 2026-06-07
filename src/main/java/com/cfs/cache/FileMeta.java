package com.cfs.cache;

import java.nio.file.Path;

public record FileMeta(Path path, long sizeBytes, long lastModifiedMs, String mimeHint) {}
