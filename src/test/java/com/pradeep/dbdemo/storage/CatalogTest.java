package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.btree.Btree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("catalog", ".db");
        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void createFreshShouldReserveExactlyPageZero() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        Page page = bufferPool.fetchPage(Catalog.CATALOG_PAGE_ID);

        assertEquals(PageHeader.PageType.CATALOG, page.getPageHeader().getPageType());

        assertEquals(-1, catalog.getFsmMetaPageId());

        assertNull(catalog.lookup("anything"));
    }

    @Test
    void createFreshShouldRefuseIfPageZeroIsAlreadyTaken() throws Exception {

        // simulate something else claiming page 0 first
        bufferPool.allocatePage();

        assertThrows(IllegalStateException.class, () -> Catalog.createFresh(bufferPool));
    }

    @Test
    void openShouldRejectPageZeroThatIsNotCatalog() throws Exception {

        // page 0 exists but wasn't set up as catalog
        bufferPool.allocatePage();

        bufferPool.flushAll();

        assertThrows(IllegalStateException.class, () -> Catalog.open(bufferPool));
    }

    @Test
    void registerAndLookupShouldRoundTrip() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        catalog.registerIndex("users_pk", Catalog.INDEX_TYPE_BTREE, 42);

        Catalog.IndexEntry entry = catalog.lookup("users_pk");

        assertNotNull(entry);

        assertEquals("users_pk", entry.name());

        assertEquals(Catalog.INDEX_TYPE_BTREE, entry.indexType());

        assertEquals(42, entry.rootPageId());
    }

    @Test
    void registeringDuplicateNameShouldThrow() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        catalog.registerIndex("orders_pk", Catalog.INDEX_TYPE_BTREE, 5);

        assertThrows(IllegalArgumentException.class,
                () -> catalog.registerIndex("orders_pk", Catalog.INDEX_TYPE_BTREE, 9));
    }

    @Test
    void updateRootOfUnknownIndexShouldThrow() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        assertThrows(IllegalArgumentException.class, () -> catalog.updateRoot("missing", 3));
    }

    @Test
    void catalogShouldSurviveRestart() throws Exception {

        Catalog fresh = Catalog.createFresh(bufferPool);

        fresh.setFsmMetaPageId(17);

        fresh.registerIndex("users_pk", Catalog.INDEX_TYPE_BTREE, 3);

        fresh.registerIndex("orders_pk", Catalog.INDEX_TYPE_BTREE, 8);

        fresh.updateRoot("users_pk", 25);

        bufferPool.flushAll();

        diskManager.close();

        diskManager = new DiskManager(dbFile);

        bufferPool = new BufferPool(diskManager);

        Catalog reloaded = Catalog.open(bufferPool);

        assertEquals(17, reloaded.getFsmMetaPageId());

        assertEquals(25, reloaded.lookup("users_pk").rootPageId());

        assertEquals(8, reloaded.lookup("orders_pk").rootPageId());

        assertEquals(2, reloaded.listIndexes().size());
    }

    @Test
    void btreeShouldRegisterItselfInCatalogOnFirstUse() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        Btree tree = new Btree(bufferPool, catalog, "primary");

        int rootPageId = tree.getbTreeMetadata().getRootPageId();

        Catalog.IndexEntry entry = catalog.lookup("primary");

        assertNotNull(entry);

        assertEquals(rootPageId, entry.rootPageId());
    }

    @Test
    void btreeShouldFindAllKeysAfterRestartViaCatalog() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        Btree tree = new Btree(bufferPool, catalog, "primary");

        int count = 1500; // enough to force splits and grow past the initial root

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        int rootBeforeRestart = tree.getbTreeMetadata().getRootPageId();

        assertNotEquals(catalog.lookup("primary").rootPageId(), 0,
                "root should have moved off page 0 after splits");

        assertEquals(rootBeforeRestart, catalog.lookup("primary").rootPageId());

        bufferPool.flushAll();

        diskManager.close();

        diskManager = new DiskManager(dbFile);

        bufferPool = new BufferPool(diskManager);

        Catalog reloaded = Catalog.open(bufferPool);

        Btree reopened = new Btree(bufferPool, reloaded, "primary");

        assertEquals(rootBeforeRestart, reopened.getbTreeMetadata().getRootPageId());

        for (int i = 0; i < count; i++) {
            RID rid = reopened.search(i);
            assertNotNull(rid, "expected key " + i + " to survive restart");
            assertEquals(i, rid.pageId());
        }
    }

    @Test
    void catalogShouldTrackRootShrinkAfterMassDelete() throws Exception {

        Catalog catalog = Catalog.createFresh(bufferPool);

        Btree tree = new Btree(bufferPool, catalog, "primary");

        int count = 1500;

        for (int i = 0; i < count; i++) {
            tree.insert(i, new RID(i, (short) i));
        }

        int rootAfterInserts = catalog.lookup("primary").rootPageId();

        for (int i = 0; i < count; i++) {
            assertTrue(tree.delete(i));
        }

        int rootAfterDeletes = catalog.lookup("primary").rootPageId();

        assertNotEquals(rootAfterInserts, rootAfterDeletes,
                "root should have shrunk down as internal levels collapsed");

        assertEquals(rootAfterDeletes, tree.getbTreeMetadata().getRootPageId());
    }
}