package com.pradeep.dbdemo.storage;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class HeapPage {
    private final Page page;

    public HeapPage(Page page) {
        this.page = page;
    }

    public RID insert(byte[] tuple) {
        if (!hasSpace(tuple.length)) {
            throw new IllegalStateException("Page size is full");
        }

        if (tuple.length == 0) {
            throw new IllegalArgumentException("tuple cannot be empty");
        }

        PageHeader pageHeader = page.getPageHeader();
        short freeOffSet = (short) (pageHeader.getFreeSpaceOffSet() - tuple.length); // freeSpaceOffSet is actually set to beginning of the last inserted tuple. before inserting the new tuple, we need to bring the offset to the point where we can insert the tuple
        System.arraycopy(tuple, 0, page.getData(), freeOffSet, tuple.length);

        Slot slot = new Slot(freeOffSet, (short) tuple.length, false);

        int slotNumber = pageHeader.getSlotCount(); // slot number goes like 0,1,2...
        writeSlot(slot, (short) slotNumber);

        pageHeader.setSlotCount(pageHeader.getSlotCount() + 1); // slot count is always prevCount+1
        pageHeader.setFreeSpaceOffSet(freeOffSet);

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        page.getPageHeader().writeTo(buffer);
        this.page.markDirty();
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
        Slot slot = readSlot(rid.slotNumber());
        slot.setDeleted(true);
        writeSlot(slot, rid.slotNumber());
        this.page.markDirty();
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
