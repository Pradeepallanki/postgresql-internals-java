package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;

import java.nio.ByteBuffer;

public class FSMInternalPage {
    private final Page page;
    private final FSMInternalHeader fsmInternalHeader;

    public FSMInternalPage(Page page) {
        this(page, readHeader(page));
    }

    public FSMInternalPage(Page page, FSMInternalHeader fsmInternalHeader) {
        this.page = page;
        this.fsmInternalHeader = fsmInternalHeader;
    }

    public static FSMInternalPage createFresh(Page page) {
        FSMInternalHeader header = new FSMInternalHeader(0);

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        header.writeInto(buffer);

        page.markDirty();

        return new FSMInternalPage(page, header);
    }

    private static FSMInternalHeader readHeader(Page page) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(PageHeader.SIZE);
        return FSMInternalHeader.readFrom(byteBuffer);
    }

    public int getEntryCount() {
        return this.fsmInternalHeader.getEntryCount();
    }

    public void updateChildSummary(int childPageId, int maxFreeSpace) {
        int index = findChildIndex(childPageId);

        if (index >= 0) {
            FSMInternalEntry existing = readEntry(index);
            existing.setFreeSpace((short) maxFreeSpace);
            writeEntry(index, existing);
            page.markDirty();
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

        page.markDirty();
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