package com.cfs.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RollingHasherTest {

    private static final int PARTIAL_BYTES = 4096;

    private final RollingHasher hasher = new RollingHasher(PARTIAL_BYTES);

    @Test
    void identicalFilesShareBothHashes(@TempDir Path tmp) throws IOException {
        byte[] data = "hello world, this is a test".getBytes();
        Path a = write(tmp, "a.txt", data);
        Path b = write(tmp, "b.txt", data);

        assertEquals(hasher.partialHash(a), hasher.partialHash(b));
        assertEquals(hasher.fullHash(a), hasher.fullHash(b));
    }

    @Test
    void oneByteDifferenceChangesBothHashes(@TempDir Path tmp) throws IOException {
        Path a = write(tmp, "a.txt", "hello world".getBytes());
        Path b = write(tmp, "b.txt", "hello World".getBytes()); // capital W

        assertNotEquals(hasher.partialHash(a), hasher.partialHash(b));
        assertNotEquals(hasher.fullHash(a), hasher.fullHash(b));
    }

    /**
     * The reason the two tiers exist: a shared prefix is enough to pass the cheap
     * filter, so only the full hash can actually decide.
     */
    @Test
    void sharedPrefixPassesPartialHashButFailsFullHash(@TempDir Path tmp) throws IOException {
        byte[] prefix = filled(PARTIAL_BYTES, (byte) 7);

        byte[] a = new byte[PARTIAL_BYTES * 2];
        byte[] b = new byte[PARTIAL_BYTES * 2];
        System.arraycopy(prefix, 0, a, 0, PARTIAL_BYTES);
        System.arraycopy(prefix, 0, b, 0, PARTIAL_BYTES);
        a[PARTIAL_BYTES] = 1;   // differ only after the partial window
        b[PARTIAL_BYTES] = 2;

        Path fileA = write(tmp, "a.bin", a);
        Path fileB = write(tmp, "b.bin", b);

        assertEquals(hasher.partialHash(fileA), hasher.partialHash(fileB),
                "identical first 4KB must produce the same partial hash");
        assertNotEquals(hasher.fullHash(fileA), hasher.fullHash(fileB),
                "differing tails must produce different SHA-256");
    }

    @Test
    void partialHashOnlyReadsUpToWindowSize(@TempDir Path tmp) throws IOException {
        byte[] prefix = filled(PARTIAL_BYTES, (byte) 3);

        byte[] shortFile = prefix.clone();
        byte[] longFile = new byte[PARTIAL_BYTES * 3];
        System.arraycopy(prefix, 0, longFile, 0, PARTIAL_BYTES);

        Path small = write(tmp, "small.bin", shortFile);
        Path large = write(tmp, "large.bin", longFile);

        assertEquals(hasher.partialHash(small), hasher.partialHash(large),
                "partial hash must ignore everything past the window");
    }

    @Test
    void fileShorterThanWindowIsHashedInFull(@TempDir Path tmp) throws IOException {
        Path a = write(tmp, "a.txt", "short".getBytes());
        Path b = write(tmp, "b.txt", "short".getBytes());
        Path c = write(tmp, "c.txt", "other".getBytes());

        assertEquals(hasher.partialHash(a), hasher.partialHash(b));
        assertNotEquals(hasher.partialHash(a), hasher.partialHash(c));
    }

    @Test
    void emptyFileHashesWithoutError(@TempDir Path tmp) throws IOException {
        Path empty = write(tmp, "empty.txt", new byte[0]);

        assertEquals(0L, hasher.partialHash(empty));
        // SHA-256 of the empty input
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                hasher.fullHash(empty));
    }

    private static Path write(Path dir, String name, byte[] content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static byte[] filled(int length, byte value) {
        byte[] data = new byte[length];
        java.util.Arrays.fill(data, value);
        return data;
    }
}
