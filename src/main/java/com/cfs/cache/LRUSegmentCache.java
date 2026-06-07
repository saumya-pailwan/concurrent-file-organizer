package com.cfs.cache;

import java.nio.file.Path;

// Sharded LRU cache: keys routed to segments via bitmask to reduce lock contention.
public class LRUSegmentCache {

    private final CacheSegment[] segments;
    private final int segmentMask;

    public LRUSegmentCache(int numSegments, int capacityPerSegment) {
        if (Integer.bitCount(numSegments) != 1) {
            throw new IllegalArgumentException("numSegments must be a power of 2");
        }
        this.segments = new CacheSegment[numSegments];
        this.segmentMask = numSegments - 1;
        for (int i = 0; i < numSegments; i++) {
            segments[i] = new CacheSegment(capacityPerSegment);
        }
    }

    public FileMeta get(Path key) {
        return segmentFor(key).get(key);
    }

    public void put(Path key, FileMeta value) {
        segmentFor(key).put(key, value);
    }

    private CacheSegment segmentFor(Path key) {
        int h = key.hashCode();
        // spread high bits into low bits to reduce clustering
        h ^= (h >>> 16);
        return segments[h & segmentMask];
    }

    public int totalSize() {
        int total = 0;
        for (CacheSegment seg : segments) {
            total += seg.size();
        }
        return total;
    }
}
