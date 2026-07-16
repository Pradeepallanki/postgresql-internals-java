package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FSMFileTest {

    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("fsm", ".db");
        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        bufferPool.flushAll();
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void shouldInitializeFreshFsmWithMetaPageAtPageZero() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        assertEquals(0, fsmFile.getMetaPageId());

        FSMMetaPage metaPage = fsmFile.getMetaPage();

        assertNotNull(metaPage);

        assertEquals(FSMMetaPage.INVALID_PAGE_ID, metaPage.getRootPageId());
        assertEquals(0, metaPage.getTreeHeight());
        assertEquals(1, metaPage.getNextFSMPageId());
    }

    @Test
    void shouldReadBackMetaPageStateAfterFlushAndReload() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        int metaPageId = fsmFile.getMetaPageId();

        bufferPool.flushAll();

        Page rawPage = bufferPool.fetchPage(metaPageId);
        FSMMetaPage reloaded = new FSMMetaPage(rawPage, bufferPool);

        assertEquals(FSMMetaPage.INVALID_PAGE_ID, reloaded.getRootPageId());
        assertEquals(0, reloaded.getTreeHeight());
        assertEquals(1, reloaded.getNextFSMPageId());
    }

    @Test
    void createLeafPageShouldAllocateNewPageInitializedEmpty() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMLeafPage leaf = fsmFile.createLeafPage();

        assertNotNull(leaf);
        assertNotEquals(fsmFile.getMetaPageId(), leaf.getPage().getPageId());

        assertEquals(0, leaf.getEntryCount());
        assertEquals(-1, leaf.getMaxFreeSpace());
    }

    @Test
    void createLeafPageShouldReturnDistinctPagesOnEachCall() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMLeafPage first = fsmFile.createLeafPage();
        FSMLeafPage second = fsmFile.createLeafPage();
        FSMLeafPage third = fsmFile.createLeafPage();

        assertNotEquals(first.getPage().getPageId(), second.getPage().getPageId());
        assertNotEquals(second.getPage().getPageId(), third.getPage().getPageId());
        assertNotEquals(first.getPage().getPageId(), third.getPage().getPageId());
    }

    @Test
    void createLeafPageShouldPersistAndBeReadableViaGetLeafPage() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMLeafPage created = fsmFile.createLeafPage();
        int pageId = created.getPage().getPageId();

        created.updateFreeSpace(0, 4096);

        bufferPool.flushAll();

        FSMFile reopened = new FSMFile(bufferPool, fsmFile.getMetaPageId());
        FSMLeafPage reloaded = reopened.getLeafPage(pageId);

        assertEquals(1, reloaded.getEntryCount());
        assertEquals(4096, reloaded.getFreeSpace(0));
    }

    @Test
    void createInternalPageShouldAllocateNewPageInitializedEmpty() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMInternalPage internal = fsmFile.createInternalPage();

        assertNotNull(internal);
        assertNotEquals(fsmFile.getMetaPageId(), internal.getPage().getPageId());

        assertEquals(0, internal.getEntryCount());
        assertEquals(-1, internal.getMaxSummary());
    }

    @Test
    void createInternalPageShouldReturnDistinctPagesOnEachCall() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMInternalPage first = fsmFile.createInternalPage();
        FSMInternalPage second = fsmFile.createInternalPage();

        assertNotEquals(first.getPage().getPageId(), second.getPage().getPageId());
    }

    @Test
    void createInternalPageShouldPersistAndBeReadableViaGetInternalPage() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMInternalPage created = fsmFile.createInternalPage();
        int pageId = created.getPage().getPageId();

        created.updateChildSummary(42, 8000);

        bufferPool.flushAll();

        FSMFile reopened = new FSMFile(bufferPool, fsmFile.getMetaPageId());
        FSMInternalPage reloaded = reopened.getInternalPage(pageId);

        assertEquals(1, reloaded.getEntryCount());
        assertTrue(reloaded.containsChild(42));
        assertEquals(8000, reloaded.getChildSummary(42));
    }

    @Test
    void createLeafAndCreateInternalShouldNotShareAPageId() throws Exception {

        FSMFile fsmFile = new FSMFile(bufferPool);

        FSMLeafPage leaf = fsmFile.createLeafPage();
        FSMInternalPage internal = fsmFile.createInternalPage();

        assertNotEquals(leaf.getPage().getPageId(), internal.getPage().getPageId());
    }
}