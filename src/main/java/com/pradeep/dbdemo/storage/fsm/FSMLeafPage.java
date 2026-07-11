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

    public static FSMLeafPage createFresh(Page page) {
        FSMLeafHeader header = new FSMLeafHeader(0);

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        header.writeTo(buffer);

        page.markDirty();

        return new FSMLeafPage(header, page);
    }

    public FSMLeafPage(FSMLeafHeader fsmLeafHeader, Page page) {
        this.fsmLeafHeader = fsmLeafHeader;
        this.page = page;
    }

    private static FSMLeafHeader readHeader(Page page) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        return FSMLeafHeader.readFrom(buffer);
    }

    public Page getPage() {
        return page;
    }

    public int getEntryCount() {
        return this.fsmLeafHeader.getEntryCount();
    }

    public int getFreeSpace(int heapPageId) {
        int index = findIndex(heapPageId);

        if (index < 0) {
            throw new IllegalArgumentException(
                    "Heap page " + heapPageId + " not tracked in this FSM leaf.");
        }

        return readEntry(index).getFreeSpace();
    }

    public void updateFreeSpace(int heapPageId, int freeBytes) {
        int index = findIndex(heapPageId);

        if (index >= 0) {
            FSMLeafEntry entry = readEntry(index);
            entry.setFreeSpace((short) freeBytes);
            writeEntry(index, entry);
            page.markDirty();
            return;
        }

        if (!hasSpace()) {
            throw new IllegalStateException("No space left in the page");
        }

        int insertAt = -(index + 1);

        shiftEntriesRight(insertAt);

        writeEntry(insertAt, new FSMLeafEntry(heapPageId, (short) freeBytes));

        fsmLeafHeader.setEntryCount(fsmLeafHeader.getEntryCount() + 1);

        writeHeader();

        page.markDirty();
    }

    public boolean containsHeapPage(int heapPageId) {
        return findIndex(heapPageId) >= 0;
    }

    public int getMaxFreeSpace() {
        if (fsmLeafHeader.getEntryCount() == 0) {
            return -1;
        }

        int max = Integer.MIN_VALUE;

        for (int i = 0; i < fsmLeafHeader.getEntryCount(); i++) {
            max = Math.max(max, readEntry(i).getFreeSpace());
        }

        return max;
    }

    public int getPageIdWithAtLeast(int requiredSize) {
        for (int i = 0; i < fsmLeafHeader.getEntryCount(); i++) {
            FSMLeafEntry entry = readEntry(i);
            if (entry.getFreeSpace() >= requiredSize) {
                return entry.getPageId();
            }
        }

        return -1;
    }

    public boolean hasSpace() {
        int currentlyOccupiedSpace =
                PageHeader.SIZE
                        + FSMLeafHeader.SIZE
                        + (fsmLeafHeader.getEntryCount() * FSMLeafEntry.SIZE);
        return (Page.PAGE_SIZE - currentlyOccupiedSpace) >= FSMLeafEntry.SIZE;
    }

    private int findIndex(int heapPageId) {
        int low = 0;
        int high = fsmLeafHeader.getEntryCount() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            int midKey = readEntry(mid).getPageId();

            if (midKey == heapPageId) {
                return mid;
            }

            if (heapPageId < midKey) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return -(low + 1);
    }

    private void shiftEntriesRight(int fromIndex) {
        int count = fsmLeafHeader.getEntryCount();
        int bytesToMove = (count - fromIndex) * FSMLeafEntry.SIZE;

        if (bytesToMove <= 0) {
            return;
        }

        int src = entryOffset(fromIndex);
        int dst = entryOffset(fromIndex + 1);

        System.arraycopy(page.getData(), src, page.getData(), dst, bytesToMove);
    }

    private void writeHeader() {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        fsmLeafHeader.writeTo(buffer);
    }

    private FSMLeafEntry readEntry(int index) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(entryOffset(index));
        return FSMLeafEntry.readFrom(buffer);
    }

    private void writeEntry(int index, FSMLeafEntry entry) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(entryOffset(index));
        entry.writeInto(buffer);
    }

    private int entryOffset(int index) {
        return PageHeader.SIZE
                + FSMLeafHeader.SIZE
                + (index * FSMLeafEntry.SIZE);
    }

}