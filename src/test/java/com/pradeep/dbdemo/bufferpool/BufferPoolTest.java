package com.pradeep.dbdemo.bufferpool;

import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {
    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("bufferpool", ".db");
        diskManager = new DiskManager(dbFile);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void hittingTheSamePageReturnsTheSameInstance() throws Exception {
        bufferPool = new BufferPool(diskManager, 4);
        int p = bufferPool.allocatePage();

        Page first = bufferPool.fetchPage(p);
        Page second = bufferPool.fetchPage(p);

        assertSame(first, second);
    }

    @Test
    void fetchingBeyondCacheSizeEvictsOldEntries() throws Exception {
        bufferPool = new BufferPool(diskManager, 2);

        int p0 = bufferPool.allocatePage();
        int p1 = bufferPool.allocatePage();
        int p2 = bufferPool.allocatePage();

        bufferPool.fetchPage(p0);
        bufferPool.fetchPage(p1);
        bufferPool.fetchPage(p2);

        assertEquals(2, bufferPool.size());
    }

    @Test
    void dirtyVictimIsFlushedBeforeEviction() throws Exception {
        bufferPool = new BufferPool(diskManager, 1);

        int p0 = bufferPool.allocatePage();
        int p1 = bufferPool.allocatePage();

        Page pg0 = bufferPool.fetchPage(p0);
        pg0.getData()[100] = 42;
        bufferPool.markDirty(p0);

        // fetching p1 forces p0 out; the write must have hit disk.
        bufferPool.fetchPage(p1);

        assertFalse(bufferPool.isCached(p0));

        Page reloaded = bufferPool.fetchPage(p0);
        assertEquals(42, reloaded.getData()[100]);
    }

    @Test
    void hotPagesOutlastColdPagesUnderPressure() throws Exception {
        bufferPool = new BufferPool(diskManager, 2);

        int hot = bufferPool.allocatePage();
        int cold = bufferPool.allocatePage();
        int newcomer = bufferPool.allocatePage();

        bufferPool.fetchPage(hot);
        bufferPool.fetchPage(cold);

        // walk the hot page's usage up to the cap
        for (int i = 0; i < 10; i++) {
            bufferPool.fetchPage(hot);
        }

        // bringing in the newcomer should evict the cold page, not the hot one
        bufferPool.fetchPage(newcomer);

        assertTrue(bufferPool.isCached(hot));
        assertFalse(bufferPool.isCached(cold));
    }

    @Test
    void freePageRemovesTheEntryFromTheCache() throws Exception {
        bufferPool = new BufferPool(diskManager, 4);

        int p = bufferPool.allocatePage();
        bufferPool.fetchPage(p);
        assertTrue(bufferPool.isCached(p));

        bufferPool.freePage(p);

        assertFalse(bufferPool.isCached(p));
        assertEquals(1, bufferPool.freeListSize());
    }

    @Test
    void nextAllocateReusesTheFreedPageId() throws Exception {
        bufferPool = new BufferPool(diskManager, 4);

        int p0 = bufferPool.allocatePage();
        bufferPool.allocatePage();

        bufferPool.freePage(p0);

        assertEquals(p0, bufferPool.allocatePage());
    }

    @Test
    void requestedSizeIsRoundedUpToTheNextPowerOfTwo() {
        bufferPool = new BufferPool(diskManager, 1000);

        assertEquals(1024, bufferPool.capacity());
    }

    @Test
    void clockHandRecoversWhenCacheShrinksBetweenSweeps() throws Exception {
        bufferPool = new BufferPool(diskManager, 3);

        int p0 = bufferPool.allocatePage();
        int p1 = bufferPool.allocatePage();
        int p2 = bufferPool.allocatePage();
        int p3 = bufferPool.allocatePage();

        bufferPool.fetchPage(p0);
        bufferPool.fetchPage(p1);
        bufferPool.fetchPage(p2);

        // drop the entry the clock hand is likely closest to; the next eviction sweep must handle the shrunken cache without going out of bounds.
        bufferPool.freePage(p2);

        assertDoesNotThrow(() -> bufferPool.fetchPage(p3));
    }

    @Test
    void flushAllPersistsEveryDirtyPage() throws Exception {
        bufferPool = new BufferPool(diskManager, 4);

        int p0 = bufferPool.allocatePage();
        int p1 = bufferPool.allocatePage();

        // write past the 12-byte page header so the payload isn't clobbered by header re-serialization on flush.
        Page pg0 = bufferPool.fetchPage(p0);
        pg0.getData()[100] = 7;
        bufferPool.markDirty(p0);

        Page pg1 = bufferPool.fetchPage(p1);
        pg1.getData()[100] = 9;
        bufferPool.markDirty(p1);

        bufferPool.flushAll();

        // reopen from disk — the buffered writes should be there
        diskManager.close();
        diskManager = new DiskManager(dbFile);
        BufferPool fresh = new BufferPool(diskManager, 4);

        assertEquals(7, fresh.fetchPage(p0).getData()[100]);
        assertEquals(9, fresh.fetchPage(p1).getData()[100]);
    }
}