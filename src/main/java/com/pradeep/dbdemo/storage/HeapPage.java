package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.wal.WalOperation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HeapPage {
    private final Page page;
    private final BufferPool bufferPool;

    public HeapPage(Page page, BufferPool bufferPool) {
        this.page = page;
        this.bufferPool = bufferPool;
    }

    public RID insert(byte[] tuple) {
        if (tuple.length == 0) {
            throw new IllegalArgumentException("tuple cannot be empty");
        }

        if (!hasSpace(tuple.length)) {
            // gaps left by deletes aren't reflected in freeSpaceOffSet — try to reclaim them before giving up.
            compact();
            if (!hasSpace(tuple.length)) {
                throw new IllegalStateException("Page size is full");
            }
        }

        // WAL-first: append the record (which mints the LSN), then mutate, then stamp pageLSN + mark dirty.
        long lsn = bufferPool.log(page.getPageId(), WalOperation.INSERT_TUPLE, tuple);

        PageHeader pageHeader = page.getPageHeader();
        short freeOffSet = (short) (pageHeader.getFreeSpaceOffSet() - tuple.length); // freeSpaceOffSet is actually set to beginning of the last inserted tuple. before inserting the new tuple, we need to bring the offset to the point where we can insert the tuple
        System.arraycopy(tuple, 0, page.getData(), freeOffSet, tuple.length);

        Slot slot = new Slot(freeOffSet, (short) tuple.length, false);

        int slotNumber = pageHeader.getSlotCount(); // slot number goes like 0,1,2...
        writeSlot(slot, (short) slotNumber);

        pageHeader.setSlotCount(pageHeader.getSlotCount() + 1); // slot count is always prevCount+1
        pageHeader.setFreeSpaceOffSet(freeOffSet);

        pageHeader.setPageLSN(lsn);
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        page.getPageHeader().writeTo(buffer);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
        return new RID(page.getPageId(), (short) slotNumber);
    }

    public byte[] read(RID rid) {
        validateRID(rid);
        Slot slot = readSlot(rid.slotNumber());

        if (slot.isDeleted()) {
            return null;
        }

        byte[] data = page.getData();
        return Arrays.copyOfRange(data, slot.getOffset(), slot.getOffset() + slot.getLength());
    }

    public boolean delete(RID rid) {
        validateRID(rid);
        long lsn = bufferPool.log(page.getPageId(), WalOperation.DELETE_TUPLE, new byte[0]);
        Slot slot = readSlot(rid.slotNumber());
        slot.setDeleted(true);
        writeSlot(slot, rid.slotNumber());
        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
        return true;
    }

    private void validateRID(RID rid) {

        if (rid.pageId() != page.getPageId()) {
            throw new IllegalArgumentException(
                    "RID belongs to another page.");
        }

        if (rid.slotNumber() < 0 ||
                rid.slotNumber() >= page.getPageHeader().getSlotCount()) {

            throw new IllegalArgumentException(
                    "Invalid slot.");
        }
    }

    public boolean hasSpace(int tupleSize) {
        int slotCount = page.getPageHeader().getSlotCount();
        int slotDirectoryEnd = PageHeader.SIZE + (slotCount * Slot.SIZE); // you want to know which is the lastOffset you need to read to know about the last slot written.

        int freeBytes = page.getPageHeader().getFreeSpaceOffSet() - slotDirectoryEnd;

        return freeBytes >= (tupleSize + Slot.SIZE);
    }

    public Integer getFreeBytes() {
        int slotCount = page.getPageHeader().getSlotCount();
        int slotDirectoryEnd = PageHeader.SIZE + (slotCount * Slot.SIZE); // you want to know which is the lastOffset you need to read to know about the last slot written.

        return page.getPageHeader().getFreeSpaceOffSet() - slotDirectoryEnd;
    }

    public static Integer getTotalRequiredSpace(int tupleSize) {
        return tupleSize + Slot.SIZE;
    }

    public void compact() {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);
        PageHeader pageHeader = page.getPageHeader();
        int slotCount = pageHeader.getSlotCount();
        byte[] data = page.getData();

        List<Slot> liveSlots = new ArrayList<>();
        List<Short> liveSlotNumbers = new ArrayList<>();
        for (short i = 0; i < slotCount; i++) {
            Slot slot = readSlot(i);
            if (!slot.isDeleted()) {
                liveSlots.add(slot);
                liveSlotNumbers.add(i);
            }
        }

        // walk tuples from highest current offset to lowest, so every move goes upward toward PAGE_SIZE and can never overwrite a tuple we still need to move.
        Integer[] order = new Integer[liveSlots.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Short.compare(liveSlots.get(b).getOffset(), liveSlots.get(a).getOffset()));

        int newFreeSpaceOffSet = Page.PAGE_SIZE;
        for (int idx : order) {
            Slot slot = liveSlots.get(idx);
            short length = slot.getLength();
            short newOffset = (short) (newFreeSpaceOffSet - length);
            if (newOffset != slot.getOffset()) {
                System.arraycopy(data, slot.getOffset(), data, newOffset, length);
                slot.setOffset(newOffset);
                writeSlot(slot, liveSlotNumbers.get(idx));
            }
            newFreeSpaceOffSet = newOffset;
        }

        pageHeader.setFreeSpaceOffSet(newFreeSpaceOffSet);
        pageHeader.setPageLSN(lsn);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        pageHeader.writeTo(buffer);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }


    private int getSlotOffSet(short slotNumber) {
        return PageHeader.SIZE + (slotNumber * Slot.SIZE);
    }

    private Slot readSlot(short slotNumber) {
        int offSet = getSlotOffSet(slotNumber);
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(offSet);
        return Slot.readFrom(byteBuffer);
    }

    private void writeSlot(Slot slot, short slotNumber) {
        int offSet = getSlotOffSet(slotNumber);
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(offSet);
        slot.writeTo(byteBuffer);
    }
}
