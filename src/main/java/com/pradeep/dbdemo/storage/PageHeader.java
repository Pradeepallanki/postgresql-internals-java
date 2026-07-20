package com.pradeep.dbdemo.storage;

import java.nio.ByteBuffer;

public class PageHeader {
    private int slotCount = 0;
    private int freeSpaceOffSet = Page.PAGE_SIZE;
    private PageType pageType = PageType.EMPTY;
    private long pageLSN;

    public static final int SIZE = 20; // 3 ints (slotCount, freeSpaceOffSet, pageType) + 1 long (pageLSN)

    public PageHeader() {
    }

    public PageHeader(int slotCount, int freeSpaceOffSet, PageType pageType, long pageLSN) {
        this.slotCount = slotCount;
        this.freeSpaceOffSet = freeSpaceOffSet;
        this.pageType = pageType;
        this.pageLSN = pageLSN;
    }

    public void writeTo(ByteBuffer byteBuffer) {
        byteBuffer.putInt(slotCount);
        byteBuffer.putInt(freeSpaceOffSet);
        byteBuffer.putInt(pageType.ordinal());
        byteBuffer.putLong(pageLSN);
    }

    public static PageHeader readFrom(ByteBuffer byteBuffer) {
        return new PageHeader(byteBuffer.getInt(), byteBuffer.getInt(), PageType.values()[byteBuffer.getInt()], byteBuffer.getLong());
    }

    public int getFreeSpaceOffSet() {
        return freeSpaceOffSet;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public PageType getPageType() {
        return pageType;
    }

    public void setFreeSpaceOffSet(int freeSpaceOffSet) {
        this.freeSpaceOffSet = freeSpaceOffSet;
    }

    public void setSlotCount(int slotCount) {
        this.slotCount = slotCount;
    }

    public void setPageType(PageType pageType) {
        this.pageType = pageType;
    }

    public long getPageLSN() {
        return pageLSN;
    }

    public void setPageLSN(long pageLSN) {
        this.pageLSN = pageLSN;
    }

    public enum PageType {
        EMPTY,
        HEAP,
        BTREE_INTERNAL,
        BTREE_LEAF,
        FSM_LEAF,
        FSM_INTERNAL,
        FSM_META,
        CATALOG
    }
}
