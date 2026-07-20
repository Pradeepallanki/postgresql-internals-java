package com.pradeep.dbdemo.wal;

import java.io.ByteArrayOutputStream;

// staging area for serialized WAL records. Records live here from append() until flush() drains them
// to durable storage. snapshot() gives the writer a non-destructive view (so flush() can retry on
// I/O failure without losing records); clear() only runs after a successful write.
public class WalBuffer {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public synchronized void append(byte[] serializedRecord) {
        bytes.writeBytes(serializedRecord);
    }

    public synchronized int size() {
        return bytes.size();
    }

    public synchronized boolean isEmpty() {
        return bytes.size() == 0;
    }

    public synchronized byte[] snapshot() {
        return bytes.toByteArray();
    }

    public synchronized void clear() {
        bytes.reset();
    }
}