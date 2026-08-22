package com.cfs.hash;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Two hashes of increasing cost: a cheap Rabin-Karp hash over the first few KB,
// and a full SHA-256 that confirms a match.
public class RollingHasher {

    private static final long BASE = 31L;
    private static final long MOD = 1_000_000_007L;

    private final int partialBytes;

    public RollingHasher(int partialBytes) {
        this.partialBytes = partialBytes;
    }

    /**
     * Rabin-Karp hash of the first partialBytes bytes. Cheap filter: identical files
     * always share this hash, so a mismatch rules out a duplicate after one short read.
     */
    public long partialHash(Path file) throws IOException {
        byte[] window = new byte[partialBytes];
        try (InputStream in = Files.newInputStream(file)) {
            int bytesRead = readFully(in, window);
            return computeHash(window, bytesRead);
        }
    }

    /** SHA-256 of the whole file as lowercase hex. Confirms a duplicate beyond collision doubt. */
    public String fullHash(Path file) throws IOException {
        MessageDigest sha256 = newSha256();
        byte[] buffer = new byte[partialBytes];

        try (InputStream raw = Files.newInputStream(file);
             BufferedInputStream in = new BufferedInputStream(raw, partialBytes * 2)) {

            int bytesRead;
            while ((bytesRead = readFully(in, buffer)) > 0) {
                sha256.update(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(sha256.digest());
    }

    private long computeHash(byte[] data, int length) {
        long h = 0;
        for (int i = 0; i < length; i++) {
            h = (h * BASE + (data[i] & 0xFFL)) % MOD;
        }
        return h;
    }

    /** Reads up to buf.length bytes, returning actual bytes read (0 at EOF). */
    private int readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int n = in.read(buf, offset, buf.length - offset);
            if (n == -1) break;
            offset += n;
        }
        return offset;
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
