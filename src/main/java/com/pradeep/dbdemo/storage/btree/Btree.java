package com.pradeep.dbdemo.storage.btree;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.RID;
import com.pradeep.dbdemo.storage.btree.internal.BTreeInternalPage;
import com.pradeep.dbdemo.storage.btree.internal.BtreeInternalEntry;
import com.pradeep.dbdemo.storage.btree.internal.BtreeInternalHeader;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafHeader;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafPage;
import com.pradeep.dbdemo.storage.btree.leaf.BtreeLeafEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;


public class Btree {
    private final BufferPool bufferPool;

    private final BTreeMetadata bTreeMetadata;

    public Btree(BufferPool bufferPool) throws IOException {
        this.bufferPool = bufferPool;
        this.bTreeMetadata = buildMetaData();
    }

    public Btree(BufferPool bufferPool, BTreeMetadata bTreeMetadata) {
        this.bufferPool = bufferPool;
        this.bTreeMetadata = bTreeMetadata;
    }

    private BTreeMetadata buildMetaData() throws IOException {
        int pageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(pageId);

        page.getPageHeader().setPageType(PageHeader.PageType.BTREE_LEAF);

        page.getPageHeader().writeTo(ByteBuffer.wrap(page.getData()));

        BTreeLeafPage leaf =
                new BTreeLeafPage(page, new BTreeLeafHeader(), bufferPool);

        leaf.writeHeader();

        page.markDirty();

        return new BTreeMetadata(pageId);
    }


    public void insert(long key, RID rid) throws IOException {
        SplitResult split = insertIntoPage(
                bTreeMetadata.getRootPageId(),
                key,
                rid
        );

        if (split != null) {
            createRoot(split);
        }
    }

    public RID search(long key) throws IOException {
        return search(bTreeMetadata.getRootPageId(), key);
    }

    private RID search(int pageId, long key) throws IOException {
        Page page = bufferPool.fetchPage(pageId);

        if (page.getPageHeader().getPageType() == PageHeader.PageType.BTREE_LEAF) {
            BTreeLeafPage bTreeLeafPage = new BTreeLeafPage(page, bufferPool);
            return bTreeLeafPage.search(key);
        } else {
            BTreeInternalPage bTreeInternalPage = new BTreeInternalPage(page, bufferPool);
            return search(bTreeInternalPage.findChild(key), key);
        }

    }

    private SplitResult insertIntoPage(
            int pageId,
            long key,
            RID rid)
            throws IOException {

        Page page = bufferPool.fetchPage(pageId);

        return switch (page.getPageHeader().getPageType()) {
            case BTREE_LEAF -> insertIntoLeaf(page, key, rid);
            case BTREE_INTERNAL -> insertIntoInternal(page, key, rid);
            default -> throw new IllegalStateException();
        };
    }

    private SplitResult insertIntoInternal(
            Page page,
            long key,
            RID rid)
            throws IOException {

        BTreeInternalPage internal =
                new BTreeInternalPage(page, bufferPool);

        int childPageId =
                internal.findChild(key);

        SplitResult childSplit =
                insertIntoPage(
                        childPageId,
                        key,
                        rid
                );

        if (childSplit == null) {
            return null;
        }

        return internal.insertSeparator(
                childSplit.separatorKey(),
                childSplit.newPageId()
        );
    }


    private SplitResult insertIntoLeaf(
            Page page,
            long key,
            RID rid)
            throws IOException {

        BTreeLeafPage leaf =
                new BTreeLeafPage(page, bufferPool);

        return leaf.insert(key, rid);
    }


    private void createRoot(SplitResult split) throws IOException {
        int oldRootPageId = bTreeMetadata.getRootPageId();

        int newRootPageId = bufferPool.allocatePage();

        Page root =
                bufferPool.fetchPage(newRootPageId);

        root.getPageHeader()
                .setPageType(PageHeader.PageType.BTREE_INTERNAL);

        BtreeInternalHeader header =
                new BtreeInternalHeader(
                        (short) 0,
                        oldRootPageId
                );

        BTreeInternalPage internal =
                new BTreeInternalPage(
                        root,
                        header,
                        bufferPool
                );

        internal.insertSeparator(
                split.separatorKey(),
                split.newPageId()
        );

        root.markDirty();

        bTreeMetadata.setRootPageId(newRootPageId);

    }

    public BTreeMetadata getbTreeMetadata() {
        return bTreeMetadata;
    }

    public boolean delete(long key) throws IOException {

        boolean deleted =
                deleteFromPage(
                        bTreeMetadata.getRootPageId(),
                        key
                );

        if (deleted) {
            shrinkRootIfNeeded();
        }

        return deleted;
    }

