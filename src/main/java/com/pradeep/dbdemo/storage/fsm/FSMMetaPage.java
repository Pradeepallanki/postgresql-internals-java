package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.wal.WalOperation;

import java.nio.ByteBuffer;

public class FSMMetaPage {

    public static final int INVALID_PAGE_ID = -1;

    public static final int SIZE = Integer.BYTES * 3;

    private static final int ROOT_OFFSET   = PageHeader.SIZE;
    private static final int HEIGHT_OFFSET = ROOT_OFFSET + Integer.BYTES;
    private static final int NEXT_OFFSET   = HEIGHT_OFFSET + Integer.BYTES;

    private final Page page;
    private final BufferPool bufferPool;
    private int rootPageId;
    private int treeHeight;
    private int nextFSMPageId;

    public FSMMetaPage(Page page, BufferPool bufferPool) {
        this.page = page;
        this.bufferPool = bufferPool;

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(ROOT_OFFSET);

        this.rootPageId    = buffer.getInt();
        this.treeHeight    = buffer.getInt();
        this.nextFSMPageId = buffer.getInt();
    }

    public static FSMMetaPage createFresh(Page page, int firstFSMPageId, BufferPool bufferPool) {
        FSMMetaPage meta = new FSMMetaPage(page, bufferPool);

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
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);
        this.rootPageId = rootPageId;
        writeInt(ROOT_OFFSET, rootPageId);
        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public void setTreeHeight(int treeHeight) {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);
        this.treeHeight = treeHeight;
        writeInt(HEIGHT_OFFSET, treeHeight);
        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public int reserveNextFSMPageId() {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);
        int allocated = nextFSMPageId;

        nextFSMPageId++;

        writeInt(NEXT_OFFSET, nextFSMPageId);
        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);

        return allocated;
    }

    private void writeAll() {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);
        writeInt(ROOT_OFFSET,   rootPageId);
        writeInt(HEIGHT_OFFSET, treeHeight);
        writeInt(NEXT_OFFSET,   nextFSMPageId);

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    private void writeInt(int offset, int value) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(offset);
        buffer.putInt(value);
    }
}