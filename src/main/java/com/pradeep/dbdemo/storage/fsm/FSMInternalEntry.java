package com.pradeep.dbdemo.storage.fsm;

import java.nio.ByteBuffer;

public class FSMInternalEntry {
    public static int SIZE = Integer.BYTES + Short.BYTES;

    private int childPageId;
    private short freeSpace;

    public FSMInternalEntry(int childPageId, short freeSpace) {
        this.childPageId = childPageId;
        this.freeSpace = freeSpace;
    }

    public void setChildPageId(int childPageId) {
        this.childPageId = childPageId;
    }

    public void setFreeSpace(short freeSpace) {
        this.freeSpace = freeSpace;
    }

    public int getChildPageId() {
        return childPageId;
    }

    public short getFreeSpace() {
        return freeSpace;
    }

    public void writeInto(ByteBuffer byteBuffer) {
        byteBuffer.putInt(childPageId);
        byteBuffer.putShort(freeSpace);
    }

    public static FSMInternalEntry readFrom(ByteBuffer byteBuffer) {
        return new FSMInternalEntry(byteBuffer.getInt(), byteBuffer.getShort());
    }
}
