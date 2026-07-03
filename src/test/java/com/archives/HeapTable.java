package com.archives;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A heap file: rows appended one after the other on disk, no ordering.
 * Fixed-width layout so we can scan in a tight loop.
 *
 * Row format (40 bytes):
 *   id   : 8 bytes (long, big-endian)
 *   name : 32 bytes UTF-8, zero-padded
 *
 * Lookup by id = full table scan = O(N).
 */
public class HeapTable implements AutoCloseable {
    public static final int NAME_BYTES = 32;
    public static final int ROW_BYTES = Long.BYTES + NAME_BYTES;

    private final RandomAccessFile file;
    private long writeCursor;

    public HeapTable(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        this.file = new RandomAccessFile(path.toFile(), "rw");
        // If a previous run crashed mid-write, drop the partial trailing row
        // so the file is always a clean multiple of ROW_BYTES.
        long len = file.length();
        long aligned = (len / ROW_BYTES) * ROW_BYTES;
        if (aligned != len) file.setLength(aligned);
        this.writeCursor = aligned;
    }

    /** Force buffered bytes + metadata to physical disk. */
    public void sync() throws IOException {
        file.getFD().sync();
    }

    /** Append a row. Returns the byte offset where it was written. */
    public long insert(Row row) throws IOException {
        long offset = writeCursor;
        file.seek(offset);
        file.writeLong(row.id());
        byte[] name = row.name().getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[NAME_BYTES];
        System.arraycopy(name, 0, padded, 0, Math.min(name.length, NAME_BYTES));
        file.write(padded);
        writeCursor += ROW_BYTES;
        return offset;
    }

    /** Read one row at a known offset. Used by the indexed path. */
    public Row readAt(long offset) throws IOException {
        file.seek(offset);
        long id = file.readLong();
        byte[] name = new byte[NAME_BYTES];
        file.readFully(name);
        return new Row(id, decodeName(name, 0));
    }

    /** Full table scan: walk every row sequentially. O(N). */
    public Optional<Row> scanById(long id) throws IOException {
        file.seek(0);
        long total = file.length();
        byte[] buf = new byte[ROW_BYTES];
        for (long pos = 0; pos < total; pos += ROW_BYTES) {
            file.readFully(buf);
            long rowId = readLong(buf, 0);
            if (rowId == id) {
                return Optional.of(new Row(rowId, decodeName(buf, Long.BYTES)));
            }
        }
        return Optional.empty();
    }

    public long rowCount() throws IOException {
        return file.length() / ROW_BYTES;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private static String decodeName(byte[] buf, int offset) {
        int len = 0;
        while (len < NAME_BYTES && buf[offset + len] != 0) len++;
        return new String(buf, offset, len, StandardCharsets.UTF_8);
    }

    private static long readLong(byte[] b, int o) {
        return ((long) (b[o]     & 0xff) << 56)
             | ((long) (b[o + 1] & 0xff) << 48)
             | ((long) (b[o + 2] & 0xff) << 40)
             | ((long) (b[o + 3] & 0xff) << 32)
             | ((long) (b[o + 4] & 0xff) << 24)
             | ((long) (b[o + 5] & 0xff) << 16)
             | ((long) (b[o + 6] & 0xff) << 8)
             |  (long) (b[o + 7] & 0xff);
    }
}