package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.wal.WalOperation;

import java.nio.ByteBuffer;

public class FSMInternalPage {
    private final Page page;
    private final FSMInternalHeader fsmInternalHeader;
    private final BufferPool bufferPool;

    public FSMInternalPage(Page page, BufferPool bufferPool) {
        this(page, readHeader(page), bufferPool);
    }

    public FSMInternalPage(Page page, FSMInternalHeader fsmInternalHeader, BufferPool bufferPool) {
        this.page = page;
        this.fsmInternalHeader = fsmInternalHeader;
        this.bufferPool = bufferPool;
    }

    public static FSMInternalPage createFresh(Page page, BufferPool bufferPool) {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);

        FSMInternalHeader header = new FSMInternalHeader(0);

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        header.writeInto(buffer);

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);

        return new FSMInternalPage(page, header, bufferPool);
    }

    private static FSMInternalHeader readHeader(Page page) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(PageHeader.SIZE);
        return FSMInternalHeader.readFrom(byteBuffer);
    }

    public Page getPage() {
        return page;
    }

    public int getEntryCount() {
        return this.fsmInternalHeader.getEntryCount();
    }

    public void updateChildSummary(int childPageId, int maxFreeSpace) {
        int index = findChildIndex(childPageId);

        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);

        if (index >= 0) {
            FSMInternalEntry existing = readEntry(index);
            existing.setFreeSpace((short) maxFreeSpace);
            writeEntry(index, existing);
            page.getPageHeader().setPageLSN(lsn);
            bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
            return;
        }

        if (!hasSpace()) {
            throw new IllegalStateException("FSM internal page is full");
        }

        int newIndex = fsmInternalHeader.getEntryCount();

        writeEntry(
                newIndex,
                new FSMInternalEntry(childPageId, (short) maxFreeSpace)
        );

        fsmInternalHeader.setEntryCount(newIndex + 1);

        writeHeader();

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public int getChildSummary(int childPageId) {
        int childIndex = findChildIndex(childPageId);

        if (childIndex < 0) {
            throw new IllegalArgumentException("entry not found");
        }

        return readEntry(childIndex).getFreeSpace();
    }

    public int getChildPageId(int index) {
        if (index < 0 || index >= fsmInternalHeader.getEntryCount()) {
            throw new IllegalArgumentException("index not found");
        }

        return readEntry(index).getChildPageId();
    }

    public int getMaxSummary() {

        if (fsmInternalHeader.getEntryCount() == 0) {
            return -1;
        }

        int max = Integer.MIN_VALUE;

        for (int i = 0; i < fsmInternalHeader.getEntryCount(); i++) {
            max = Math.max(max, readEntry(i).getFreeSpace());
        }

        return max;
    }

    public int findChildIdWithAtLeast(int size) {
        if (fsmInternalHeader.getEntryCount() == 0) {
            return -1;
        }

        for (int i = 0; i < fsmInternalHeader.getEntryCount(); i++) {
            if (readEntry(i).getFreeSpace() >= size) {
                return getChildPageId(i);
            }
        }

        return -1;
    }

    public boolean containsChild(int childPageId) {
        return findChildIndex(childPageId) >= 0;
    }

    private void writeHeader() {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        fsmInternalHeader.writeInto(buffer);
    }

    private boolean hasSpace() {
        return fsmInternalHeader.getEntryCount() < maxEntry();
    }

    private int findChildIndex(int childPageId) {
        for (int i = 0; i < fsmInternalHeader.getEntryCount(); i++) {
            if (readEntry(i).getChildPageId() == childPageId) {
                return i;
            }
        }
        return -1;
    }

    private FSMInternalEntry readEntry(int index) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(entryOffset(index));
        return FSMInternalEntry.readFrom(byteBuffer);
    }

    private void writeEntry(int index, FSMInternalEntry fsmInternalEntry) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(entryOffset(index));
        fsmInternalEntry.writeInto(byteBuffer);
    }

    private int entryOffset(int index) {
        return PageHeader.SIZE
                + FSMInternalHeader.SIZE
                + index * FSMInternalEntry.SIZE;
    }

    private int maxEntry() {
        return (Page.PAGE_SIZE - (PageHeader.SIZE + FSMInternalHeader.SIZE))
                / FSMInternalEntry.SIZE;
    }

}