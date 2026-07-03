package com.pradeep.dbdemo.storage.btree;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.RID;
import com.pradeep.dbdemo.storage.btree.internal.BTreeInternalPage;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
}