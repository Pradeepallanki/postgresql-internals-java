package com.pradeep.dbdemo.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeapPageTest {
    private Path dbFile;
    private DiskManager diskManager;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("mini", ".db");
        diskManager = new DiskManager(dbFile);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void shouldInsertAndReadSingleTuple() throws Exception {

        int pageId = diskManager.allocatePage();

        Page page = diskManager.readPage(pageId);

        HeapPage heapPage = new HeapPage(page);

        byte[] tuple =
                "John".getBytes(StandardCharsets.UTF_8);

        RID rid =
                heapPage.insert(tuple);

        diskManager.writePage(page);

        Page reloaded =
                diskManager.readPage(pageId);

        HeapPage reloadedHeap =
                new HeapPage(reloaded);

        byte[] result =
                reloadedHeap.read(rid);

        assertArrayEquals(tuple, result);
    }

    @Test
    void shouldReadCorrectTupleForEachRID() throws Exception {

        int pageId = diskManager.allocatePage();

        Page page = diskManager.readPage(pageId);

        HeapPage heapPage = new HeapPage(page);

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
                new HeapPage(reloaded);

        assertArrayEquals(tuple1, hp.read(rid1));

        assertArrayEquals(tuple2, hp.read(rid2));

        assertArrayEquals(tuple3, hp.read(rid3));
    }

    @Test
    void shouldDeleteTuple() throws Exception {

        int pageId = diskManager.allocatePage();

        Page page = diskManager.readPage(pageId);

        HeapPage heapPage = new HeapPage(page);

        byte[] tuple =
                "DeleteMe".getBytes(StandardCharsets.UTF_8);

        RID rid =
                heapPage.insert(tuple);

        heapPage.delete(rid);

        diskManager.writePage(page);

        Page reloaded =
                diskManager.readPage(pageId);

        HeapPage hp =
                new HeapPage(reloaded);

        assertNull(hp.read(rid));
    }

    @Test
    void shouldEventuallyRunOutOfSpace() throws Exception {

        int pageId = diskManager.allocatePage();

        Page page = diskManager.readPage(pageId);

        HeapPage heapPage = new HeapPage(page);

        byte[] tuple = new byte[100];

        int inserted = 0;

        while (heapPage.hasSpace(tuple.length)) {

            heapPage.insert(tuple);

            inserted++;
        }

        assertFalse(heapPage.hasSpace(tuple.length));

        assertTrue(inserted > 0);
    }
}