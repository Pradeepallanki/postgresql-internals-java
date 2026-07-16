package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.bufferpool.BufferPool;
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
            HeapPage heapPage = new HeapPage(page, bufferPool);

            if (!heapPage.hasSpace(tuple.length)) {
                // FSM said this page has room but the contiguous slice doesn't fit — compact and see if reclaimed bytes are enough.
                heapPage.compact();
            }

            if (heapPage.hasSpace(tuple.length)) {
                RID rid = heapPage.insert(tuple);
                freeSpaceMap.updateFreeSpace(freePageId, heapPage.getFreeBytes());
                return rid;
            }

            // still doesn't fit — sync FSM to the truth so we don't loop back to this page, then fall through to allocation.
            freeSpaceMap.updateFreeSpace(freePageId, heapPage.getFreeBytes());
        }

        int pageId = bufferPool.allocatePage();
        Page page = bufferPool.fetchPage(pageId);
        HeapPage heapPage = new HeapPage(page, bufferPool);
        RID rid = heapPage.insert(tuple);
        freeSpaceMap.updateFreeSpace(pageId, heapPage.getFreeBytes());
        return rid;
    }

    public byte[] read(RID rid) throws IOException {
        HeapPage heapPage = new HeapPage(bufferPool.fetchPage(rid.pageId()), bufferPool);
        return heapPage.read(rid);
    }

    public boolean delete(RID rid) throws IOException {
        Page page = bufferPool.fetchPage(rid.pageId());
        HeapPage heapPage = new HeapPage(page, bufferPool);
        heapPage.delete(rid);
        freeSpaceMap.updateFreeSpace(
                rid.pageId(),
                heapPage.getFreeBytes()
        );
        return true;
    }
}
