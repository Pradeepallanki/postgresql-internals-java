package com.pradeep.dbdemo.storage.btree.internal;

import java.nio.ByteBuffer;

public class BtreeInternalHeader {
    private short entryCount;
    private int leftMostChildPageId;
    public static final int SIZE = Short.BYTES + Integer.BYTES;

    public BtreeInternalHeader(short entryCount, int leftMostChildPageId) {
        this.entryCount = entryCount;
        this.leftMostChildPageId = leftMostChildPageId;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putShort(entryCount);
        buffer.putInt(leftMostChildPageId);
    }

    public static BtreeInternalHeader readFrom(ByteBuffer buffer) {
        return new BtreeInternalHeader(buffer.getShort(), buffer.getInt());
    }

    public int getLeftMostChildPageId() {
        return leftMostChildPageId;
    }

    public short getEntryCount() {
        return entryCount;
    }

    public void setLeftMostChildPageId(int leftMostChildPageId) {
        this.leftMostChildPageId = leftMostChildPageId;
    }

    public void setEntryCount(short entryCount) {
        this.entryCount = entryCount;
    }
}
