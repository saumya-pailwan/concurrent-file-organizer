package com.cfs.hash;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

// Hashes a file in fixed-size chunks (Rabin-Karp) and computes a full SHA-256 for collision-free verification.
public class RollingHasher {

    private static final long BASE = 31L;
    private static final long MOD = 1_000_000_007L;

    private final int windowSize;

    public RollingHasher(int windowSize) {
        this.windowSize = windowSize;
    }

    /** Returns chunk hashes + SHA-256. Empty chunk list means the file was empty. */
    public HashResult hash(Path file) throws IOException {
        List<Long> chunkHashes = new ArrayList<>();
        MessageDigest sha256 = newSha256();

        try (InputStream raw = Files.newInputStream(file);
             BufferedInputStream in = new BufferedInputStream(raw, windowSize * 2)) {

            byte[] window = new byte[windowSize];
            int bytesRead;

            while ((bytesRead = readFully(in, window)) > 0) {
                long chunkHash = computeHash(window, bytesRead);
                chunkHashes.add(chunkHash);
                sha256.update(window, 0, bytesRead);
            }
        }

        String digest = HexFormat.of().formatHex(sha256.digest());
        return new HashResult(chunkHashes, digest);
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

    public record HashResult(List<Long> chunkHashes, String sha256) {
        /** Two files are duplicate candidates if their chunk-hash sequences match. */
        public boolean candidateMatchesWith(HashResult other) {
            return this.chunkHashes.equals(other.chunkHashes);
        }

        /** True duplicates share the same SHA-256. */
        public boolean confirmedMatchesWith(HashResult other) {
            return this.sha256.equals(other.sha256);
        }
    }
}
