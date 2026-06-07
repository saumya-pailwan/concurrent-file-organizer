package com.cfs.cache;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

// One LRU shard: LinkedHashMap(accessOrder=true) + ReentrantLock.
public class CacheSegment {

    private final int maxCapacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<Path, FileMeta> map;

    public CacheSegment(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.map = new LinkedHashMap<>(maxCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, FileMeta> eldest) {
                return size() > maxCapacity;
            }
        };
    }

    public FileMeta get(Path key) {
        lock.lock();
        try {
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void put(Path key, FileMeta value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }
}
