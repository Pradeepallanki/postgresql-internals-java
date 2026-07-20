package com.pradeep.dbdemo.wal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WalBufferingTest {
    private Path walFile;
    private WalManager walManager;

    @BeforeEach
    void setup() throws Exception {
        walFile = Files.createTempFile("wal-buffering-", ".wal");
        Files.deleteIfExists(walFile);
        walManager = WalManager.forFile(walFile);
    }

    @AfterEach
    void cleanup() throws Exception {
        try { walManager.close(); } catch (Exception ignored) {}
        Files.deleteIfExists(walFile);
    }

    @Test
    void appendAdvancesInsertLsnButDoesNotTouchTheFile() throws Exception {
        long insertBefore = walManager.insertLsn();
        long flushBefore = walManager.flushLsn();
        long fileBefore = Files.size(walFile);

        long lsn = walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 1,
                "hi".getBytes(StandardCharsets.UTF_8)));

        assertEquals(insertBefore + 1, walManager.insertLsn());
        assertEquals(insertBefore + 1, lsn);
        assertEquals(flushBefore, walManager.flushLsn(), "flushLsn should not move on append");
        assertEquals(fileBefore, Files.size(walFile), "no bytes should have hit the file yet");
        assertTrue(walManager.bufferedBytes() > 0);
    }

    @Test
    void flushPersistsBufferedRecordsAndAdvancesFlushLsn() throws Exception {
        walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 1,
                "aaa".getBytes(StandardCharsets.UTF_8)));
        walManager.append(new WalRecord(WalOperation.DELETE_TUPLE, 1,
                "bbb".getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, walManager.flushLsn());
        assertEquals(2, walManager.insertLsn());
        assertEquals(0, Files.size(walFile));

        walManager.flush();

        assertEquals(2, walManager.flushLsn());
        assertEquals(2, walManager.insertLsn());
        assertTrue(Files.size(walFile) > 0, "file should now hold the flushed records");
        assertEquals(0, walManager.bufferedBytes(), "buffer must be empty after a successful flush");
    }

    @Test
    void multipleAppendsFollowedByOneFlushProduceASingleBatchedWrite() throws Exception {
        for (int i = 0; i < 5; i++) {
            walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, i,
                    ("payload-" + i).getBytes(StandardCharsets.UTF_8)));
        }

        // still nothing on disk
        assertEquals(0, Files.size(walFile));

        int expectedBytes = walManager.bufferedBytes();

        walManager.flush();

        // one write of exactly the buffered bytes' worth
        assertEquals(expectedBytes, Files.size(walFile));

        // and the records read back match what we appended, in order
        List<WalRecord> log = walManager.readAll();
        assertEquals(5, log.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, log.get(i).getLsn());
            assertEquals(i, log.get(i).getPageId());
            assertArrayEquals(("payload-" + i).getBytes(StandardCharsets.UTF_8),
                    log.get(i).getPayload());
        }
    }

    @Test
    void flushOnAnEmptyBufferIsANoOp() throws Exception {
        long fileBefore = Files.size(walFile);
        long insertBefore = walManager.insertLsn();
        long flushBefore = walManager.flushLsn();

        walManager.flush();

        assertEquals(fileBefore, Files.size(walFile));
        assertEquals(insertBefore, walManager.insertLsn());
        assertEquals(flushBefore, walManager.flushLsn());
        assertEquals(0, walManager.bufferedBytes());
    }

    @Test
    void bufferIsEmptyAfterSuccessfulFlush() throws Exception {
        walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 1, new byte[]{1, 2, 3}));
        walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 2, new byte[]{4, 5, 6}));

        assertTrue(walManager.bufferedBytes() > 0);

        walManager.flush();

        assertEquals(0, walManager.bufferedBytes());
    }

    @Test
    void secondFlushAfterAppendsOnlyWritesNewBytes() throws Exception {
        walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 1, new byte[]{1}));
        walManager.flush();
        long sizeAfterFirstFlush = Files.size(walFile);

        walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 2, new byte[]{2}));
        int pendingBytes = walManager.bufferedBytes();

        walManager.flush();

        assertEquals(sizeAfterFirstFlush + pendingBytes, Files.size(walFile));
        assertEquals(2, walManager.flushLsn());
    }

    @Test
    void closeFlushesRemainingRecords() throws Exception {
        walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 7, new byte[]{9, 9}));
        assertEquals(0, Files.size(walFile), "sanity: append is buffered, not written");

        walManager.close();
        // create a new reader to verify the on-disk contents
        WalManager reader = WalManager.forFile(walFile);
        try {
            List<WalRecord> log = reader.readAll();
            assertEquals(1, log.size());
            assertEquals(7, log.get(0).getPageId());
        } finally {
            reader.close();
        }
    }
}