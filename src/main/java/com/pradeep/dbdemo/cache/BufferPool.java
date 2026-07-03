package com.pradeep.dbdemo.cache;

import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BufferPool {
    private final DiskManager diskManager;
    private final Map<Integer, Page> cache;

    public BufferPool(DiskManager diskManager) {
        this.diskManager = diskManager;
        this.cache = new HashMap<>();
    }

    public Page fetchPage(int pageId) throws IOException {
        if (cache.containsKey(pageId)) {
            return cache.get(pageId);
        }

        Page page = diskManager.readPage(pageId);
        cache.put(pageId, page);
        return page;
    }

    public int allocatePage() throws IOException {
        return this.diskManager.allocatePage();
    }

    public void flushPage(int pageId) throws IOException {
        Page page = cache.get(pageId);

        if (page == null) {
            return;
        }

        if (!page.isDirty()) {
            return;
        }

        diskManager.writePage(page);

        page.markNotDirty();
    }

    public void flushAll() throws IOException {
        for (Map.Entry<Integer, Page> entry : cache.entrySet()) {
            if (entry.getValue().isDirty()) {
                diskManager.writePage(entry.getValue());
                entry.getValue().markNotDirty();
            }
        }
    }

    public long getPageCount() throws IOException {
        return diskManager.getPageCount();
    }

}
