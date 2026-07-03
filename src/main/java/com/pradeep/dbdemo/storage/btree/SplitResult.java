package com.pradeep.dbdemo.storage.btree;

public interface SplitResult {
    long separatorKey();

    int newPageId();
}
