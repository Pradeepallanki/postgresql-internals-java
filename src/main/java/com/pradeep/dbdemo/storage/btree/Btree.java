package com.pradeep.dbdemo.storage.btree;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.RID;
import com.pradeep.dbdemo.storage.btree.internal.BTreeInternalPage;
import com.pradeep.dbdemo.storage.btree.internal.BtreeInternalHeader;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafHeader;
import com.pradeep.dbdemo.storage.btree.leaf.BTreeLeafPage;

import java.io.IOException;
import java.nio.ByteBuffer;


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


}
