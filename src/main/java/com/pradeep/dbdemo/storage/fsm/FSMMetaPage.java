package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;

import java.nio.ByteBuffer;

public class FSMMetaPage {

    public static final int INVALID_PAGE_ID = -1;

    public static final int SIZE = Integer.BYTES * 3;

    private static final int ROOT_OFFSET   = PageHeader.SIZE;
    private static final int HEIGHT_OFFSET = ROOT_OFFSET + Integer.BYTES;
    private static final int NEXT_OFFSET   = HEIGHT_OFFSET + Integer.BYTES;

    private final Page page;
    private int rootPageId;
    private int treeHeight;
    private int nextFSMPageId;

    public FSMMetaPage(Page page) {
        this.page = page;

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(ROOT_OFFSET);

        this.rootPageId    = buffer.getInt();
        this.treeHeight    = buffer.getInt();
        this.nextFSMPageId = buffer.getInt();
    }

    public static FSMMetaPage createFresh(Page page, int firstFSMPageId) {
        FSMMetaPage meta = new FSMMetaPage(page);

        meta.rootPageId    = INVALID_PAGE_ID;
        meta.treeHeight    = 0;
        meta.nextFSMPageId = firstFSMPageId;

        meta.writeAll();

        return meta;
    }

    public int getRootPageId() {
        return rootPageId;
    }

    public int getTreeHeight() {
        return treeHeight;
    }

    public int getNextFSMPageId() {
        return nextFSMPageId;
    }

    public void setRootPageId(int rootPageId) {
        this.rootPageId = rootPageId;
        writeInt(ROOT_OFFSET, rootPageId);
        page.markDirty();
    }

    public void setTreeHeight(int treeHeight) {
        this.treeHeight = treeHeight;
        writeInt(HEIGHT_OFFSET, treeHeight);
        page.markDirty();
    }

    public int reserveNextFSMPageId() {
        int allocated = nextFSMPageId;

        nextFSMPageId++;

        writeInt(NEXT_OFFSET, nextFSMPageId);
        page.markDirty();

        return allocated;
    }

    private void writeAll() {
        writeInt(ROOT_OFFSET,   rootPageId);
        writeInt(HEIGHT_OFFSET, treeHeight);
        writeInt(NEXT_OFFSET,   nextFSMPageId);

        page.markDirty();
    }

    private void writeInt(int offset, int value) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(offset);
        buffer.putInt(value);
    }
}