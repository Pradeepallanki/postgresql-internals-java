package com.pradeep.dbdemo.storage.btree.leaf;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.RID;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class BTreeLeafPage {
    /*
     * Leaf node layout (conceptual)
     *
     * +------------------------------+
     * | PageHeader                   |
     * +------------------------------+
     * | previous leaf pageId (int)   |
     * +------------------------------+
     * | next leaf pageId (int)       |
     * +------------------------------+
     * | key0                         |
     * +------------------------------+
     * | RID0                         |
     * +------------------------------+
     * | key1                         |
     * +------------------------------+
     * | RID1                         |
     * +------------------------------+
     */

    private final Page page;
    private final BTreeLeafHeader bTreeLeafHeader;
    private final BufferPool bufferPool;

    public static final int KEY_SIZE = Long.BYTES;
    public static final int RID_SIZE = Integer.BYTES + Short.BYTES;

    public BTreeLeafPage(Page page, BufferPool bufferPool) {
        this(page, readHeader(page), bufferPool);
    }

    private static BTreeLeafHeader readHeader(Page page) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.position(PageHeader.SIZE);
        return BTreeLeafHeader.readFrom(buffer);
    }

    public BTreeLeafPage(Page page, BTreeLeafHeader bTreeLeafHeader, BufferPool bufferPool) {
        if (page.getPageHeader().getPageType() != PageHeader.PageType.BTREE_LEAF) {
            throw new IllegalArgumentException("Page type should be BTree leaf type");
        }
        this.page = page;
        this.bTreeLeafHeader = bTreeLeafHeader;
        this.bufferPool = bufferPool;
    }

    public static int maxEntries() {
        int fixedOverHead = PageHeader.SIZE + BTreeLeafHeader.SIZE;

        return (Page.PAGE_SIZE - fixedOverHead) / (KEY_SIZE + RID_SIZE);
    }

    public Page getPage() {
        return page;
    }

    public BTreeLeafHeader getbTreeLeafHeader() {
        return bTreeLeafHeader;
    }

    public LeafSplitResult insert(long key, RID rid) throws IOException {
        if (hasSpace()) {
            shiftAndInsert(key, rid);
            return null;
        }

        List<BtreeLeafEntry> list = new ArrayList<>();

        for (int i = 0; i < bTreeLeafHeader.getEntryCount(); i++) {
            list.add(readEntry(i));
        }

        list.add(new BtreeLeafEntry(key, rid));

        list.sort(Comparator.comparingLong(BtreeLeafEntry::key));

        int sliceIndex = list.size() / 2;

        List<BtreeLeafEntry> leftEntries =
                new ArrayList<>(list.subList(0, sliceIndex));

        List<BtreeLeafEntry> rightEntries =
                new ArrayList<>(list.subList(sliceIndex, list.size()));

        int newPageId = bufferPool.allocatePage();

        Page newPage = bufferPool.fetchPage(newPageId);

        newPage.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_LEAF);

        int oldNext = bTreeLeafHeader.getNextLeafPageId();

        bTreeLeafHeader.setNextLeafPageId(newPageId);

        BTreeLeafHeader newHeader =
                new BTreeLeafHeader(
                        (short) 0,
                        oldNext,
                        page.getPageId()
                );

        if (oldNext != -1) {

            Page nextPage =
                    bufferPool.fetchPage(oldNext);

            BTreeLeafPage nextLeaf =
                    new BTreeLeafPage(nextPage, bufferPool);

            nextLeaf.getbTreeLeafHeader()
                    .setPrevLeafPageId(newPageId);

            nextLeaf.writeHeader();

            nextPage.markDirty();
        }

        rewriteEntries(
                page,
                bTreeLeafHeader,
                leftEntries
        );

        rewriteEntries(
                newPage,
                newHeader,
                rightEntries
        );

        page.markDirty();
        newPage.markDirty();

        return new LeafSplitResult(
                rightEntries.getFirst().key(),
                newPageId
        );
    }

    private void rewriteEntries(
            Page targetPage,
            BTreeLeafHeader header,
            List<BtreeLeafEntry> entries) {

        clearEntries(targetPage);

        header.setEntryCount((short) entries.size());

        writeHeader(targetPage, header);

        for (int i = 0; i < entries.size(); i++) {
            writeEntry(targetPage, i, entries.get(i));
        }
    }

    private void clearEntries(Page targetPage) {

        int start = PageHeader.SIZE + BTreeLeafHeader.SIZE;

        Arrays.fill(
                targetPage.getData(),
                start,
                targetPage.getData().length,
                (byte) 0
        );
    }

    private void writeHeader(
            Page targetPage,
            BTreeLeafHeader header) {

        ByteBuffer buffer =
                ByteBuffer.wrap(targetPage.getData());

        buffer.position(PageHeader.SIZE);

        header.writeTo(buffer);
    }

    private void writeEntry(
            Page targetPage,
            int index,
            BtreeLeafEntry entry) {

        ByteBuffer buffer =
                ByteBuffer.wrap(targetPage.getData());

        buffer.position(entryOffset(index));

        buffer.putLong(entry.key());

        buffer.putInt(entry.rid().pageId());

        buffer.putShort(entry.rid().slotNumber());
    }


    private void shiftAndInsert(long key, RID rid) {

        int low = 0;
        int high = bTreeLeafHeader.getEntryCount() - 1;

        int indexToInsert = -1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            BtreeLeafEntry bTreeLeafEntry = readEntry(mid);
            if (bTreeLeafEntry.key() == key) {
                throw new IllegalArgumentException("Duplicate keys are not supported.");

            }

            if (key < bTreeLeafEntry.key()) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        indexToInsert = low;

        int insertionLength = BtreeLeafEntry.SIZE;
        int insertionPos = entryOffset(indexToInsert);

        System.arraycopy(
                page.getData(),
                insertionPos,
                page.getData(),
                insertionPos + insertionLength,
                (bTreeLeafHeader.getEntryCount() - indexToInsert) * BtreeLeafEntry.SIZE
        );

        BtreeLeafEntry entry = new BtreeLeafEntry(key, rid);

        writeEntry(indexToInsert, entry);

        this.bTreeLeafHeader.setEntryCount((short) (this.bTreeLeafHeader.getEntryCount() + 1));
        writeHeader();

        page.markDirty();
    }

    public RID search(long key) {
        int low = 0;
        int high = bTreeLeafHeader.getEntryCount() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            BtreeLeafEntry bTreeLeafEntry = readEntry(mid);

            if (bTreeLeafEntry.key() == key) {
                return bTreeLeafEntry.rid();
            }

            if (key < bTreeLeafEntry.key()) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return null;
    }

    public boolean hasSpace() {
        return bTreeLeafHeader.getEntryCount() < maxEntries();
    }

    public BtreeLeafEntry readEntry(int index) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(page.getData());
        byteBuffer.position(entryOffset(index));

        return new BtreeLeafEntry(byteBuffer.getLong(), new RID(byteBuffer.getInt(), byteBuffer.getShort()));
    }

    private int entryOffset(int index) {
        int startOffset = PageHeader.SIZE + BTreeLeafHeader.SIZE;
        int eachEntrySize = BtreeLeafEntry.SIZE;
        return startOffset + (eachEntrySize * index);
    }

    public void writeEntry(int index, BtreeLeafEntry btreeLeafEntry) {
        writeEntry(page, index, btreeLeafEntry);
    }

    public void writeHeader() {

        ByteBuffer buffer =
                ByteBuffer.wrap(page.getData());

        buffer.position(PageHeader.SIZE);

        bTreeLeafHeader.writeTo(buffer);
    }


}
