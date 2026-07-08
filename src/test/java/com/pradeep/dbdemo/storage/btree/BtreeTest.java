package com.pradeep.dbdemo.storage.btree;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.RID;
import com.pradeep.dbdemo.storage.btree.internal.BTreeInternalPage;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafHeader;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafPage;
import com.pradeep.dbdemo.storage.btree.leaf.BtreeLeafEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BtreeTest {
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
    void shouldInsertIntoSingleLeaf() throws Exception {

        Btree tree = new Btree(bufferPool);

        tree.insert(10L, new RID(1, (short) 1));

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId())
                        .getPageHeader()
                        .getPageType()
        );
    }

    @Test
    void shouldCreateRootAfterLeafSplit() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_INTERNAL,
                root.getPageHeader().getPageType()
        );
    }

    @Test
    void shouldCreateRootWithOneSeparator() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        BTreeInternalPage internal =
                new BTreeInternalPage(root, bufferPool);

        assertEquals(
                1,
                internal.getBtreeInternalHeader().getEntryCount()
        );
    }

    @Test
    void shouldPointRootToTwoLeafPages() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i <= capacity; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        BTreeInternalPage internal =
                new BTreeInternalPage(root, bufferPool);

        int left =
                internal.getBtreeInternalHeader()
                        .getLeftMostChildPageId();

        int right =
                internal.readEntry(0)
                        .rightChildPageId();

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                bufferPool.fetchPage(left)
                        .getPageHeader()
                        .getPageType()
        );

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                bufferPool.fetchPage(right)
                        .getPageHeader()
                        .getPageType()
        );
    }

    @Test
    void shouldInsertManyKeys() throws Exception {

        Btree tree = new Btree(bufferPool);

        for (int i = 0; i < 5000; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_INTERNAL,
                root.getPageHeader().getPageType()
        );
    }

    @Test
    void shouldSearchInsertedKeys() throws Exception {

        Btree tree = new Btree(bufferPool);

        for (int i = 0; i < 1000; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = 0; i < 1000; i++) {

            RID rid = tree.search(i);

            assertNotNull(rid);

            assertEquals(i, rid.pageId());
            assertEquals((short) i, rid.slotNumber());
        }
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingKeyFromEmptyTree() throws Exception {

        Btree tree = new Btree(bufferPool);

        assertFalse(tree.delete(42L));
    }

    @Test
    void shouldDeleteFromSingleLeaf() throws Exception {

        Btree tree = new Btree(bufferPool);

        tree.insert(10L, new RID(1, (short) 1));
        tree.insert(20L, new RID(2, (short) 2));
        tree.insert(30L, new RID(3, (short) 3));

        assertTrue(tree.delete(20L));

        assertNull(tree.search(20L));
        assertNotNull(tree.search(10L));
        assertNotNull(tree.search(30L));
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingKey() throws Exception {

        Btree tree = new Btree(bufferPool);

        tree.insert(10L, new RID(1, (short) 1));

        assertFalse(tree.delete(99L));

        assertNotNull(tree.search(10L));
    }

    @Test
    void shouldNotFindKeyAfterDelete() throws Exception {

        Btree tree = new Btree(bufferPool);

        tree.insert(7L, new RID(7, (short) 7));

        assertTrue(tree.delete(7L));

        assertNull(tree.search(7L));
    }

    @Test
    void shouldRedeletingReturnFalse() throws Exception {

        Btree tree = new Btree(bufferPool);

        tree.insert(5L, new RID(5, (short) 5));

        assertTrue(tree.delete(5L));
        assertFalse(tree.delete(5L));
    }

    @Test
    void shouldDeleteAcrossSplitCausingLeafBorrow() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        assertTrue(tree.delete(0L));

        assertNull(tree.search(0L));

        for (int i = 1; i < capacity + 1; i++) {
            assertNotNull(tree.search(i));
        }
    }

    @Test
    void shouldShrinkRootAfterMergingBackToSingleLeaf() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page rootBefore =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_INTERNAL,
                rootBefore.getPageHeader().getPageType()
        );

        for (int i = 0; i < capacity + 1; i++) {
            assertTrue(tree.delete(i));
        }

        Page rootAfter =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                rootAfter.getPageHeader().getPageType()
        );

        for (int i = 0; i < capacity + 1; i++) {
            assertNull(tree.search(i));
        }
    }

    @Test
    void shouldDeleteAllKeysFromLargeTree() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 1000;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = 0; i < count; i++) {
            assertTrue(tree.delete(i), "expected delete to succeed for key " + i);
        }

        for (int i = 0; i < count; i++) {
            assertNull(tree.search(i), "expected key " + i + " to be gone");
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                root.getPageHeader().getPageType()
        );
    }

    @Test
    void shouldPreserveRemainingKeysAfterInterleavedDeletes() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 500;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = 0; i < count; i += 2) {
            assertTrue(tree.delete(i));
        }

        for (int i = 0; i < count; i++) {
            RID rid = tree.search(i);
            if (i % 2 == 0) {
                assertNull(rid);
            } else {
                assertNotNull(rid);
                assertEquals(i, rid.pageId());
            }
        }
    }

    @Test
    void shouldDeleteInReverseOrder() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 500;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = count - 1; i >= 0; i--) {
            assertTrue(tree.delete(i));
        }

        for (int i = 0; i < count; i++) {
            assertNull(tree.search(i));
        }
    }

    @Test
    void shouldReinsertAfterDelete() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 200;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = 0; i < count; i++) {
            assertTrue(tree.delete(i));
        }

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i + 1000, (short) i));
        }

        for (int i = 0; i < count; i++) {
            RID rid = tree.search(i);
            assertNotNull(rid);
            assertEquals(i + 1000, rid.pageId());
        }
    }

    @Test
    void shouldMergeWithLeftSibling() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page rootBefore =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_INTERNAL,
                rootBefore.getPageHeader().getPageType()
        );

        int min = BTreeLeafPage.minEntries();

        int leftDrop = (capacity + 1) / 2 - min;
        int rightDrop = (capacity + 1) - (capacity + 1) / 2 - min + 1;

        for (int i = 0; i < leftDrop; i++) {
            assertTrue(tree.delete(i));
        }

        for (int i = capacity; i > capacity - rightDrop; i--) {
            assertTrue(tree.delete(i));
        }

        Page rootAfter =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                rootAfter.getPageHeader().getPageType()
        );

        for (int i = leftDrop; i <= capacity - rightDrop; i++) {
            RID rid = tree.search(i);
            assertNotNull(rid, "expected key " + i + " to remain");
            assertEquals(i, rid.pageId());
        }
    }

    @Test
    void mergeFromShouldPreserveSortedOrderAndDedupDuplicateKeys() throws Exception {

        int leftId = bufferPool.allocatePage();
        Page leftPage = bufferPool.fetchPage(leftId);
        leftPage.getPageHeader().setPageType(PageHeader.PageType.BTREE_LEAF);
        BTreeLeafPage left =
                new BTreeLeafPage(leftPage, new BTreeLeafHeader(), bufferPool);
        left.writeHeader();

        int rightId = bufferPool.allocatePage();
        Page rightPage = bufferPool.fetchPage(rightId);
        rightPage.getPageHeader().setPageType(PageHeader.PageType.BTREE_LEAF);
        BTreeLeafPage right =
                new BTreeLeafPage(rightPage, new BTreeLeafHeader(), bufferPool);
        right.writeHeader();

        left.rewriteAllEntries(List.of(
                new BtreeLeafEntry(10L, new RID(1, (short) 1)),
                new BtreeLeafEntry(20L, new RID(2, (short) 2)),
                new BtreeLeafEntry(30L, new RID(3, (short) 3))
        ));

        right.rewriteAllEntries(List.of(
                new BtreeLeafEntry(20L, new RID(200, (short) 20)),
                new BtreeLeafEntry(35L, new RID(35, (short) 35)),
                new BtreeLeafEntry(40L, new RID(40, (short) 40))
        ));

        right.getbTreeLeafHeader().setNextLeafPageId(999);
        right.writeHeader();

        left.mergeFrom(right);

        List<BtreeLeafEntry> merged = left.readAllEntries();

        assertEquals(5, merged.size());
        assertEquals(10L, merged.get(0).key());
        assertEquals(20L, merged.get(1).key());
        assertEquals(30L, merged.get(2).key());
        assertEquals(35L, merged.get(3).key());
        assertEquals(40L, merged.get(4).key());

        assertEquals(2, merged.get(1).rid().pageId());

        assertEquals(999, left.getbTreeLeafHeader().getNextLeafPageId());
    }

    @Test
    void shouldPreserveLeafLinksAfterMerge() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        BTreeInternalPage internal =
                new BTreeInternalPage(root, bufferPool);

        int leftLeafId =
                internal.getBtreeInternalHeader().getLeftMostChildPageId();

        int rightLeafId = internal.readEntry(0).rightChildPageId();

        BTreeLeafPage rightLeaf =
                new BTreeLeafPage(bufferPool.fetchPage(rightLeafId), bufferPool);

        assertEquals(
                leftLeafId,
                rightLeaf.getbTreeLeafHeader().getPrevLeafPageId()
        );

        for (int i = 0; i < capacity + 1; i++) {
            tree.delete(i);
        }

        Page mergedRoot =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                mergedRoot.getPageHeader().getPageType()
        );

        BTreeLeafPage rootLeaf =
                new BTreeLeafPage(mergedRoot, bufferPool);

        assertEquals(-1, rootLeaf.getbTreeLeafHeader().getNextLeafPageId());
    }

    @Test
    void shouldRemoveParentSeparatorAfterMerge() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        Page rootBefore =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        BTreeInternalPage internalBefore =
                new BTreeInternalPage(rootBefore, bufferPool);

        assertEquals(
                1,
                internalBefore.getBtreeInternalHeader().getEntryCount()
        );

        for (int i = 0; i < capacity + 1; i++) {
            tree.delete(i);
        }

        Page rootAfter =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                rootAfter.getPageHeader().getPageType()
        );
    }

    @Test
    void shouldReduceTreeHeightAfterRootCollapse() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        assertEquals(2, treeHeight(tree));

        for (int i = 0; i < capacity + 1; i++) {
            tree.delete(i);
        }

        assertEquals(1, treeHeight(tree));
    }

    @Test
    void shouldRecursivelyMergeParentsInLargeTree() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 200_000;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        int heightBefore = treeHeight(tree);
        assertTrue(heightBefore >= 3, "expected 3-level tree, got " + heightBefore);

        for (int i = 0; i < count; i++) {
            assertTrue(tree.delete(i));
        }

        assertEquals(1, treeHeight(tree));

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                root.getPageHeader().getPageType()
        );

        BTreeLeafPage leaf = new BTreeLeafPage(root, bufferPool);

        assertEquals(0, leaf.getbTreeLeafHeader().getEntryCount());
    }

    @Test
    void shouldPersistMergedTreeAcrossRestart() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 1500;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = 0; i < count; i += 2) {
            assertTrue(tree.delete(i));
        }

        int rootPageId = tree.getbTreeMetadata().getRootPageId();

        bufferPool.flushAll();
        diskManager.close();

        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);

        Btree reloaded =
                new Btree(bufferPool, new BTreeMetadata(rootPageId));

        for (int i = 0; i < count; i++) {
            RID rid = reloaded.search(i);
            if (i % 2 == 0) {
                assertNull(rid, "expected deleted key " + i + " to be gone");
            } else {
                assertNotNull(rid, "expected key " + i + " to survive restart");
                assertEquals(i, rid.pageId());
            }
        }
    }

    @Test
    void shouldEndAsSingleEmptyLeafAfterDeletingAllKeys() throws Exception {

        Btree tree = new Btree(bufferPool);

        int count = 2000;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        for (int i = 0; i < count; i++) {
            assertTrue(tree.delete(i));
        }

        Page root =
                bufferPool.fetchPage(tree.getbTreeMetadata().getRootPageId());

        assertEquals(
                PageHeader.PageType.BTREE_LEAF,
                root.getPageHeader().getPageType()
        );

        BTreeLeafPage rootLeaf = new BTreeLeafPage(root, bufferPool);

        assertEquals(0, rootLeaf.getbTreeLeafHeader().getEntryCount());
        assertEquals(-1, rootLeaf.getbTreeLeafHeader().getNextLeafPageId());
        assertEquals(-1, rootLeaf.getbTreeLeafHeader().getPrevLeafPageId());

        tree.insert(42L, new RID(42, (short) 42));

        assertNotNull(tree.search(42L));
    }

    @Test
    void shouldReturnFreedPagesToFreeList() throws Exception {

        Btree tree = new Btree(bufferPool);

        int capacity = BTreeLeafPage.maxEntries();

        for (int i = 0; i < capacity + 1; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        assertEquals(0, bufferPool.freeListSize());

        for (int i = 0; i < capacity + 1; i++) {
            tree.delete(i);
        }

        assertTrue(
                bufferPool.freeListSize() >= 2,
                "expected freed sibling leaf and collapsed root, got " + bufferPool.freeListSize()
        );

        long pageCountBefore = bufferPool.getPageCount();

        tree.insert(1L, new RID(1, (short) 1));

        assertEquals(
                pageCountBefore,
                bufferPool.getPageCount(),
                "expected reuse from free list rather than fresh allocation"
        );
    }

    private int treeHeight(Btree tree) throws Exception {
        int height = 0;
        int pageId = tree.getbTreeMetadata().getRootPageId();

        while (true) {
            height++;

            Page page = bufferPool.fetchPage(pageId);

            if (page.getPageHeader().getPageType()
                    == PageHeader.PageType.BTREE_LEAF) {
                return height;
            }

            BTreeInternalPage internal =
                    new BTreeInternalPage(page, bufferPool);

            pageId = internal.getBtreeInternalHeader().getLeftMostChildPageId();
        }
    }
}