package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeapPageTest {
    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("mini", ".db");
        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void shouldInsertAndReadSingleTuple() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple =
                "John".getBytes(StandardCharsets.UTF_8);

        RID rid =
                heapPage.insert(tuple);

        diskManager.writePage(page);

        Page reloaded =
                diskManager.readPage(pageId);

        HeapPage reloadedHeap =
                new HeapPage(reloaded, bufferPool);

        byte[] result =
                reloadedHeap.read(rid);

        assertArrayEquals(tuple, result);
    }

    @Test
    void shouldReadCorrectTupleForEachRID() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple1 =
                "Alice".getBytes(StandardCharsets.UTF_8);

        byte[] tuple2 =
                "Bob".getBytes(StandardCharsets.UTF_8);

        byte[] tuple3 =
                "Charlie".getBytes(StandardCharsets.UTF_8);

        RID rid1 =
                heapPage.insert(tuple1);

        RID rid2 =
                heapPage.insert(tuple2);

        RID rid3 =
                heapPage.insert(tuple3);

        diskManager.writePage(page);

        Page reloaded =
                diskManager.readPage(pageId);

        HeapPage hp =
                new HeapPage(reloaded, bufferPool);

        assertArrayEquals(tuple1, hp.read(rid1));

        assertArrayEquals(tuple2, hp.read(rid2));

        assertArrayEquals(tuple3, hp.read(rid3));
    }

    @Test
    void shouldDeleteTuple() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple =
                "DeleteMe".getBytes(StandardCharsets.UTF_8);

        RID rid =
                heapPage.insert(tuple);

        heapPage.delete(rid);

        diskManager.writePage(page);

        Page reloaded =
                diskManager.readPage(pageId);

        HeapPage hp =
                new HeapPage(reloaded, bufferPool);

        assertNull(hp.read(rid));
    }

    @Test
    void shouldEventuallyRunOutOfSpace() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple = new byte[100];

        int inserted = 0;

        while (heapPage.hasSpace(tuple.length)) {

            heapPage.insert(tuple);

            inserted++;
        }

        assertFalse(heapPage.hasSpace(tuple.length));

        assertTrue(inserted > 0);
    }

    @Test
    void compactShouldReclaimSpaceOnlyAfterCompacting() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple = new byte[100];

        List<RID> rids = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            rids.add(heapPage.insert(tuple));
        }

        int beforeDelete = heapPage.getFreeBytes();

        for (int i = 0; i < 5; i++) {
            heapPage.delete(rids.get(i));
        }

        int afterDelete = heapPage.getFreeBytes();

        heapPage.compact();

        int afterCompact = heapPage.getFreeBytes();

        assertEquals(beforeDelete, afterDelete);

        assertTrue(afterCompact > afterDelete);
    }

    @Test
    void compactShouldPreserveLiveTuplesAndTombstoneDeletedOnes() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] alice = "Alice".getBytes(StandardCharsets.UTF_8);

        byte[] bob = "Bob".getBytes(StandardCharsets.UTF_8);

        byte[] charlie = "Charlie".getBytes(StandardCharsets.UTF_8);

        RID ridAlice = heapPage.insert(alice);

        RID ridBob = heapPage.insert(bob);

        RID ridCharlie = heapPage.insert(charlie);

        heapPage.delete(ridBob);

        heapPage.compact();

        assertArrayEquals(alice, heapPage.read(ridAlice));

        assertNull(heapPage.read(ridBob));

        assertArrayEquals(charlie, heapPage.read(ridCharlie));
    }

    @Test
    void compactShouldBeNoOpWhenNothingDeleted() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        RID r1 = heapPage.insert("A".getBytes(StandardCharsets.UTF_8));

        RID r2 = heapPage.insert("BB".getBytes(StandardCharsets.UTF_8));

        RID r3 = heapPage.insert("CCC".getBytes(StandardCharsets.UTF_8));

        int beforeFree = heapPage.getFreeBytes();

        heapPage.compact();

        int afterFree = heapPage.getFreeBytes();

        assertEquals(beforeFree, afterFree);

        assertArrayEquals("A".getBytes(StandardCharsets.UTF_8), heapPage.read(r1));

        assertArrayEquals("BB".getBytes(StandardCharsets.UTF_8), heapPage.read(r2));

        assertArrayEquals("CCC".getBytes(StandardCharsets.UTF_8), heapPage.read(r3));
    }

    @Test
    void compactShouldFreeEverythingWhenAllSlotsAreDeleted() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        RID r1 = heapPage.insert("X".getBytes(StandardCharsets.UTF_8));

        RID r2 = heapPage.insert("Y".getBytes(StandardCharsets.UTF_8));

        heapPage.delete(r1);

        heapPage.delete(r2);

        heapPage.compact();

        assertNull(heapPage.read(r1));

        assertNull(heapPage.read(r2));

        // freeSpaceOffSet should be back at PAGE_SIZE — the two tombstone slot entries are the only overhead
        assertEquals(Page.PAGE_SIZE - PageHeader.SIZE - (2 * Slot.SIZE), (int) heapPage.getFreeBytes());
    }

    @Test
    void compactShouldUnlockSpaceForInsertAfterFragmentation() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple = new byte[100];

        List<RID> rids = new ArrayList<>();

        while (heapPage.hasSpace(tuple.length)) {
            rids.add(heapPage.insert(tuple));
        }

        assertFalse(heapPage.hasSpace(tuple.length));

        for (int i = 0; i < rids.size() / 2; i++) {
            heapPage.delete(rids.get(i));
        }

        // delete alone doesn't move freeSpaceOffSet, so hasSpace is still false
        assertFalse(heapPage.hasSpace(tuple.length));

        heapPage.compact();

        assertTrue(heapPage.hasSpace(tuple.length));

        RID newRid = heapPage.insert(tuple);

        assertArrayEquals(tuple, heapPage.read(newRid));
    }

    @Test
    void insertShouldThrowWhenPageIsGenuinelyFull() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        byte[] tuple = new byte[100];

        while (heapPage.hasSpace(tuple.length)) {
            heapPage.insert(tuple);
        }

        assertThrows(IllegalStateException.class, () -> heapPage.insert(tuple));
    }

    @Test
    void compactShouldSurviveWriteAndReloadCycle() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        HeapPage heapPage = new HeapPage(page, bufferPool);

        RID r1 = heapPage.insert("keep-1".getBytes(StandardCharsets.UTF_8));

        RID r2 = heapPage.insert("drop-me".getBytes(StandardCharsets.UTF_8));

        RID r3 = heapPage.insert("keep-3".getBytes(StandardCharsets.UTF_8));

        heapPage.delete(r2);

        heapPage.compact();

        diskManager.writePage(page);

        Page reloaded = diskManager.readPage(pageId);

        HeapPage reloadedHeap = new HeapPage(reloaded, bufferPool);

        assertArrayEquals("keep-1".getBytes(StandardCharsets.UTF_8), reloadedHeap.read(r1));

        assertNull(reloadedHeap.read(r2));

        assertArrayEquals("keep-3".getBytes(StandardCharsets.UTF_8), reloadedHeap.read(r3));
    }
}