package com.pradeep.dbdemo.wal;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.HeapPage;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.RID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryManagerTest {
    private Path dbFile;
    private Path walFile;
    private DiskManager diskManager;
    private WalManager walManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("recovery-db-", ".db");
        walFile = Files.createTempFile("recovery-wal-", ".wal");
        Files.deleteIfExists(walFile);
        diskManager = new DiskManager(dbFile);
        walManager = WalManager.forFile(walFile);
        bufferPool = new BufferPool(diskManager, 4, walManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        try { walManager.close(); } catch (Exception ignored) {}
        try { diskManager.close(); } catch (Exception ignored) {}
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(walFile);
    }

    // Mimic a process crash after WAL is durable but before dirty pages were flushed.
    private void simulateCrashKeepingWalIntact() throws Exception {
        walManager.close();       // ensure WAL bytes are on disk
        diskManager.close();      // drop dirty in-memory pages
        diskManager = new DiskManager(dbFile);
    }

    // Read the tuple bytes at the highest offset in a page (matches HeapPage.insert's placement of the first tuple).
    private static byte[] readLastTupleBytes(Page page, int length) {
        byte[] out = new byte[length];
        System.arraycopy(page.getData(), Page.PAGE_SIZE - length, out, 0, length);
        return out;
    }

    @Test
    void singleUpdateRecoveryReflectsTheChange() throws Exception {
        int pageId = bufferPool.allocatePage();
        HeapPage heap = new HeapPage(bufferPool.fetchPage(pageId), bufferPool);
        byte[] tuple = "hello".getBytes(StandardCharsets.UTF_8);
        heap.insert(tuple);

        long stampedLsn = bufferPool.fetchPage(pageId).getPageHeader().getPageLSN();

        simulateCrashKeepingWalIntact();

        // pre-recovery: page on disk has no data
        Page before = diskManager.readPage(pageId);
        assertEquals(0, before.getPageHeader().getSlotCount());
        assertEquals(0, before.getPageHeader().getPageLSN());

        RecoveryManager.RecoveryStats stats =
                new RecoveryManager(walFile, diskManager).recover();

        assertEquals(1, stats.applied());
        assertEquals(0, stats.skipped());

        Page after = diskManager.readPage(pageId);
        assertEquals(stampedLsn, after.getPageHeader().getPageLSN());
        assertEquals(1, after.getPageHeader().getSlotCount());
        assertArrayEquals(tuple, readLastTupleBytes(after, tuple.length));
    }

    @Test
    void alreadyAppliedRecordIsSkipped() throws Exception {
        int pageId = bufferPool.allocatePage();
        HeapPage heap = new HeapPage(bufferPool.fetchPage(pageId), bufferPool);
        heap.insert("data".getBytes(StandardCharsets.UTF_8));

        // both WAL and page make it to disk — page.pageLSN now matches WAL's last LSN
        bufferPool.flushAll();
        walManager.close();
        diskManager.close();
        diskManager = new DiskManager(dbFile);

        byte[] bytesBefore = diskManager.readPage(pageId).getData().clone();

        RecoveryManager.RecoveryStats stats =
                new RecoveryManager(walFile, diskManager).recover();

        assertEquals(0, stats.applied());
        assertTrue(stats.skipped() >= 1);

        byte[] bytesAfter = diskManager.readPage(pageId).getData().clone();
        assertArrayEquals(bytesBefore, bytesAfter);
    }

    @Test
    void multipleSequentialRecordsRecoveredInOrder() throws Exception {
        int pageId = bufferPool.allocatePage();
        HeapPage heap = new HeapPage(bufferPool.fetchPage(pageId), bufferPool);

        byte[] t1 = "aa".getBytes(StandardCharsets.UTF_8);
        byte[] t2 = "bb".getBytes(StandardCharsets.UTF_8);
        byte[] t3 = "cc".getBytes(StandardCharsets.UTF_8);

        heap.insert(t1);
        heap.insert(t2);
        heap.insert(t3);

        long finalLsn = bufferPool.fetchPage(pageId).getPageHeader().getPageLSN();

        simulateCrashKeepingWalIntact();

        RecoveryManager.RecoveryStats stats =
                new RecoveryManager(walFile, diskManager).recover();

        assertEquals(3, stats.applied());
        assertEquals(0, stats.skipped());

        Page recovered = diskManager.readPage(pageId);
        assertEquals(finalLsn, recovered.getPageHeader().getPageLSN());
        assertEquals(3, recovered.getPageHeader().getSlotCount());

        // tuples stack downward: t1 at [end-2 .. end), t2 at [end-4 .. end-2), t3 at [end-6 .. end-4)
        byte[] out = new byte[6];
        System.arraycopy(recovered.getData(), Page.PAGE_SIZE - 6, out, 0, 6);
        assertArrayEquals(new byte[]{ 'c','c','b','b','a','a' }, out);
    }

    @Test
    void recoveryIsIdempotent() throws Exception {
        int pageId = bufferPool.allocatePage();
        HeapPage heap = new HeapPage(bufferPool.fetchPage(pageId), bufferPool);
        heap.insert("first".getBytes(StandardCharsets.UTF_8));
        heap.insert("second".getBytes(StandardCharsets.UTF_8));

        simulateCrashKeepingWalIntact();

        RecoveryManager.RecoveryStats firstRun =
                new RecoveryManager(walFile, diskManager).recover();
        byte[] afterFirst = diskManager.readPage(pageId).getData().clone();

        // simulate another restart (still no writes)
        diskManager.close();
        diskManager = new DiskManager(dbFile);

        RecoveryManager.RecoveryStats secondRun =
                new RecoveryManager(walFile, diskManager).recover();
        byte[] afterSecond = diskManager.readPage(pageId).getData().clone();

        assertEquals(2, firstRun.applied());
        assertEquals(0, secondRun.applied());
        assertTrue(secondRun.skipped() >= 2);
        assertArrayEquals(afterFirst, afterSecond);
    }

    @Test
    void multiplePagesEachRecoveredIndependently() throws Exception {
        int p1 = bufferPool.allocatePage();
        int p2 = bufferPool.allocatePage();

        HeapPage h1 = new HeapPage(bufferPool.fetchPage(p1), bufferPool);
        HeapPage h2 = new HeapPage(bufferPool.fetchPage(p2), bufferPool);

        h1.insert("page-one".getBytes(StandardCharsets.UTF_8));
        h2.insert("page-two".getBytes(StandardCharsets.UTF_8));

        long lsnP1 = bufferPool.fetchPage(p1).getPageHeader().getPageLSN();
        long lsnP2 = bufferPool.fetchPage(p2).getPageHeader().getPageLSN();

        // flush only p1 — it's already on disk. p2 remains dirty-in-memory only.
        bufferPool.flushPage(p1);

        simulateCrashKeepingWalIntact();

        // sanity: p1 already has the update; p2 does not.
        assertEquals(lsnP1, diskManager.readPage(p1).getPageHeader().getPageLSN());
        assertEquals(0, diskManager.readPage(p2).getPageHeader().getPageLSN());

        RecoveryManager.RecoveryStats stats =
                new RecoveryManager(walFile, diskManager).recover();

        assertEquals(1, stats.applied(), "only p2 should get replayed");
        assertEquals(1, stats.skipped(), "p1's record should be skipped because pageLSN already matches");

        Page after1 = diskManager.readPage(p1);
        Page after2 = diskManager.readPage(p2);

        assertEquals(lsnP1, after1.getPageHeader().getPageLSN());
        assertEquals(lsnP2, after2.getPageHeader().getPageLSN());

        assertArrayEquals("page-one".getBytes(StandardCharsets.UTF_8),
                readLastTupleBytes(after1, 8));
        assertArrayEquals("page-two".getBytes(StandardCharsets.UTF_8),
                readLastTupleBytes(after2, 8));
    }

    @Test
    void unsupportedOperationsThrowDuringDispatch() throws Exception {
        // craft a WAL record with an operation whose replay is intentionally a stub.
        int pageId = diskManager.allocatePage();  // extend file so recovery doesn't skip on pageId
        walManager.append(new WalRecord(WalOperation.DELETE_TUPLE, pageId, new byte[0]));

        simulateCrashKeepingWalIntact();

        // the page's pageLSN is 0, the record's LSN is 1, so dispatcher will actually be invoked.
        RecoveryManager rm = new RecoveryManager(walFile, diskManager);
        assertThrows(UnsupportedOperationException.class, rm::recover);
    }

    @Test
    void ridReturnedByInsertMatchesRecoveredSlotLayout() throws Exception {
        // sanity: after recovery, an RID captured at write time still reads the same tuple back.
        int pageId = bufferPool.allocatePage();
        HeapPage heap = new HeapPage(bufferPool.fetchPage(pageId), bufferPool);

        RID rid = heap.insert("greetings".getBytes(StandardCharsets.UTF_8));

        simulateCrashKeepingWalIntact();
        new RecoveryManager(walFile, diskManager).recover();

        // rebuild a HeapPage over the recovered page (fresh buffer pool with in-memory WAL, we don't care about that log)
        BufferPool readerPool = new BufferPool(diskManager, 4, WalManager.inMemory());
        Page recovered = readerPool.fetchPage(pageId);
        // tag the page as heap so HeapPage.read validates. Recovery leaves the type as EMPTY.
        recovered.getPageHeader().setPageType(PageHeader.PageType.HEAP);
        HeapPage recoveredHeap = new HeapPage(recovered, readerPool);
        assertArrayEquals("greetings".getBytes(StandardCharsets.UTF_8), recoveredHeap.read(rid));
    }
}