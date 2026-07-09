package com.pradeep.dbdemo.storage.fsm;

import java.nio.ByteBuffer;

public class FSMLeafHeader {
    private int firstHeapPageId;
    private int entryCount;
    public static int SIZE = Integer.BYTES + Integer.BYTES;

    public FSMLeafHeader(int firstHeapPageId) {
        this(firstHeapPageId, 0);
    }

    public FSMLeafHeader(int firstHeapPageId, int entryCount) {
        this.firstHeapPageId = firstHeapPageId;
        this.entryCount = entryCount;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putInt(firstHeapPageId);
        buffer.putInt(entryCount);
    }

    public static FSMLeafHeader readFrom(ByteBuffer byteBuffer) {
        return new FSMLeafHeader(byteBuffer.getInt(), byteBuffer.getInt());
    }

    public int getFirstHeapPageId() {
        return firstHeapPageId;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

}
