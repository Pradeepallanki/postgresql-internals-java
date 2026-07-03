package com.archives;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A tiny "database engine":
 *   - Rows live in a heap file on disk (durable).
 *   - A B+ tree index on the primary key lives in memory.
 *   - On open(), the index is rebuilt by scanning the heap file —
 *     this is how durability survives a restart even though the
 *     index itself isn't persisted.
 *
 * Semantics:
 *   - id is the primary key. Duplicate inserts are rejected.
 *   - Every insert is fsync'd before it's acknowledged, so a row
 *     that returned successfully will survive a crash.
 */
public class Engine implements AutoCloseable {

    private final HeapTable heap;
    private final BPlusTree index = new BPlusTree();

    public static Engine open(Path dataFile) throws IOException {
        HeapTable heap = new HeapTable(dataFile);
        Engine engine = new Engine(heap);
        engine.recoverIndex();
        return engine;
    }

    private Engine(HeapTable heap) {
        this.heap = heap;
    }

    private void recoverIndex() throws IOException {
        long count = heap.rowCount();
        for (long i = 0; i < count; i++) {
            long offset = i * HeapTable.ROW_BYTES;
            Row row = heap.readAt(offset);
            index.insert(row.id(), offset);
        }
    }

    public void insert(Row row) throws IOException {
        if (index.find(row.id()) != null) {
            throw new PrimaryKeyViolation(row.id());
        }
        long offset = heap.insert(row);
        heap.sync();
        index.insert(row.id(), offset);
    }

    public Optional<Row> query(long id) throws IOException {
        Long offset = index.find(id);
        if (offset == null) return Optional.empty();
        return Optional.of(heap.readAt(offset));
    }

    public long rowCount() throws IOException {
        return heap.rowCount();
    }

    @Override
    public void close() throws IOException {
        heap.close();
    }

    public static class PrimaryKeyViolation extends RuntimeException {
        public PrimaryKeyViolation(long id) {
            super("duplicate primary key: " + id);
        }
    }
}