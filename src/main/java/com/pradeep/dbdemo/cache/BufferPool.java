package com.pradeep.dbdemo.cache;

import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class BufferPool {
    private final DiskManager diskManager;
    private final Map<Integer, Page> cache;
    private final Deque<Integer> freePages;

    public BufferPool(DiskManager diskManager) {
        this.diskManager = diskManager;
        this.cache = new HashMap<>();
        this.freePages = new ArrayDeque<>();
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
        Integer reused = freePages.pollFirst();
        if (reused != null) {
            Page page = fetchPage(reused);
            Arrays.fill(page.getData(), (byte) 0);
            page.getPageHeader().setPageType(PageHeader.PageType.EMPTY);
            page.getPageHeader().setSlotCount(0);
            page.getPageHeader().setFreeSpaceOffSet(Page.PAGE_SIZE);
            page.markDirty();
            return reused;
        }
        return this.diskManager.allocatePage();
    }

    public void freePage(int pageId) throws IOException {
        Page page = fetchPage(pageId);
        Arrays.fill(page.getData(), (byte) 0);
        page.getPageHeader().setPageType(PageHeader.PageType.EMPTY);
        page.getPageHeader().setSlotCount(0);
        page.getPageHeader().setFreeSpaceOffSet(Page.PAGE_SIZE);
        page.markDirty();
        freePages.addLast(pageId);
    }

    public int freeListSize() {
        return freePages.size();
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
