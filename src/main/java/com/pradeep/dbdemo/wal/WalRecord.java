package com.pradeep.dbdemo.wal;

import java.nio.ByteBuffer;

// wire format: [long lsn][int opOrdinal][int pageId][int payloadLen][payload bytes]
// fixed-header size lets the reader parse fields sequentially without an outer length prefix.
public class WalRecord {

    public static final int HEADER_SIZE = Long.BYTES + Integer.BYTES * 3; // 20

    private long lsn;
    private final WalOperation operation;
    private final int pageId;
    private final byte[] payload;

    public WalRecord(WalOperation operation, int pageId, byte[] payload) {
        this(0L, operation, pageId, payload);
    }

    public WalRecord(long lsn, WalOperation operation, int pageId, byte[] payload) {
        this.lsn = lsn;
        this.operation = operation;
        this.pageId = pageId;
        this.payload = payload;
    }

    public long getLsn() {
        return lsn;
    }

    public void setLsn(long lsn) {
        this.lsn = lsn;
    }

    public WalOperation getOperation() {
        return operation;
    }

    public int getPageId() {
        return pageId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int serializedSize() {
        return HEADER_SIZE + payload.length;
    }

    public byte[] serialize() {
        ByteBuffer buf = ByteBuffer.allocate(serializedSize());
        buf.putLong(lsn);
        buf.putInt(operation.ordinal());
        buf.putInt(pageId);
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    public static WalRecord deserialize(ByteBuffer buf) {
        long lsn = buf.getLong();
        WalOperation op = WalOperation.values()[buf.getInt()];
        int pageId = buf.getInt();
        int payloadLen = buf.getInt();
        byte[] payload = new byte[payloadLen];
        buf.get(payload);
        return new WalRecord(lsn, op, pageId, payload);
    }
}