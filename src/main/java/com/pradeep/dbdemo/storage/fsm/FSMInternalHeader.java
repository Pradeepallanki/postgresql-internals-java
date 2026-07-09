package com.pradeep.dbdemo.storage.fsm;

import java.nio.ByteBuffer;

public class FSMInternalHeader {
    private int entryCount;
    public static int SIZE = Integer.BYTES;

    public FSMInternalHeader() {
        this(0);
    }

    public FSMInternalHeader(int entryCount) {
        this.entryCount = entryCount;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public void writeInto(ByteBuffer byteBuffer) {
        byteBuffer.putInt(entryCount);
    }

    public static FSMInternalHeader readFrom(ByteBuffer byteBuffer) {
        return new FSMInternalHeader(byteBuffer.getInt());
    }
}
