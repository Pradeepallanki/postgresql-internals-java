package com.pradeep.dbdemo.storage.btree.internal;

public record BtreeInternalEntry(long separatorKey, int rightChildPageId) {
    public static int SIZE = Long.BYTES + Integer.BYTES;
}
