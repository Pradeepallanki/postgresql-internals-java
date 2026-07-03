package com.pradeep.dbdemo;

import com.archives.BPlusTreeTable;
import com.archives.HeapTable;
import com.archives.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexPerformanceTest {

    private static final int ROW_COUNT = 50_000;
    private static final int LOOKUPS = 500;
    private static final long SEED = 42L;

    @Test
    void bPlusTreeFindIsFasterThanFullScan(@TempDir Path dir) throws IOException {
        Path heapFile = dir.resolve("rows.db");

        try (HeapTable heap = new HeapTable(heapFile)) {
            BPlusTreeTable indexed = new BPlusTreeTable(heap);

            for (int i = 0; i < ROW_COUNT; i++) {
                indexed.insert(new Row(i, "user-" + i));
            }
            assertEquals(ROW_COUNT, heap.rowCount());

            Random rnd = new Random(SEED);
            long[] ids = new long[LOOKUPS];
            for (int i = 0; i < LOOKUPS; i++) {
                ids[i] = rnd.nextInt(ROW_COUNT);
            }

            // warm up JIT + page cache so the comparison is fair
            for (int i = 0; i < 10; i++) {
                heap.scanById(ids[i]);
                indexed.findById(ids[i]);
            }

            long scanStart = System.nanoTime();
            for (long id : ids) {
                Optional<Row> r = heap.scanById(id);
                assertTrue(r.isPresent());
                assertEquals(id, r.get().id());
            }
            long scanNs = System.nanoTime() - scanStart;

            long idxStart = System.nanoTime();
            for (long id : ids) {
                Optional<Row> r = indexed.findById(id);
                assertTrue(r.isPresent());
                assertEquals(id, r.get().id());
            }
            long idxNs = System.nanoTime() - idxStart;

            double scanMs = scanNs / 1_000_000.0;
            double idxMs = idxNs / 1_000_000.0;
            double scanUs = scanNs / 1_000.0 / LOOKUPS;
            double idxUs = idxNs / 1_000.0 / LOOKUPS;
            double speedup = (double) scanNs / idxNs;

            System.out.println();
            System.out.println("=== " + ROW_COUNT + " rows, " + LOOKUPS + " lookups ===");
            System.out.printf("Full scan : %8.2f ms total  | %8.2f us / lookup%n", scanMs, scanUs);
            System.out.printf("B+ tree   : %8.2f ms total  | %8.2f us / lookup%n", idxMs, idxUs);
            System.out.printf("Speedup   : %8.1fx%n", speedup);
            System.out.println();

            assertTrue(idxNs * 10 < scanNs,
                "B+ tree should be at least 10x faster than full scan, got " + speedup + "x");
        }
    }

    @Test
    void bothPathsReturnTheSameRow(@TempDir Path dir) throws IOException {
        Path heapFile = dir.resolve("rows.db");
        try (HeapTable heap = new HeapTable(heapFile)) {
            BPlusTreeTable indexed = new BPlusTreeTable(heap);
            for (int i = 0; i < 1_000; i++) {
                indexed.insert(new Row(i, "user-" + i));
            }
            for (long id : new long[]{0, 1, 7, 42, 500, 999}) {
                assertEquals(heap.scanById(id), indexed.findById(id));
            }
            assertEquals(Optional.empty(), heap.scanById(10_000));
            assertEquals(Optional.empty(), indexed.findById(10_000));
        }
    }
}