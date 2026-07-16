package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.DiskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiskFSMImplTest {

    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;
    private FSMFile fsmFile;
    private DiskFSMImpl fsm;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("disk-fsm", ".db");
        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);
        fsmFile = new FSMFile(bufferPool);
        fsm = new DiskFSMImpl(fsmFile, bufferPool);
    }

    @AfterEach
    void cleanup() throws Exception {
        bufferPool.flushAll();
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void getFreeSpaceShouldThrowOnEmptyFsm() {

        assertThrows(
                IllegalArgumentException.class,
                () -> fsm.getFreeSpace(0)
        );
    }

    @Test
    void getFreeSpaceShouldReturnStoredValueAfterUpdate() {

        fsm.updateFreeSpace(0, 4096);

        assertEquals(4096, fsm.getFreeSpace(0));
    }

    @Test
    void getFreeSpaceShouldThrowForUntrackedPageInNonEmptyFsm() {

        fsm.updateFreeSpace(0, 4096);

        assertThrows(
                IllegalArgumentException.class,
                () -> fsm.getFreeSpace(999)
        );
    }

    @Test
    void getFreeSpaceShouldReflectLatestUpdate() {

        fsm.updateFreeSpace(0, 4096);
        fsm.updateFreeSpace(0, 1024);

        assertEquals(1024, fsm.getFreeSpace(0));
    }

    @Test
    void removePageShouldBeNoOpOnEmptyFsm() {

        assertDoesNotThrow(() -> fsm.removePage(0));
    }

    @Test
    void removePageShouldBeNoOpForUntrackedPage() {

        fsm.updateFreeSpace(0, 4096);

        assertDoesNotThrow(() -> fsm.removePage(999));

        assertEquals(4096, fsm.getFreeSpace(0));
    }

    @Test
    void removePageShouldZeroOutFreeSpace() {

        fsm.updateFreeSpace(0, 4096);

        fsm.removePage(0);

        assertEquals(0, fsm.getFreeSpace(0));
    }

    @Test
    void findPageWithAtLeastShouldSkipRemovedPage() {

        fsm.updateFreeSpace(0, 4096);
        fsm.updateFreeSpace(1, 2048);

        fsm.removePage(0);

        int found = fsm.findPageWithAtLeast(3000);

        assertEquals(FSMMetaPage.INVALID_PAGE_ID, found);
    }

    @Test
    void findPageWithAtLeastShouldStillFindOtherPagesAfterRemove() {

        fsm.updateFreeSpace(0, 4096);
        fsm.updateFreeSpace(1, 2048);

        fsm.removePage(0);

        int found = fsm.findPageWithAtLeast(1024);

        assertEquals(1, found);
    }

    @Test
    void updateAfterRemoveShouldRestoreFreeSpace() {

        fsm.updateFreeSpace(0, 4096);
        fsm.removePage(0);

        fsm.updateFreeSpace(0, 2048);

        assertEquals(2048, fsm.getFreeSpace(0));
    }

    @Test
    void updateShouldSupportNonContiguousHeapPageIds() {

        fsm.updateFreeSpace(1, 4096);
        fsm.updateFreeSpace(3, 2048);
        fsm.updateFreeSpace(10, 1024);

        assertEquals(4096, fsm.getFreeSpace(1));
        assertEquals(2048, fsm.getFreeSpace(3));
        assertEquals(1024, fsm.getFreeSpace(10));
    }

    @Test
    void updateShouldKeepEntriesSortedWhenInsertedOutOfOrder() {

        fsm.updateFreeSpace(10, 1024);
        fsm.updateFreeSpace(1, 4096);
        fsm.updateFreeSpace(5, 2048);

        assertEquals(4096, fsm.getFreeSpace(1));
        assertEquals(2048, fsm.getFreeSpace(5));
        assertEquals(1024, fsm.getFreeSpace(10));

        assertEquals(1, fsm.findPageWithAtLeast(3000));
    }

    @Test
    void getFreeSpaceShouldSurviveFlushAndReload() throws Exception {

        fsm.updateFreeSpace(0, 4096);
        fsm.updateFreeSpace(1, 2048);

        int metaPageId = fsmFile.getMetaPageId();

        bufferPool.flushAll();

        FSMFile reloadedFile = new FSMFile(bufferPool, metaPageId);
        DiskFSMImpl reloaded = new DiskFSMImpl(reloadedFile, bufferPool);

        assertEquals(4096, reloaded.getFreeSpace(0));
        assertEquals(2048, reloaded.getFreeSpace(1));
    }
}