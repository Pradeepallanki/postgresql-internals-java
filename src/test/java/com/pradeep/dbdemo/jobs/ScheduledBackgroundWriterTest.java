package com.pradeep.dbdemo.jobs;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledBackgroundWriterTest {
    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("bgwriter", ".db");
        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager, 8);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void dirtyPagesReachDiskWithoutAnExplicitFlush() throws Exception {
        int p = bufferPool.allocatePage();
        Page page = bufferPool.fetchPage(p);
        page.getData()[100] = 77;
        bufferPool.markDirty(p);

        try (ScheduledBackgroundWriter writer = new ScheduledBackgroundWriter(bufferPool, 10)) {
            writer.start(0, 20);
            waitFor(() -> diskByteAt(p, 100) == 77, 2_000);
        }

        assertEquals(77, diskByteAt(p, 100), "background writer should have persisted the page");
    }

    @Test
    void writerKeepsFiringAfterAnEmptyCycle() throws Exception {
        try (ScheduledBackgroundWriter writer = new ScheduledBackgroundWriter(bufferPool, 5)) {
            writer.start(0, 15);

            // let a few empty cycles pass first — nothing to flush yet
            Thread.sleep(80);

            int p = bufferPool.allocatePage();
            Page page = bufferPool.fetchPage(p);
            page.getData()[200] = 42;
            bufferPool.markDirty(p);

            waitFor(() -> diskByteAt(p, 200) == 42, 2_000);

            assertEquals(42, diskByteAt(p, 200), "writer should still be flushing after prior empty cycles");
        }
    }

    @Test
    void closeShouldReturnPromptly() throws Exception {
        ScheduledBackgroundWriter writer = new ScheduledBackgroundWriter(bufferPool, 10);
        writer.start(0, 20);
        Thread.sleep(50);

        long before = System.currentTimeMillis();
        writer.close();
        long elapsed = System.currentTimeMillis() - before;

        assertTrue(elapsed < 5_500, "close should return within the shutdown timeout, took " + elapsed + "ms");
    }

    // read a byte from the file on disk without going through the pool being tested.
    private int diskByteAt(int pageId, int offset) {
        try (DiskManager reader = new DiskManager(dbFile)) {
            if (pageId >= reader.getPageCount()) return -1;
            return reader.readPage(pageId).getData()[offset] & 0xff;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
    }
}