package com.cfs.hash;

import java.nio.file.Path;
import java.util.List;

public record FileEntry(Path path, long sizeBytes, List<Long> chunkHashes, String sha256) {}
