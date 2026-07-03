package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.btree.internal.BTreeInternalPage;
import com.pradeep.dbdemo.storage.btree.internal.BtreeInternalHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BTreeInternalPageTest {

    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {

        dbFile = Files.createTempFile("btree", ".db");

        diskManager = new DiskManager(dbFile);

        bufferPool = new BufferPool(diskManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void shouldInsertSeparatorsInSortedOrder() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        page.getPageHeader()
                .setFreeSpaceOffSet((short) Page.PAGE_SIZE);

        BtreeInternalHeader header =
                new BtreeInternalHeader((short) 0, 100);

        BTreeInternalPage internal =
                new BTreeInternalPage(page, header, bufferPool);

        internal.shiftAndInsert(40, 4);
        internal.shiftAndInsert(10, 1);
        internal.shiftAndInsert(30, 3);
        internal.shiftAndInsert(20, 2);

        assertEquals(1, internal.findChild(10));
        assertEquals(2, internal.findChild(20));
        assertEquals(3, internal.findChild(30));
        assertEquals(4, internal.findChild(40));
    }

    @Test
    void shouldReturnCorrectChild() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        BtreeInternalHeader header =
                new BtreeInternalHeader((short) 0, 50);

        BTreeInternalPage internal =
                new BTreeInternalPage(page, header, bufferPool);

        internal.shiftAndInsert(20, 200);

        internal.shiftAndInsert(40, 400);

        internal.shiftAndInsert(70, 700);

        assertEquals(50, internal.findChild(5));

        assertEquals(200, internal.findChild(25));

        assertEquals(400, internal.findChild(60));

        assertEquals(700, internal.findChild(100));
    }

    @Test
    void shouldRejectDuplicateSeparator() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        BtreeInternalHeader header =
                new BtreeInternalHeader((short) 0, 1);

        BTreeInternalPage internal =
                new BTreeInternalPage(page, header, bufferPool);

        internal.shiftAndInsert(50, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> internal.shiftAndInsert(50, 3)
        );
    }

    @Test
    void shouldPersistInternalPage() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        BtreeInternalHeader header =
                new BtreeInternalHeader((short) 0, 999);

        BTreeInternalPage internal =
                new BTreeInternalPage(page, header, bufferPool);

        internal.shiftAndInsert(25, 2);

        internal.shiftAndInsert(50, 3);

        internal.shiftAndInsert(75, 4);

        bufferPool.flushAll();

        diskManager.close();

        diskManager = new DiskManager(dbFile);

        bufferPool = new BufferPool(diskManager);

        Page persisted =
                bufferPool.fetchPage(pageId);

        BTreeInternalPage reloaded =
                new BTreeInternalPage(persisted, bufferPool);

        assertEquals(999,
                reloaded.findChild(10));

        assertEquals(2,
                reloaded.findChild(30));

        assertEquals(3,
                reloaded.findChild(60));

        assertEquals(4,
                reloaded.findChild(90));
    }

    @Test
    void shouldFillInternalPage() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        BtreeInternalHeader header =
                new BtreeInternalHeader((short) 0, 0);

        BTreeInternalPage internal =
                new BTreeInternalPage(page, header, bufferPool);

        int max = BTreeInternalPage.maxEntries();

        for (int i = 0; i < max; i++) {

            internal.shiftAndInsert(i * 10L, i + 1);

        }

        assertFalse(internal.hasSpace());

        assertEquals(
                max,
                internal.getBtreeInternalHeader()
                        .getEntryCount()
        );
    }

    @Test
    void shouldReturnLeftMostChildForSmallestKey() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        BtreeInternalHeader header =
                new BtreeInternalHeader((short) 0, 42);

        BTreeInternalPage internal =
                new BTreeInternalPage(page, header, bufferPool);

        internal.shiftAndInsert(100, 2);

        assertEquals(42, internal.findChild(-1));

        assertEquals(42, internal.findChild(0));

        assertEquals(42, internal.findChild(99));
    }

}