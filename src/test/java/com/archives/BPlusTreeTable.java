package com.archives;

import java.io.IOException;
import java.util.Optional;

/**
 * Same heap file as before, but with a B+ tree index on `id` mapping
 * id -> byte offset. Lookup is now: tree walk in memory, then one disk read.
 */
public class BPlusTreeTable implements AutoCloseable {

    private final HeapTable heap;
    private final BPlusTree index = new BPlusTree();

    public BPlusTreeTable(HeapTable heap) {
        this.heap = heap;
    }

    public void insert(Row row) throws IOException {
        long offset = heap.insert(row);
        index.insert(row.id(), offset);
    }

    /** Indexed lookup: O(log N) tree walk + one row read from disk. */
    public Optional<Row> findById(long id) throws IOException {
        Long offset = index.find(id);
        if (offset == null) return Optional.empty();
        return Optional.of(heap.readAt(offset));
    }

    @Override
    public void close() throws IOException {
        heap.close();
    }
}