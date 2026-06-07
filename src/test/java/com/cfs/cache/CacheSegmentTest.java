package com.cfs.cache;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CacheSegmentTest {

    private static FileMeta meta(String p) {
        Path path = Path.of(p);
        return new FileMeta(path, 100L, 0L, "txt");
    }

    @Test
    void evictsEldestWhenFull() {
        CacheSegment seg = new CacheSegment(3);
        Path p1 = Path.of("/a");
        Path p2 = Path.of("/b");
        Path p3 = Path.of("/c");
        Path p4 = Path.of("/d");

        seg.put(p1, meta("/a"));
        seg.put(p2, meta("/b"));
        seg.put(p3, meta("/c"));

        // Access p1 so it becomes recently used; p2 becomes the eldest
        seg.get(p1);

        // Adding p4 should evict the least recently used (p2)
        seg.put(p4, meta("/d"));

        assertEquals(3, seg.size());
        assertNotNull(seg.get(p1), "p1 should still be present (recently accessed)");
        assertNull(seg.get(p2), "p2 should have been evicted (eldest unused)");
        assertNotNull(seg.get(p3), "p3 should still be present");
        assertNotNull(seg.get(p4), "p4 should be present (just inserted)");
    }

    @Test
    void concurrentPutsDoNotCorruptState() throws InterruptedException {
        CacheSegment seg = new CacheSegment(100);
        int threads = 8;
        int insertsPerThread = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < insertsPerThread; i++) {
                    Path p = Path.of("/thread" + tid + "/file" + i);
                    seg.put(p, meta(p.toString()));
                    seg.get(p); // interleave reads
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // Capacity is 100; we inserted 160 entries — size must be at most 100
        assertTrue(seg.size() <= 100, "Segment must not exceed capacity after concurrent puts");
    }

    @Test
    void getMissReturnsNull() {
        CacheSegment seg = new CacheSegment(10);
        assertNull(seg.get(Path.of("/nonexistent")));
    }
}