    private boolean deleteFromPage(int pageId, long key)
            throws IOException {

        Page page = bufferPool.fetchPage(pageId);

        return switch (page.getPageHeader().getPageType()) {
            case BTREE_LEAF -> deleteFromLeaf(page, key);
            case BTREE_INTERNAL -> deleteFromInternal(page, key);
            default -> throw new IllegalStateException();
        };
    }

    private boolean deleteFromLeaf(Page page, long key) {

        BTreeLeafPage leaf =
                new BTreeLeafPage(page, bufferPool);

        return leaf.delete(key);
    }

    private boolean deleteFromInternal(Page page, long key)
            throws IOException {

        BTreeInternalPage internal =
                new BTreeInternalPage(page, bufferPool);

        int childIndex = internal.findChildIndex(key);

        int childPageId = internal.childPageIdAt(childIndex);

        boolean deleted = deleteFromPage(childPageId, key);

        if (deleted) {
            rebalanceIfNeeded(internal, childIndex);
        }

        return deleted;
    }

    private void rebalanceIfNeeded(
            BTreeInternalPage parent,
            int childIndex)
            throws IOException {

        int childPageId = parent.childPageIdAt(childIndex);

        Page childPage = bufferPool.fetchPage(childPageId);

        if (childPage.getPageHeader().getPageType()
                == PageHeader.PageType.BTREE_LEAF) {

            BTreeLeafPage leaf =
                    new BTreeLeafPage(childPage, bufferPool);

            if (leaf.underflows()) {
                rebalanceLeaf(parent, childIndex, leaf);
            }

        } else {

            BTreeInternalPage child =
                    new BTreeInternalPage(childPage, bufferPool);

            if (child.underflows()) {
                rebalanceInternal(parent, childIndex, child);
            }
        }
    }

    private void rebalanceLeaf(
            BTreeInternalPage parent,
            int childIndex,
            BTreeLeafPage leaf)
            throws IOException {

        int parentEntryCount =
                parent.getBtreeInternalHeader().getEntryCount();

        if (childIndex > 0) {

            BTreeLeafPage left =
                    fetchLeaf(parent.childPageIdAt(childIndex - 1));

            if (left.canLend()) {
                borrowFromLeftLeaf(parent, childIndex, left, leaf);
                return;
            }
        }

        if (childIndex < parentEntryCount) {

            BTreeLeafPage right =
                    fetchLeaf(parent.childPageIdAt(childIndex + 1));

            if (right.canLend()) {
                borrowFromRightLeaf(parent, childIndex, leaf, right);
                return;
            }
        }

        if (childIndex > 0) {

            BTreeLeafPage left =
                    fetchLeaf(parent.childPageIdAt(childIndex - 1));

            mergeLeaves(parent, childIndex - 1, left, leaf);

        } else {

            BTreeLeafPage right =
                    fetchLeaf(parent.childPageIdAt(childIndex + 1));

            mergeLeaves(parent, childIndex, leaf, right);
        }
    }

    private void borrowFromLeftLeaf(
            BTreeInternalPage parent,
            int childIndex,
            BTreeLeafPage left,
            BTreeLeafPage right) {

        List<BtreeLeafEntry> leftEntries = left.readAllEntries();
        List<BtreeLeafEntry> rightEntries = right.readAllEntries();

        BtreeLeafEntry moved = leftEntries.removeLast();

        rightEntries.addFirst(moved);

        left.rewriteAllEntries(leftEntries);
        right.rewriteAllEntries(rightEntries);

        parent.updateSeparatorAt(childIndex - 1, right.firstKey());
    }

    private void borrowFromRightLeaf(
            BTreeInternalPage parent,
            int childIndex,
            BTreeLeafPage left,
            BTreeLeafPage right) {

        List<BtreeLeafEntry> leftEntries = left.readAllEntries();
        List<BtreeLeafEntry> rightEntries = right.readAllEntries();

        BtreeLeafEntry moved = rightEntries.removeFirst();

        leftEntries.add(moved);

        left.rewriteAllEntries(leftEntries);
        right.rewriteAllEntries(rightEntries);

        parent.updateSeparatorAt(childIndex, right.firstKey());
    }

    private void mergeLeaves(
            BTreeInternalPage parent,
            int separatorIndex,
            BTreeLeafPage left,
            BTreeLeafPage right)
            throws IOException {

        int rightNext =
                right.getbTreeLeafHeader().getNextLeafPageId();

        left.mergeFrom(right);

        if (rightNext != -1) {

            Page nextPage = bufferPool.fetchPage(rightNext);

            BTreeLeafPage next =
                    new BTreeLeafPage(nextPage, bufferPool);

            next.getbTreeLeafHeader()
                    .setPrevLeafPageId(left.getPage().getPageId());

            next.writeHeader();

            nextPage.markDirty();
        }

        parent.removeEntry(separatorIndex);

        bufferPool.freePage(right.getPage().getPageId());
    }

