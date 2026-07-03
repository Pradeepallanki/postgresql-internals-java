package com.pradeep.dbdemo.storage;


public class Page {
    public static final int PAGE_SIZE = 8192; // we will enforce page size to be exactly 8192 bytes, so that nobody can alter this.
    private final byte[] data;
    private final int pageId;
    private boolean isDirty;

    private final PageHeader pageHeader; // this header will contain all the metadata about the page

    public Page(int pageId) {
        this(pageId, new byte[PAGE_SIZE], new PageHeader(), false);
    }

    public Page(int pageId, byte[] data, PageHeader pageHeader, boolean isDirty) {
        this.pageId = pageId;
        this.data = data;
        this.pageHeader = pageHeader;
        this.isDirty = isDirty;
    }

    public byte[] getData() {
        return this.data;
    }

    public int getPageId() {
        return this.pageId;
    }

    public PageHeader getPageHeader() {
        return this.pageHeader;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void markDirty() {
        isDirty = true;
    }

    public void markNotDirty() {
        isDirty = false;
    }
}
