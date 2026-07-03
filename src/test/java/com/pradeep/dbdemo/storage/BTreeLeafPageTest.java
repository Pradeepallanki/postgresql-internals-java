package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafHeader;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafPage;
import com.pradeep.dbdemo.storage.btree.leaf.LeafSplitResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BTreeLeafPageTest {
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
        bufferPool.flushAll();
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void shouldKeepEntriesSorted() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        leaf.insert(40, new RID(1, (short) 1));
        leaf.insert(10, new RID(2, (short) 2));
        leaf.insert(30, new RID(3, (short) 3));
        leaf.insert(20, new RID(4, (short) 4));

        assertEquals(10, leaf.readEntry(0).key());
        assertEquals(20, leaf.readEntry(1).key());
        assertEquals(30, leaf.readEntry(2).key());
        assertEquals(40, leaf.readEntry(3).key());
    }

    @Test
    void shouldSearchExistingKeys() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        RID rid1 = new RID(5, (short) 10);
        RID rid2 = new RID(6, (short) 20);

        leaf.insert(100, rid1);
        leaf.insert(200, rid2);

        assertEquals(rid1, leaf.search(100));
        assertEquals(rid2, leaf.search(200));
    }

    @Test
    void shouldReturnNullForMissingKey() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        leaf.insert(10, new RID(1, (short) 1));
        leaf.insert(20, new RID(2, (short) 2));

        assertNull(leaf.search(99));
    }

    @Test
    void shouldPersistLeafPage() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        leaf.insert(10, new RID(1, (short) 1));
        leaf.insert(20, new RID(2, (short) 2));
        leaf.insert(30, new RID(3, (short) 3));

        int pageId = leaf.getPage().getPageId();

        bufferPool.flushAll();

        diskManager.close();

        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);

        Page page = bufferPool.fetchPage(pageId);

        BTreeLeafPage restored =
                new BTreeLeafPage(page, bufferPool);

        assertEquals(3,
                restored.getbTreeLeafHeader().getEntryCount());

        assertEquals(10, restored.readEntry(0).key());
        assertEquals(20, restored.readEntry(1).key());
        assertEquals(30, restored.readEntry(2).key());

        assertEquals(
                new RID(2, (short) 2),
                restored.search(20)
        );
    }

    @Test
    void shouldFillLeafPageUntilFull() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int inserted = 0;

        while (leaf.hasSpace()) {

            leaf.insert(
                    inserted,
                    new RID(inserted, (short) inserted)
            );

            inserted++;
        }

        assertEquals(
                BTreeLeafPage.maxEntries(),
                inserted
        );

        assertFalse(leaf.hasSpace());

        assertEquals(
                inserted,
                leaf.getbTreeLeafHeader().getEntryCount()
        );
    }

    @Test
    void shouldRejectDuplicateKeys() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        leaf.insert(10, new RID(1, (short) 1));

        assertThrows(
                IllegalArgumentException.class,
                () -> leaf.insert(
                        10,
                        new RID(2, (short) 2)
                )
        );
    }

    @Test
    void shouldSplitLeafPage() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity; i++) {
            assertNull(
                    leaf.insert(
                            i,
                            new RID(1, (short) i)
                    )
            );
        }

        LeafSplitResult result =
                leaf.insert(
                        capacity,
                        new RID(2, (short) 1)
                );

        assertNotNull(result);
    }

    @Test
    void shouldReturnCorrectSeparatorKey() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity; i++) {
            leaf.insert(
                    i,
                    new RID(1, (short) i)
            );
        }

        LeafSplitResult result =
                leaf.insert(
                        capacity,
                        new RID(1, (short) 99)
                );

        assertNotNull(result);

        int expectedSeparator =
                (capacity + 1) / 2;

        assertEquals(
                expectedSeparator,
                result.separatorKey()
        );
    }

    @Test
    void shouldDistributeEntriesAcrossPages() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {
            leaf.insert(
                    i,
                    new RID(1, (short) i)
            );
        }

        bufferPool.flushAll();

        Page newPage =
                bufferPool.fetchPage(
                        leaf.getbTreeLeafHeader()
                                .getNextLeafPageId()
                );

        BTreeLeafPage right =
                new BTreeLeafPage(newPage, bufferPool);

        for (int i = 0;
             i < leaf.getbTreeLeafHeader().getEntryCount();
             i++) {

            assertEquals(
                    i,
                    leaf.readEntry(i).key()
            );
        }

        long expected =
                leaf.getbTreeLeafHeader().getEntryCount();

        for (int i = 0;
             i < right.getbTreeLeafHeader().getEntryCount();
             i++) {

            assertEquals(
                    expected++,
                    right.readEntry(i).key()
            );
        }
    }

    @Test
    void shouldLinkSiblingPages() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {

            leaf.insert(
                    i,
                    new RID(1, (short) i)
            );
        }

        int next =
                leaf.getbTreeLeafHeader()
                        .getNextLeafPageId();

        assertTrue(next >= 0);

        BTreeLeafPage right =
                new BTreeLeafPage(
                        bufferPool.fetchPage(next),
                        bufferPool
                );

        assertEquals(
                leaf.getPage().getPageId(),
                right.getbTreeLeafHeader()
                        .getPrevLeafPageId()
        );
    }

    @Test
    void shouldPersistSplitPages() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {

            leaf.insert(
                    i,
                    new RID(1, (short) i)
            );
        }

        int leftId =
                leaf.getPage().getPageId();

        int rightId =
                leaf.getbTreeLeafHeader()
                        .getNextLeafPageId();

        bufferPool.flushAll();

        diskManager.close();

        diskManager =
                new DiskManager(dbFile);

        bufferPool =
                new BufferPool(diskManager);

        BTreeLeafPage left =
                new BTreeLeafPage(
                        bufferPool.fetchPage(leftId), bufferPool
                );

        BTreeLeafPage right =
                new BTreeLeafPage(
                        bufferPool.fetchPage(rightId), bufferPool
                );

        assertTrue(
                left.getbTreeLeafHeader()
                        .getEntryCount() > 0
        );

        assertTrue(
                right.getbTreeLeafHeader()
                        .getEntryCount() > 0
        );

        assertEquals(
                rightId,
                left.getbTreeLeafHeader()
                        .getNextLeafPageId()
        );

        assertEquals(
                leftId,
                right.getbTreeLeafHeader()
                        .getPrevLeafPageId()
        );
    }

    @Test
    void shouldSearchAcrossSplitPages() throws Exception {

        BTreeLeafPage leaf = createLeafPage();

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {

            leaf.insert(
                    i,
                    new RID(10, (short) i)
            );
        }

        int rightPageId =
                leaf.getbTreeLeafHeader()
                        .getNextLeafPageId();

        BTreeLeafPage right =
                new BTreeLeafPage(
                        bufferPool.fetchPage(rightPageId),
                        bufferPool
                );

        for (int i = 0;
             i < leaf.getbTreeLeafHeader().getEntryCount();
             i++) {

            assertNotNull(
                    leaf.search(
                            leaf.readEntry(i).key()
                    )
            );
        }

        for (int i = 0;
             i < right.getbTreeLeafHeader().getEntryCount();
             i++) {

            assertNotNull(
                    right.search(
                            right.readEntry(i).key()
                    )
            );
        }
    }

    private BTreeLeafPage createLeafPage() throws Exception {

        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader().setPageType(PageHeader.PageType.BTREE_LEAF);

        page.getPageHeader().writeTo(ByteBuffer.wrap(page.getData()));

        BTreeLeafHeader header =
                new BTreeLeafHeader((short) 0, -1, -1);

        return new BTreeLeafPage(page, header, bufferPool);
    }

}