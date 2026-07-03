package com.pradeep.dbdemo.storage.btree;

public class BTreeMetadata {
    private int rootPageId = -1;

    public BTreeMetadata(int rootPageId) {
        this.rootPageId = rootPageId;
    }

    public int getRootPageId() {
        return rootPageId;
    }

    public void setRootPageId(int rootPageId) {
        this.rootPageId = rootPageId;
    }
}
