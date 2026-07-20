package com.pradeep.dbdemo.wal;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.Catalog;
import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.HeapPage;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.RID;
import com.pradeep.dbdemo.storage.btree.Btree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WalIntegrationTest {
    private Path dbFile;
    private Path walFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;
    private WalManager walManager;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("wal-db-", ".db");
        walFile = Files.createTempFile("wal-log-", ".wal");
        Files.deleteIfExists(walFile); // let WalManager.forFile create it fresh
        diskManager = new DiskManager(dbFile);
        walManager = WalManager.forFile(walFile);
        bufferPool = new BufferPool(diskManager, 8, walManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        walManager.close();
        diskManager.close();
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(walFile);
    }

    // --- Assignment integration tests -------------------------------------

    @Test
    void walRecordSerializationRoundTripsEveryField() {
        byte[] payload = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        WalRecord original = new WalRecord(42L, WalOperation.INSERT_TUPLE, 7, payload);

        byte[] bytes = original.serialize();
        WalRecord decoded = WalRecord.deserialize(ByteBuffer.wrap(bytes));

        assertEquals(original.getLsn(), decoded.getLsn());
        assertEquals(original.getOperation(), decoded.getOperation());
        assertEquals(original.getPageId(), decoded.getPageId());
        assertArrayEquals(original.getPayload(), decoded.getPayload());
    }

    @Test
    void multipleAppendsProduceMonotonicallyIncreasingLsns() throws Exception {
        long prev = 0;
        for (int i = 0; i < 20; i++) {
            long lsn = walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, i, new byte[0]));
            assertTrue(lsn > prev, "lsn " + lsn + " should be greater than previous " + prev);
            prev = lsn;
        }
    }

    @Test
    void recordsAreAppendedInOrderToTheWalFile() throws Exception {
        byte[] p1 = "one".getBytes(StandardCharsets.UTF_8);
        byte[] p2 = "two".getBytes(StandardCharsets.UTF_8);
        byte[] p3 = "three".getBytes(StandardCharsets.UTF_8);

        long l1 = walManager.append(new WalRecord(WalOperation.INSERT_TUPLE, 1, p1));
        long l2 = walManager.append(new WalRecord(WalOperation.DELETE_TUPLE, 2, p2));
        long l3 = walManager.append(new WalRecord(WalOperation.UPDATE_TUPLE, 3, p3));

        walManager.flush();

        List<WalRecord> log = walManager.readAll();

        assertEquals(3, log.size());

        assertEquals(l1, log.get(0).getLsn());
        assertEquals(WalOperation.INSERT_TUPLE, log.get(0).getOperation());
        assertEquals(1, log.get(0).getPageId());
        assertArrayEquals(p1, log.get(0).getPayload());

        assertEquals(l2, log.get(1).getLsn());
        assertEquals(WalOperation.DELETE_TUPLE, log.get(1).getOperation());

        assertEquals(l3, log.get(2).getLsn());
        assertEquals(WalOperation.UPDATE_TUPLE, log.get(2).getOperation());
    }

    @Test
    void aPageModificationAppendsAWalRecordBeforeStampingPageLsn() throws Exception {
        int pageId = bufferPool.allocatePage();
        Page page = bufferPool.fetchPage(pageId);
        HeapPage heap = new HeapPage(page, bufferPool);

        long lsnBefore = walManager.peekNextLsn();
        heap.insert("payload".getBytes(StandardCharsets.UTF_8));
        long stampedLsn = page.getPageHeader().getPageLSN();

        // the stamped LSN must equal exactly the one that WAL was expecting to hand out next.
        assertEquals(lsnBefore, stampedLsn);

        // and the last record in the file must carry that same LSN and the operation type.
        walManager.flush();
        List<WalRecord> log = walManager.readAll();
        WalRecord last = log.get(log.size() - 1);
        assertEquals(stampedLsn, last.getLsn());
        assertEquals(WalOperation.INSERT_TUPLE, last.getOperation());
        assertEquals(pageId, last.getPageId());
    }

    @Test
    void readingTheWalFileYieldsTheSameSequenceThatWasWritten() throws Exception {
        // catalog claims page 0 — must be created first, before anything else allocates a page.
        Catalog catalog = Catalog.createFresh(bufferPool);
        Btree tree = new Btree(bufferPool, catalog, "primary");
        for (int i = 0; i < 3; i++) tree.insert(i, new RID(i, (short) i));

        int heapPageId = bufferPool.allocatePage();
        HeapPage heap = new HeapPage(bufferPool.fetchPage(heapPageId), bufferPool);
        heap.insert("alpha".getBytes(StandardCharsets.UTF_8));
        RID rid = heap.insert("beta".getBytes(StandardCharsets.UTF_8));
        heap.delete(rid);
        heap.compact();

        // record what append handed out to us — the LSN sequence should exactly match readAll().
        walManager.flush();
        List<WalRecord> log = walManager.readAll();
        assertFalse(log.isEmpty());

        long expected = 1;
        for (WalRecord r : log) {
            assertEquals(expected, r.getLsn());
            expected++;
        }
    }

    // --- Sanity checks retained from the prior chapter ---------------------

    @Test
    void pageLsnSurvivesFlushToDisk() throws Exception {
        int pageId = bufferPool.allocatePage();
        Page page = bufferPool.fetchPage(pageId);
        HeapPage heap = new HeapPage(page, bufferPool);

        heap.insert("payload".getBytes(StandardCharsets.UTF_8));
        long stampedLsn = page.getPageHeader().getPageLSN();

        bufferPool.flushAll();

        // reopen just the disk (WAL stays where it is)
        diskManager.close();
        diskManager = new DiskManager(dbFile);

        Page onDisk = diskManager.readPage(pageId);
        assertEquals(stampedLsn, onDisk.getPageHeader().getPageLSN());
    }
}