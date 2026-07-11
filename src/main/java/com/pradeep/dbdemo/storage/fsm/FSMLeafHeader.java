package com.pradeep.dbdemo.storage.fsm;

import java.nio.ByteBuffer;

public class FSMLeafHeader {
    private int entryCount;
    public static int SIZE = Integer.BYTES;

    public FSMLeafHeader() {
        this(0);
    }

    public FSMLeafHeader(int entryCount) {
        this.entryCount = entryCount;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putInt(entryCount);
    }

    public static FSMLeafHeader readFrom(ByteBuffer byteBuffer) {
        return new FSMLeafHeader(byteBuffer.getInt());
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

}