    private void rebalanceInternal(
            BTreeInternalPage parent,
            int childIndex,
            BTreeInternalPage child)
            throws IOException {

        int parentEntryCount =
                parent.getBtreeInternalHeader().getEntryCount();

        if (childIndex > 0) {

            BTreeInternalPage left =
                    fetchInternal(parent.childPageIdAt(childIndex - 1));

            if (left.canLend()) {
                borrowFromLeftInternal(parent, childIndex, left, child);
                return;
            }
        }

        if (childIndex < parentEntryCount) {

            BTreeInternalPage right =
                    fetchInternal(parent.childPageIdAt(childIndex + 1));

            if (right.canLend()) {
                borrowFromRightInternal(parent, childIndex, child, right);
                return;
            }
        }

        if (childIndex > 0) {

            BTreeInternalPage left =
                    fetchInternal(parent.childPageIdAt(childIndex - 1));

            mergeInternals(parent, childIndex - 1, left, child);

        } else {

            BTreeInternalPage right =
                    fetchInternal(parent.childPageIdAt(childIndex + 1));

            mergeInternals(parent, childIndex, child, right);
        }
    }

    private void borrowFromLeftInternal(
            BTreeInternalPage parent,
            int childIndex,
            BTreeInternalPage left,
            BTreeInternalPage child) {

        long parentSepKey =
                parent.readEntry(childIndex - 1).separatorKey();

        List<BtreeInternalEntry> leftEntries = left.readAllEntries();
        List<BtreeInternalEntry> childEntries = child.readAllEntries();

        BtreeInternalEntry moved = leftEntries.removeLast();

        int oldChildLeftMost =
                child.getBtreeInternalHeader().getLeftMostChildPageId();

        childEntries.addFirst(
                new BtreeInternalEntry(parentSepKey, oldChildLeftMost)
        );

        child.getBtreeInternalHeader()
                .setLeftMostChildPageId(moved.rightChildPageId());

        child.rewriteAllEntries(childEntries);
        left.rewriteAllEntries(leftEntries);

        parent.updateSeparatorAt(childIndex - 1, moved.separatorKey());
    }

    private void borrowFromRightInternal(
            BTreeInternalPage parent,
            int childIndex,
            BTreeInternalPage child,
            BTreeInternalPage right) {

        long parentSepKey =
                parent.readEntry(childIndex).separatorKey();

        List<BtreeInternalEntry> childEntries = child.readAllEntries();
        List<BtreeInternalEntry> rightEntries = right.readAllEntries();

        int oldRightLeftMost =
                right.getBtreeInternalHeader().getLeftMostChildPageId();

        BtreeInternalEntry firstRight = rightEntries.removeFirst();

        childEntries.add(
                new BtreeInternalEntry(parentSepKey, oldRightLeftMost)
        );

        right.getBtreeInternalHeader()
                .setLeftMostChildPageId(firstRight.rightChildPageId());

        child.rewriteAllEntries(childEntries);
        right.rewriteAllEntries(rightEntries);

        parent.updateSeparatorAt(childIndex, firstRight.separatorKey());
    }

    private void mergeInternals(
            BTreeInternalPage parent,
            int separatorIndex,
            BTreeInternalPage left,
            BTreeInternalPage right)
            throws IOException {

        long parentSepKey =
                parent.readEntry(separatorIndex).separatorKey();

        int rightLeftMost =
                right.getBtreeInternalHeader().getLeftMostChildPageId();

        List<BtreeInternalEntry> merged = left.readAllEntries();

        merged.add(
                new BtreeInternalEntry(parentSepKey, rightLeftMost)
        );

        merged.addAll(right.readAllEntries());

        left.rewriteAllEntries(merged);

        parent.removeEntry(separatorIndex);

        bufferPool.freePage(right.getPage().getPageId());
    }

    private void shrinkRootIfNeeded() throws IOException {

        Page root =
                bufferPool.fetchPage(bTreeMetadata.getRootPageId());

        if (root.getPageHeader().getPageType()
                != PageHeader.PageType.BTREE_INTERNAL) {
            return;
        }

        BTreeInternalPage internal =
                new BTreeInternalPage(root, bufferPool);

        if (internal.getBtreeInternalHeader().getEntryCount() > 0) {
            return;
        }

        int newRootPageId =
                internal.getBtreeInternalHeader()
                        .getLeftMostChildPageId();

        int oldRootPageId = bTreeMetadata.getRootPageId();

        bTreeMetadata.setRootPageId(newRootPageId);

        bufferPool.freePage(oldRootPageId);
    }

    private BTreeLeafPage fetchLeaf(int pageId) throws IOException {
        return new BTreeLeafPage(
                bufferPool.fetchPage(pageId),
                bufferPool
        );
    }

    private BTreeInternalPage fetchInternal(int pageId)
            throws IOException {
        return new BTreeInternalPage(
                bufferPool.fetchPage(pageId),
                bufferPool
        );
    }

}
