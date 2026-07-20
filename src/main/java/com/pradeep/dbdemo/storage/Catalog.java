package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.wal.WalOperation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Catalog {
    // page 0 is the well-known catalog location — on reopen this is the only pageId we know without asking anyone.
    public static final int CATALOG_PAGE_ID = 0;

    public static final byte INDEX_TYPE_BTREE = 0;

    private final BufferPool bufferPool;
    private int fsmMetaPageId = -1;
    private final Map<String, IndexEntry> indexes = new LinkedHashMap<>();

    public record IndexEntry(String name, byte indexType, int rootPageId) {}

    private Catalog(BufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }

    public static Catalog createFresh(BufferPool bufferPool) throws IOException {
        int pageId = bufferPool.allocatePage();
        if (pageId != CATALOG_PAGE_ID) {
            throw new IllegalStateException(
                    "Catalog must be the first page allocated (expected id "
                            + CATALOG_PAGE_ID + ", got " + pageId + ")");
        }

        Page page = bufferPool.fetchPage(pageId);
        page.getPageHeader().setPageType(PageHeader.PageType.CATALOG);

        Catalog catalog = new Catalog(bufferPool);
        catalog.flush();
        return catalog;
    }

    public static Catalog open(BufferPool bufferPool) throws IOException {
        Page page = bufferPool.fetchPage(CATALOG_PAGE_ID);

        if (page.getPageHeader().getPageType() != PageHeader.PageType.CATALOG) {
            throw new IllegalStateException("Page 0 is not a catalog page");
        }

        Catalog catalog = new Catalog(bufferPool);

        ByteBuffer buf = ByteBuffer.wrap(page.getData());
        buf.position(PageHeader.SIZE);

        catalog.fsmMetaPageId = buf.getInt();

        int indexCount = buf.getInt();
        for (int i = 0; i < indexCount; i++) {
            short nameLen = buf.getShort();
            byte[] nameBytes = new byte[nameLen];
            buf.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            byte indexType = buf.get();
            int rootPageId = buf.getInt();
            catalog.indexes.put(name, new IndexEntry(name, indexType, rootPageId));
        }

        return catalog;
    }

    public int getFsmMetaPageId() {
        return fsmMetaPageId;
    }

    public void setFsmMetaPageId(int id) throws IOException {
        this.fsmMetaPageId = id;
        flush();
    }

    public IndexEntry lookup(String name) {
        return indexes.get(name);
    }

    public Collection<IndexEntry> listIndexes() {
        return indexes.values();
    }

    public void registerIndex(String name, byte indexType, int rootPageId) throws IOException {
        if (indexes.containsKey(name)) {
            throw new IllegalArgumentException("Index already registered: " + name);
        }
        indexes.put(name, new IndexEntry(name, indexType, rootPageId));
        flush();
    }

    public void updateRoot(String name, int newRootPageId) throws IOException {
        IndexEntry existing = indexes.get(name);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown index: " + name);
        }
        indexes.put(name, new IndexEntry(existing.name, existing.indexType, newRootPageId));
        flush();
    }

    private void flush() throws IOException {
        Page page = bufferPool.fetchPage(CATALOG_PAGE_ID);
        long lsn = bufferPool.log(CATALOG_PAGE_ID, WalOperation.UPDATE_TUPLE, new byte[0]);

        page.getPageHeader().setPageLSN(lsn);

        ByteBuffer buf = ByteBuffer.wrap(page.getData());
        page.getPageHeader().writeTo(buf);

        buf.putInt(fsmMetaPageId);
        buf.putInt(indexes.size());

        for (IndexEntry e : indexes.values()) {
            byte[] nameBytes = e.name.getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) nameBytes.length);
            buf.put(nameBytes);
            buf.put(e.indexType);
            buf.putInt(e.rootPageId);
        }

        this.bufferPool.markDirtyAtLsn(page.getPageId(), lsn);
    }
}