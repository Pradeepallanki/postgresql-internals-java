package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.fsm.FreeSpaceMap;

import java.io.IOException;

public class HeapFile {
    private final BufferPool bufferPool;
    private final FreeSpaceMap freeSpaceMap;

    public HeapFile(BufferPool bufferPool, FreeSpaceMap freeSpaceMap) {
        this.bufferPool = bufferPool;
        this.freeSpaceMap = freeSpaceMap;
    }

    public RID insert(byte[] tuple) throws IOException {
        int freePageId = freeSpaceMap.findPageWithAtLeast(HeapPage.getTotalRequiredSpace(tuple.length));

        if (freePageId != -1) {
            Page page = bufferPool.fetchPage(freePageId);
            HeapPage heapPage = new HeapPage(page);
            RID rid = heapPage.insert(tuple);
            freeSpaceMap.updateFreeSpace(
                    freePageId,
                    heapPage.getFreeBytes()
            );
            return rid;
        }

        int pageId = bufferPool.allocatePage();
        Page page = bufferPool.fetchPage(pageId);
        HeapPage heapPage = new HeapPage(page);
        RID rid = heapPage.insert(tuple);
        freeSpaceMap.updateFreeSpace(pageId, heapPage.getFreeBytes());
        return rid;
    }

    public byte[] read(RID rid) throws IOException {
        HeapPage heapPage = new HeapPage(bufferPool.fetchPage(rid.pageId()));
        return heapPage.read(rid);
    }

    public boolean delete(RID rid) throws IOException {
        Page page = bufferPool.fetchPage(rid.pageId());
        HeapPage heapPage = new HeapPage(page);
        heapPage.delete(rid);
        freeSpaceMap.updateFreeSpace(
                rid.pageId(),
                heapPage.getFreeBytes()
        );
        return true;
    }
}
