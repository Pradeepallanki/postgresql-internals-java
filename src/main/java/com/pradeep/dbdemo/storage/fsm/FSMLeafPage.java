package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;

import java.nio.ByteBuffer;

public class FSMLeafPage {

    private final Page page;
    private final FSMLeafHeader fsmLeafHeader;

    public FSMLeafPage(Page page) {
        this(readHeader(page), page);
    }

    public FSMLeafPage(int firstHeapPageId, Page page) {
        this(writeHeader(firstHeapPageId, page), page);
    }

    public FSMLeafPage(FSMLeafHeader fsmLeafHeader, Page page) {
        this.fsmLeafHeader = fsmLeafHeader;
        this.page = page;
    }

    private static FSMLeafHeader writeHeader(int firstHeapPageId, Page page) {
        FSMLeafHeader fsmLeafHeader = new FSMLeafHeader(firstHeapPageId, 0);

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        fsmLeafHeader.writeTo(buffer);

        page.markDirty();
        return fsmLeafHeader;
    }

    private static FSMLeafHeader readHeader(Page page) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        return FSMLeafHeader.readFrom(buffer);
    }

    public int getFirstHeapPageId() {
        return this.fsmLeafHeader.getFirstHeapPageId();
    }

    public int getEntryCount() {
        return this.fsmLeafHeader.getEntryCount();
    }

    public int getFreeSpace(int heapPageId) {
        int offSet = getOffSet(heapPageId);

        if (offSet < 0) {
            throw new IllegalArgumentException(
                    "Heap page " + heapPageId + " not tracked in this FSM leaf.");
        }

        return readEntryFromOffSet(offSet).getFreeSpace();
    }

    public void updateFreeSpace(int heapPageId, int freeBytes) {
        int offSet = getOffSet(heapPageId);

        if (offSet >= 0) {
            FSMLeafEntry fsmLeafEntry = readEntryFromOffSet(offSet);
            fsmLeafEntry.setFreeSpace((short) freeBytes);
            writeEntryIntoOffSet(fsmLeafEntry, offSet);
            page.markDirty();
            return;
        }

        int expectedNextHeapPageId =
                fsmLeafHeader.getFirstHeapPageId() + fsmLeafHeader.getEntryCount();

        if (heapPageId != expectedNextHeapPageId) {
            throw new IllegalArgumentException(
                    "Heap page " + heapPageId
                            + " is out of order; expected " + expectedNextHeapPageId);
        }

        if (!hasSpace(FSMLeafEntry.SIZE)) {
            throw new IllegalStateException("No space left in the page");
        }

        FSMLeafEntry fsmLeafEntry =
                new FSMLeafEntry(heapPageId, (short) freeBytes);

        int newOffSet = entryOffset(fsmLeafHeader.getEntryCount());

        writeEntryIntoOffSet(fsmLeafEntry, newOffSet);

        fsmLeafHeader.setEntryCount(fsmLeafHeader.getEntryCount() + 1);

        writeHeader();

        page.markDirty();
    }

    public boolean containsHeapPage(int heapPageId) {
        return getOffSet(heapPageId) >= 0;
    }

    public int getMaxFreeSpace() {

        if (fsmLeafHeader.getEntryCount() == 0) {
            return -1;
        }

        int maxSpace = Integer.MIN_VALUE;

        int first = fsmLeafHeader.getFirstHeapPageId();
        int last = first + fsmLeafHeader.getEntryCount() - 1;

        for (int i = first; i <= last; i++) {
            maxSpace = Math.max(
                    maxSpace,
                    readEntryFromOffSet(getOffSet(i)).getFreeSpace());
        }

        return maxSpace;
    }

    private void writeHeader() {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        fsmLeafHeader.writeTo(buffer);
    }

    private void writeEntryIntoOffSet(FSMLeafEntry fsmLeafEntry, int offSet) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(offSet);
        fsmLeafEntry.writeInto(byteBuffer);
    }

    private FSMLeafEntry readEntryFromOffSet(int offSet) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(this.page.getData());
        byteBuffer.position(offSet);
        return FSMLeafEntry.readFrom(byteBuffer);
    }

    private boolean hasSpace(int size) {
        int currentlyOccupiedSpace =
                PageHeader.SIZE
                        + FSMLeafHeader.SIZE
                        + (fsmLeafHeader.getEntryCount() * FSMLeafEntry.SIZE);
        return (Page.PAGE_SIZE - currentlyOccupiedSpace) >= size;
    }

    private int getOffSet(int heapPageId) {
        int first = fsmLeafHeader.getFirstHeapPageId();
        int index = heapPageId - first;

        if (index < 0 || index >= fsmLeafHeader.getEntryCount()) {
            return -1;
        }

        return entryOffset(index);
    }

    private int entryOffset(int index) {
        return PageHeader.SIZE
                + FSMLeafHeader.SIZE
                + (index * FSMLeafEntry.SIZE);
    }

}