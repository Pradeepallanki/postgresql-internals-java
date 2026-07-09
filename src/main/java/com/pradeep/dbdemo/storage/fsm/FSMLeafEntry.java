package com.pradeep.dbdemo.storage.fsm;

import java.nio.ByteBuffer;

public class FSMLeafEntry {
    private int pageId;
    private short freeSpace;

    public static int SIZE = Integer.BYTES + Short.BYTES;

    public FSMLeafEntry(int pageId, short freeSpace) {
        this.pageId = pageId;
        this.freeSpace = freeSpace;
    }

    public void writeInto(ByteBuffer byteBuffer) {
        byteBuffer.putInt(this.pageId);
        byteBuffer.putShort(this.freeSpace);
    }

    public static FSMLeafEntry readFrom(ByteBuffer byteBuffer) {
        return new FSMLeafEntry(byteBuffer.getInt(), byteBuffer.getShort());
    }

    public int getFreeSpace() {
        return freeSpace;
    }

    public int getPageId() {
        return pageId;
    }

    public void setFreeSpace(short freeSpace) {
        this.freeSpace = freeSpace;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }
}
