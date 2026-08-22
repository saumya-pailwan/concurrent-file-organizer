package com.cfs.hash;

import java.nio.file.Path;

public record FileEntry(Path path, long sizeBytes, String sha256) {}
