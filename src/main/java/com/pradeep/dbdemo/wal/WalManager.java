package com.pradeep.dbdemo.wal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// mints LSNs, serializes records, appends them to the WAL sink.
// two flavours: forFile(path) writes to disk; inMemory() writes to a ByteArrayOutputStream (useful for tests
// that don't care about crash recovery but still need append semantics).
public class WalManager implements AutoCloseable {

    private final AtomicLong nextLsn = new AtomicLong();
    private final OutputStream out;
    private final Path walPath;

    private WalManager(OutputStream out, Path walPath) {
        this.out = out;
        this.walPath = walPath;
    }

    public static WalManager forFile(Path walPath) throws IOException {
        Path parent = walPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        OutputStream fileOut = new BufferedOutputStream(
                Files.newOutputStream(walPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        return new WalManager(fileOut, walPath);
    }

    public static WalManager inMemory() {
        return new WalManager(new ByteArrayOutputStream(), null);
    }

    public synchronized long append(WalRecord record) throws IOException {
        long lsn = nextLsn.incrementAndGet();
        record.setLsn(lsn);
        // record now holds its own lsn — we serialize it as-is so on-disk bytes always match the in-memory view.
        byte[] bytes = record.serialize();
        out.write(bytes);
        out.flush(); // WAL-durability precondition: record is at least in the OS page cache before the caller uses this lsn.
        return lsn;
    }

    public synchronized long peekNextLsn() {
        return nextLsn.get() + 1;
    }

    public synchronized int size() {
        // number of records appended so far in this session.
        return (int) nextLsn.get();
    }

    public synchronized List<WalRecord> readAll() throws IOException {
        out.flush();

        byte[] all;
        if (walPath != null) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(walPath))) {
                all = in.readAllBytes();
            }
        } else if (out instanceof ByteArrayOutputStream baos) {
            all = baos.toByteArray();
        } else {
            throw new IllegalStateException("WalManager has no readable sink");
        }

        List<WalRecord> records = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(all);
        while (buf.remaining() >= WalRecord.HEADER_SIZE) {
            records.add(WalRecord.deserialize(buf));
        }
        return records;
    }

    @Override
    public synchronized void close() throws IOException {
        out.close();
    }
}