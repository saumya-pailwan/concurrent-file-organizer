package com.cfs.hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

// Thread-safe map of SHA-256 → files sharing that hash. getDuplicates() returns groups with >= 2 entries.
public class DuplicateRegistry {

    // key: full-file SHA-256 (collision-free; rolling hash used only for early grouping)
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<FileEntry>> map =
            new ConcurrentHashMap<>();

    public void register(FileEntry entry) {
        map.computeIfAbsent(entry.sha256(), k -> new CopyOnWriteArrayList<>())
           .add(entry);
    }

    /** Returns groups of files that are confirmed duplicates (same SHA-256, >= 2 files). */
    public Map<String, List<FileEntry>> getDuplicates() {
        return map.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ArrayList<>(e.getValue())
                ));
    }

    public int totalUniqueHashes() {
        return map.size();
    }

    public long totalFilesRegistered() {
        return map.values().stream().mapToLong(List::size).sum();
    }
}
