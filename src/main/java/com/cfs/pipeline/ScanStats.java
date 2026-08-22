package com.cfs.pipeline;

// How many files survived each tier of the funnel.
public record ScanStats(
        int totalFiles,          // every regular file traversal found
        int emptyFilesSkipped,   // size == 0, excluded before tier 1
        int afterSizeFilter,     // files sharing a size with at least one other file
        int afterPartialFilter,  // files sharing a size and a partial hash
        int duplicateFiles,      // files in confirmed duplicate groups
        int failedFiles          // could not be read; silently absent from the results
) {}
