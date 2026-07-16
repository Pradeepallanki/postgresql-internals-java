package com.pradeep.dbdemo.bufferpool;

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
    private final Map<Integer, BufferDescriptor> cache;
    private final Deque<Integer> freePages;
    private static final int CACHE_SIZE = 1024;

    public BufferPool(DiskManager diskManager) {
        this.diskManager = diskManager;
        this.cache = new HashMap<>();
        this.freePages = new ArrayDeque<>();
    }

    public Page fetchPage(int pageId) throws IOException {
        if (cache.containsKey(pageId)) {
            return cache.get(pageId).getPage();
        }

        Page page = diskManager.readPage(pageId);

        if (cache.size() != CACHE_SIZE) {
            cache.put(pageId, new BufferDescriptor(pageId, page));
        }

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
            markDirty(reused);
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
        markDirty(pageId);
        freePages.addLast(pageId);
    }

    public int freeListSize() {
        return freePages.size();
    }

    public void flushPage(int pageId) throws IOException {
        BufferDescriptor descriptor = cache.get(pageId);

        if (descriptor == null) {
            return;
        }

        if (!descriptor.isDirty()) {
            return;
        }

        diskManager.writePage(descriptor.getPage());

        descriptor.markUnDirty();
    }

    public void flushAll() throws IOException {
        for (Map.Entry<Integer, BufferDescriptor> entry : cache.entrySet()) {
            if (entry.getValue().isDirty()) {
                diskManager.writePage(entry.getValue().getPage());
                entry.getValue().markUnDirty();
            }
        }
    }

    public long getPageCount() throws IOException {
        return diskManager.getPageCount();
    }

    public void markDirty(int pageId) {
        this.cache.get(pageId).markDirty();
    }

    public void markNotDirty(int pageId) {
        this.cache.get(pageId).markUnDirty();
    }

}
