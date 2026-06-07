package com.cfs.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RollingHasherTest {

    private final RollingHasher hasher = new RollingHasher(4096);

    @Test
    void sameContentProducesSameHash(@TempDir Path tmp) throws IOException {
        byte[] data = "hello world, this is a test".getBytes();
        Path a = tmp.resolve("a.txt");
        Path b = tmp.resolve("b.txt");
        Files.write(a, data);
        Files.write(b, data);

        RollingHasher.HashResult ra = hasher.hash(a);
        RollingHasher.HashResult rb = hasher.hash(b);

        assertEquals(ra.chunkHashes(), rb.chunkHashes(), "chunk hashes must match for identical content");
        assertEquals(ra.sha256(), rb.sha256(), "SHA-256 must match for identical content");
        assertTrue(ra.candidateMatchesWith(rb));
        assertTrue(ra.confirmedMatchesWith(rb));
    }

    @Test
    void oneByteDifferenceProducesDifferentHash(@TempDir Path tmp) throws IOException {
        byte[] data1 = "hello world".getBytes();
        byte[] data2 = "hello World".getBytes(); // capital W

        Path a = tmp.resolve("a.txt");
        Path b = tmp.resolve("b.txt");
        Files.write(a, data1);
        Files.write(b, data2);

        RollingHasher.HashResult ra = hasher.hash(a);
        RollingHasher.HashResult rb = hasher.hash(b);

        assertNotEquals(ra.sha256(), rb.sha256(), "SHA-256 must differ for different content");
    }

    @Test
    void multipleChunksForLargeFile(@TempDir Path tmp) throws IOException {
        // Write 12KB of data → 3 chunks of 4KB
        byte[] data = new byte[12 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 127);
        Path file = tmp.resolve("large.bin");
        Files.write(file, data);

        RollingHasher.HashResult result = hasher.hash(file);

        assertEquals(3, result.chunkHashes().size(), "12KB file should produce 3 chunks at 4KB window");
    }

    @Test
    void emptyFileProducesEmptyChunkList(@TempDir Path tmp) throws IOException {
        Path empty = tmp.resolve("empty.txt");
        Files.write(empty, new byte[0]);

        RollingHasher.HashResult result = hasher.hash(empty);

        assertTrue(result.chunkHashes().isEmpty(), "empty file should have no chunk hashes");
    }
}
