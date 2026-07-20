package com.pradeep.dbdemo.wal;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Two-phase WAL:
//   append(record)  — mints an LSN, serializes, stages in WalBuffer. No disk I/O. Advances insertLsn.
//   flush()         — writes the buffered bytes to the sink, forces to disk, clears buffer. Advances flushLsn.
//
// insertLsn  = the highest LSN handed out to a caller.
// flushLsn   = the highest LSN that is durable on disk.
// insertLsn - flushLsn = records currently in-buffer.
//
// forFile mode uses FileChannel + force(true) so flush() gives fsync-like durability. inMemory mode
// keeps a ByteArrayOutputStream as the "durable" sink so recovery tests can inspect the log without
// touching the filesystem.
public class WalManager implements AutoCloseable {

    private final AtomicLong nextLsn = new AtomicLong();
    private long flushLsn = 0L;

    private final WalBuffer buffer = new WalBuffer();

    private final Path walPath;                        // null in inMemory mode
    private final FileChannel channel;                 // null in inMemory mode
    private final ByteArrayOutputStream memorySink;    // null in file mode

    private WalManager(Path walPath, FileChannel channel, ByteArrayOutputStream memorySink) {
        this.walPath = walPath;
        this.channel = channel;
        this.memorySink = memorySink;
    }

    public static WalManager forFile(Path walPath) throws IOException {
        Path parent = walPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        FileChannel channel = FileChannel.open(
                walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        return new WalManager(walPath, channel, null);
    }

    public static WalManager inMemory() {
        return new WalManager(null, null, new ByteArrayOutputStream());
    }

    public synchronized long append(WalRecord record) {
        long lsn = nextLsn.incrementAndGet();
        record.setLsn(lsn);
        buffer.append(record.serialize());
        return lsn;
    }

    public synchronized void flush() throws IOException {
        if (buffer.isEmpty()) {
            return;
        }

        byte[] toWrite = buffer.snapshot();
        long snapshotLsn = nextLsn.get();

        if (channel != null) {
            ByteBuffer bb = ByteBuffer.wrap(toWrite);
            while (bb.hasRemaining()) {
                channel.write(bb);
            }
            channel.force(true);
        } else {
            memorySink.write(toWrite);
        }

        // clear only after the write succeeded — an IOException above leaves the records staged for retry.
        buffer.clear();
        flushLsn = snapshotLsn;
    }

    public synchronized long insertLsn() {
        return nextLsn.get();
    }

    public synchronized long flushLsn() {
        return flushLsn;
    }

    public synchronized int bufferedBytes() {
        return buffer.size();
    }

    public synchronized long peekNextLsn() {
        return nextLsn.get() + 1;
    }

    public synchronized int size() {
        return (int) nextLsn.get();
    }

    // readAll reads only records that have been flushed to the sink. Records still in the buffer are
    // deliberately excluded — they weren't durable and wouldn't survive a real crash.
    public synchronized List<WalRecord> readAll() throws IOException {
        byte[] all;
        if (channel != null) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(walPath))) {
                all = in.readAllBytes();
            }
        } else {
            all = memorySink.toByteArray();
        }

        List<WalRecord> records = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(all);
        while (buf.remaining() >= WalRecord.HEADER_SIZE) {
            int start = buf.position();
            int payloadLen = buf.getInt(start + Long.BYTES + Integer.BYTES + Integer.BYTES);
            if (buf.remaining() < WalRecord.HEADER_SIZE + payloadLen) break;
            records.add(WalRecord.deserialize(buf));
        }
        return records;
    }

    @Override
    public synchronized void close() throws IOException {
        flush();
        if (channel != null) {
            channel.close();
        }
    }
}