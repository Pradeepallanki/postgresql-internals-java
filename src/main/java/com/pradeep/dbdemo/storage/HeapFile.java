package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.cache.BufferPool;

import java.io.IOException;

public class HeapFile {
    private final BufferPool bufferPool;

    public HeapFile(BufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }

    public RID insert(byte[] tuple) throws IOException {
        long pageCount = bufferPool.getPageCount();

        for (int i = 0; i < pageCount; i++) {
            Page page = bufferPool.fetchPage(i);
            HeapPage heapPage = new HeapPage(page);

            if (heapPage.hasSpace(tuple.length)) {
                return heapPage.insert(tuple);
            }
        }

        int pageId = bufferPool.allocatePage();
        Page page = bufferPool.fetchPage(pageId);
        HeapPage heapPage = new HeapPage(page);
        return heapPage.insert(tuple);
    }

    public byte[] read(RID rid) throws IOException {
        HeapPage heapPage = new HeapPage(bufferPool.fetchPage(rid.pageId()));
        return heapPage.read(rid);
    }

    public boolean delete(RID rid) throws IOException {
        Page page = bufferPool.fetchPage(rid.pageId());
        HeapPage heapPage = new HeapPage(page);
        heapPage.delete(rid);
        return true;
    }
}
