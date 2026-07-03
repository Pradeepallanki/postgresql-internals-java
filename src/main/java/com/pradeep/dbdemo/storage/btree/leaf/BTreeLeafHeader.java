package com.pradeep.dbdemo.storage.btree.leaf;

import java.nio.ByteBuffer;

public class BTreeLeafHeader {
    private short entryCount;
    private int nextLeafPageId;
    private int prevLeafPageId;
    public static int SIZE = Short.BYTES + Integer.BYTES + Integer.BYTES;

    public BTreeLeafHeader() {
        this((short) 0, -1, -1);
    }

    public BTreeLeafHeader(short entryCount, int nextLeafPageId, int prevLeafPageId) {
        this.entryCount = entryCount;
        this.nextLeafPageId = nextLeafPageId;
        this.prevLeafPageId = prevLeafPageId;
    }

    public void writeTo(ByteBuffer byteBuffer) {
        byteBuffer.putShort(entryCount);
        byteBuffer.putInt(nextLeafPageId);
        byteBuffer.putInt(prevLeafPageId);
    }

    public static BTreeLeafHeader readFrom(ByteBuffer byteBuffer) {
        return new BTreeLeafHeader(byteBuffer.getShort(), byteBuffer.getInt(), byteBuffer.getInt());
    }


    public short getEntryCount() {
        return entryCount;
    }

    public int getNextLeafPageId() {
        return nextLeafPageId;
    }

    public int getPrevLeafPageId() {
        return prevLeafPageId;
    }


    public void setNextLeafPageId(int nextLeafPageId) {
        this.nextLeafPageId = nextLeafPageId;
    }

    public void setEntryCount(short entryCount) {
        this.entryCount = entryCount;
    }

    public void setPrevLeafPageId(int prevLeafPageId) {
        this.prevLeafPageId = prevLeafPageId;
    }
}
