package com.pradeep.dbdemo.storage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DiskManagerTest {

    @Test
    void shouldAllocateThreePages() throws Exception {
        Path db = Files.createTempFile("mini", ".db");

        DiskManager diskManager = new DiskManager(db);

        diskManager.allocatePage();
        diskManager.allocatePage();
        diskManager.allocatePage();

        diskManager.close();

        assertEquals(3L * Page.PAGE_SIZE, Files.size(db));
    }

    @Test
    void shouldReadWrittenPages() throws Exception {
        Path db = Files.createTempFile("mini", ".db");

        DiskManager dm = new DiskManager(db);

        int p0 = dm.allocatePage();
        int p1 = dm.allocatePage();
        int p2 = dm.allocatePage();

        Page page0 = new Page(p0);
        Page page1 = new Page(p1);
        Page page2 = new Page(p2);

        Arrays.fill(page0.getData(), (byte) 1);
        Arrays.fill(page1.getData(), (byte) 2);
        Arrays.fill(page2.getData(), (byte) 3);

        dm.writePage(page0);
        dm.writePage(page1);
        dm.writePage(page2);

        assertArrayEquals(
                page0.getData(),
                dm.readPage(p0).getData());

        assertArrayEquals(
                page1.getData(),
                dm.readPage(p1).getData());

        assertArrayEquals(
                page2.getData(),
                dm.readPage(p2).getData());

        dm.close();
    }

    @Test
    void shouldPersistPagesAcrossRestart() throws Exception {

        Path db = Files.createTempFile("mini", ".db");

        DiskManager dm = new DiskManager(db);

        int pageId = dm.allocatePage();

        Page page = new Page(pageId);

        Arrays.fill(page.getData(), (byte) 99);

        dm.writePage(page);

        dm.close();

        dm = new DiskManager(db);

        Page restored = dm.readPage(pageId);

        assertArrayEquals(
                page.getData(),
                restored.getData());

        dm.close();
    }

    @Test
    void shouldCreateDefaultHeader() {

        Page page = new Page(0);

        assertEquals(
                0,
                page.getPageHeader().getSlotCount());

        assertEquals(
                Page.PAGE_SIZE,
                page.getPageHeader().getFreeSpaceOffSet());

        assertEquals(
                PageHeader.PageType.EMPTY,
                page.getPageHeader().getPageType());

    }

    @Test
    void shouldPersistHeader() throws Exception {

        Path db = Files.createTempFile("mini", ".db");

        DiskManager dm =
                new DiskManager(db);

        int id = dm.allocatePage();

        Page page = new Page(id);

        page.getPageHeader().setSlotCount(12);

        page.getPageHeader().setFreeSpaceOffSet(7000);

        dm.writePage(page);

        dm.close();

        dm = new DiskManager(db);

        Page restored =
                dm.readPage(id);

        assertEquals(
                12,
                restored.getPageHeader().getSlotCount());

        assertEquals(
                7000,
                restored.getPageHeader().getFreeSpaceOffSet());

        assertEquals(
                PageHeader.PageType.EMPTY,
                restored.getPageHeader().getPageType());

    }

    @Test
    void shouldPersistPageType() throws Exception {

        Path db = Files.createTempFile("mini", ".db");

        DiskManager dm =
                new DiskManager(db);

        int id = dm.allocatePage();

        Page page = new Page(id);

        page.getPageHeader().setPageType(PageHeader.PageType.HEAP);

        dm.writePage(page);

        dm.close();

        dm = new DiskManager(db);

        Page restored =
                dm.readPage(id);

        assertEquals(
                PageHeader.PageType.HEAP,
                restored.getPageHeader().getPageType());

    }

}