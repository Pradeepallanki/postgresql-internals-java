package com.pradeep.dbdemo.storage;

import java.nio.ByteBuffer;

public class Slot {
    private short offset;// our page size if 8192 bytes, in Java short can store -327678 - 32767. That's enough to store the offset
    private short length;
    private boolean deleted;
    public static final int SIZE = 5;

    public Slot(short offset, short length, boolean deleted) {
        this.offset = offset;
        this.length = length;
        this.deleted = deleted;
    }

    public void writeTo(ByteBuffer byteBuffer) {
        byteBuffer.putShort(offset);
        byteBuffer.putShort(length);
        byteBuffer.putShort((short) (deleted ? 1 : 0));
    }

    public static Slot readFrom(ByteBuffer byteBuffer) {
        return new Slot(byteBuffer.getShort(), byteBuffer.getShort(), byteBuffer.getShort() == 1);
    }

    public short getLength() {
        return length;
    }

    public short getOffset() {
        return offset;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setLength(short length) {
        this.length = length;
    }

    public void setOffset(short offset) {
        this.offset = offset;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
