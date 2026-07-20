package com.pradeep.dbdemo.storage.btree.internal;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.wal.WalOperation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class BTreeInternalPage {

    /*
     * Internal node layout (conceptual)
     *
     * +-------------------------------+
     * | PageHeader                    |
     * +-------------------------------+
     * | child0 (4 bytes)              |
     * +-------------------------------+
     * | key0   (8 bytes)              |
     * +-------------------------------+
     * | child1                        |
     * +-------------------------------+
     * | key1                          |
     * +-------------------------------+
     * | child2                        |
     * +-------------------------------+
     *
     * Number of children = number of keys + 1
     */


    private final Page page;
    private final BtreeInternalHeader btreeInternalHeader;
    private final BufferPool bufferPool;

    public BTreeInternalPage(Page page, BufferPool bufferPool) {
        this(page, readHeader(page), bufferPool);
    }

    public BTreeInternalPage(Page page, BtreeInternalHeader btreeInternalHeader, BufferPool bufferPool) {
        if (page.getPageHeader().getPageType() != PageHeader.PageType.BTREE_INTERNAL) {
            throw new IllegalArgumentException("Page type should be BTree leaf type");
        }
        this.page = page;
        this.btreeInternalHeader = btreeInternalHeader;
        this.bufferPool = bufferPool;
    }

    public InternalSplitResult insertSeparator(long separatorKey,
                                               int rightChildPageId) throws IOException {
        if (hasSpace()) {
            shiftAndInsert(separatorKey, rightChildPageId);
            return null;
        }

        List<BtreeInternalEntry> entries = new ArrayList<>();

        for (int i = 0; i < btreeInternalHeader.getEntryCount(); i++) {
            entries.add(readEntry(i));
        }

        entries.add(
                new BtreeInternalEntry(
                        separatorKey,
                        rightChildPageId
                )
        );

        entries.sort(
                Comparator.comparingLong(
                        BtreeInternalEntry::separatorKey
                )
        );

        int middle = entries.size() / 2;

        BtreeInternalEntry promoted =
                entries.get(middle);

        List<BtreeInternalEntry> leftEntries =
                new ArrayList<>(
                        entries.subList(0, middle)
                );

        List<BtreeInternalEntry> rightEntries =
                new ArrayList<>(
                        entries.subList(
                                middle + 1,
                                entries.size()
                        )
                );

        int newPageId =
                bufferPool.allocatePage();

        Page newPage =
                bufferPool.fetchPage(newPageId);

        newPage.getPageHeader()
                .setPageType(
                        PageHeader.PageType.BTREE_INTERNAL
                );

        BtreeInternalHeader newHeader =
                new BtreeInternalHeader(
                        (short) 0,
                        promoted.rightChildPageId()
                );

        long leftLsn = bufferPool.log(page.getPageId(), WalOperation.BTREE_SPLIT, new byte[0]);
        long rightLsn = bufferPool.log(newPage.getPageId(), WalOperation.BTREE_SPLIT, new byte[0]);

        rewriteEntries(
                page,
                btreeInternalHeader,
                leftEntries
        );

        rewriteEntries(
                newPage,
                newHeader,
                rightEntries
        );

        page.getPageHeader().setPageLSN(leftLsn);
        newPage.getPageHeader().setPageLSN(rightLsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), leftLsn);
        bufferPool.markDirtyAtLsn(newPage.getPageId(), rightLsn);

        return new InternalSplitResult(
                promoted.separatorKey(),
                newPageId
        );
    }

    private void rewriteEntries(
            Page targetPage,
            BtreeInternalHeader header,
            List<BtreeInternalEntry> entries) {

        clearEntries(targetPage);

        header.setEntryCount((short) entries.size());

        writeHeader(targetPage, header);

        for (int i = 0; i < entries.size(); i++) {
            writeEntry(targetPage, i, entries.get(i));
        }
    }

    private void writeEntry(
            Page targetPage,
            int index,
            BtreeInternalEntry entry) {

        ByteBuffer buffer =
                ByteBuffer.wrap(targetPage.getData());

        buffer.position(entryOffset(index));

        buffer.putLong(entry.separatorKey());

        buffer.putInt(entry.rightChildPageId());
    }

    private void clearEntries(Page targetPage) {

        int start = PageHeader.SIZE + BtreeInternalHeader.SIZE;

        Arrays.fill(
                targetPage.getData(),
                start,
                targetPage.getData().length,
                (byte) 0
        );
    }

    private void writeHeader(
            Page targetPage,
            BtreeInternalHeader header) {

        ByteBuffer buffer =
                ByteBuffer.wrap(targetPage.getData());

        buffer.position(PageHeader.SIZE);

        header.writeTo(buffer);
    }

    public void shiftAndInsert(long separatorKey,
                               int rightChildPageId) {

        if (!hasSpace()) {
            throw new IllegalStateException("Internal page is full.");
        }

        long lsn = bufferPool.log(page.getPageId(), WalOperation.INSERT_TUPLE, new byte[0]);

        int low = 0;
        int high = btreeInternalHeader.getEntryCount() - 1;

        while (low <= high) {

            int mid = low + (high - low) / 2;

            BtreeInternalEntry entry = readEntry(mid);

            if (entry.separatorKey() == separatorKey) {
                throw new IllegalArgumentException(
                        "Duplicate separator keys are not supported.");
            }

            if (separatorKey < entry.separatorKey()) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        int insertIndex = low;

        int insertionOffset = entryOffset(insertIndex);

        int bytesToMove =
                (btreeInternalHeader.getEntryCount() - insertIndex)
                        * BtreeInternalEntry.SIZE;

        System.arraycopy(
                page.getData(),
                insertionOffset,
                page.getData(),
                insertionOffset + BtreeInternalEntry.SIZE,
                bytesToMove
        );

        writeEntry(
                insertIndex,
                new BtreeInternalEntry(
                        separatorKey,
                        rightChildPageId
                )
        );

        btreeInternalHeader.setEntryCount(
                (short) (btreeInternalHeader.getEntryCount() + 1)
        );

        writeHeader();

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public int findChild(long key) {
        int low = 0;
        int high = btreeInternalHeader.getEntryCount() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;

            BtreeInternalEntry entry = readEntry(mid);

            if (key < entry.separatorKey()) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        if (low == 0) {
            return btreeInternalHeader.getLeftMostChildPageId();
        }

        return readEntry(low - 1).rightChildPageId();
    }

    public int findChildIndex(long key) {

        int low = 0;
        int high = btreeInternalHeader.getEntryCount() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;

            BtreeInternalEntry entry = readEntry(mid);

            if (key < entry.separatorKey()) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return low;
    }

    public int childPageIdAt(int index) {

        if (index == 0) {
            return btreeInternalHeader.getLeftMostChildPageId();
        }

        return readEntry(index - 1).rightChildPageId();
    }

    public void removeEntry(int index) {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.DELETE_TUPLE, new byte[0]);

        int count = btreeInternalHeader.getEntryCount();

        int bytesToMove =
                (count - index - 1) * BtreeInternalEntry.SIZE;

        if (bytesToMove > 0) {
            System.arraycopy(
                    page.getData(),
                    entryOffset(index + 1),
                    page.getData(),
                    entryOffset(index),
                    bytesToMove
            );
        }

        Arrays.fill(
                page.getData(),
                entryOffset(count - 1),
                entryOffset(count),
                (byte) 0
        );

        btreeInternalHeader.setEntryCount((short) (count - 1));

        writeHeader();

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public void updateSeparatorAt(int index, long newKey) {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);

        BtreeInternalEntry existing = readEntry(index);

        writeEntry(
                index,
                new BtreeInternalEntry(
                        newKey,
                        existing.rightChildPageId()
                )
        );

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public List<BtreeInternalEntry> readAllEntries() {

        List<BtreeInternalEntry> list =
                new ArrayList<>(btreeInternalHeader.getEntryCount());

        for (int i = 0; i < btreeInternalHeader.getEntryCount(); i++) {
            list.add(readEntry(i));
        }

        return list;
    }

    public void rewriteAllEntries(List<BtreeInternalEntry> entries) {
        long lsn = bufferPool.log(page.getPageId(), WalOperation.UPDATE_TUPLE, new byte[0]);

        rewriteEntries(page, btreeInternalHeader, entries);

        page.getPageHeader().setPageLSN(lsn);
        bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }

    public boolean underflows() {
        return btreeInternalHeader.getEntryCount() < minEntries();
    }

    public boolean canLend() {
        return btreeInternalHeader.getEntryCount() > minEntries();
    }

    public static int minEntries() {
        return maxEntries() / 2;
    }

    private void writeHeader() {

        ByteBuffer buffer =
                ByteBuffer.wrap(page.getData());

        buffer.position(PageHeader.SIZE);

        btreeInternalHeader.writeTo(buffer);
    }

    public static int maxKeys() {
        /*
         * A page is of size 8Kb, if there are N keys in a Btree, then there would be N+1 child pointers
         * Let's say keys required to be as big as Long and child pointers would be int. Long is of 8Bytes in size and Int is 4
         * So 8(N) + (N+1)*4 = 8192
         * 8n+4n+4 = 8192
         * 12N+4 = 8192
         * We have page header as well in the page. So 12N+4+PageHeader.SIZE = 8192
         * 12N+4 = 8192-PageHeader.SIZE
         * 12N = 8192-PageHeader.SIZE+4
         * N = (8192-PageHeader.SIZE+4)/2
         * */

        return (Page.PAGE_SIZE
                - PageHeader.SIZE
                - BtreeInternalHeader.SIZE)
                / BtreeInternalEntry.SIZE;
    }

    public static int maxEntries() {

        int fixed =
                PageHeader.SIZE +
                        BtreeInternalHeader.SIZE;

        return (Page.PAGE_SIZE - fixed)
                / BtreeInternalEntry.SIZE;
    }

    private int entryOffset(int index) {

        return PageHeader.SIZE +
                BtreeInternalHeader.SIZE +
                index * BtreeInternalEntry.SIZE;
    }

    public BtreeInternalEntry readEntry(int index) {

        ByteBuffer buffer =
                ByteBuffer.wrap(page.getData());

        buffer.position(entryOffset(index));

        return new BtreeInternalEntry(
                buffer.getLong(),
                buffer.getInt()
        );
    }

    private void writeEntry(
            int index,
            BtreeInternalEntry entry) {

        ByteBuffer buffer =
                ByteBuffer.wrap(page.getData());

        buffer.position(entryOffset(index));

        buffer.putLong(entry.separatorKey());

        buffer.putInt(entry.rightChildPageId());
    }

    private static BtreeInternalHeader readHeader(Page page) {

        ByteBuffer buffer =
                ByteBuffer.wrap(page.getData());

        buffer.position(PageHeader.SIZE);

        return BtreeInternalHeader.readFrom(buffer);

    }

    public boolean hasSpace() {

        return btreeInternalHeader.getEntryCount()
                < maxEntries();

    }

    public static int maxChildren() {
        return maxKeys() + 1;
    }

    public Page getPage() {
        return page;
    }

    public BtreeInternalHeader getBtreeInternalHeader() {
        return btreeInternalHeader;
    }

}
