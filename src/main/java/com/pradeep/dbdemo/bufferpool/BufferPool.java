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
    // frames is a fixed-size array whose length is a power of two. clock sweep walks it with (hand + 1) & mask.
    // pageToSlot indexes it by pageId so hits stay O(1). freeSlots tracks which array indices are currently unoccupied.
    // freePages (disk-level free list) is separate — it tracks pageIds that can be handed back out by allocatePage, not frame slots.
    private final DiskManager diskManager;
    private final BufferDescriptor[] frames;
    private final int mask;
    private final Map<Integer, Integer> pageToSlot;
    private final Deque<Integer> freeSlots;
    private final Deque<Integer> freePages;
    private int clockHand = 0;
    // separate cursor for the background writer so its scan doesn't interfere with the eviction clock hand.
    private int writerCursor = 0;

    public static final int DEFAULT_CACHE_SIZE = 1024;
    private static final int MAX_USAGE = 5;

    public BufferPool(DiskManager diskManager) {
        this(diskManager, DEFAULT_CACHE_SIZE);
    }

    public BufferPool(DiskManager diskManager, int requestedSize) {
        int size = ceilPowerOfTwo(requestedSize);
        this.diskManager = diskManager;
        this.frames = new BufferDescriptor[size];
        this.mask = size - 1;
        this.pageToSlot = new HashMap<>(size);
        this.freeSlots = new ArrayDeque<>(size);
        for (int i = size - 1; i >= 0; i--) {
            freeSlots.push(i);
        }
        this.freePages = new ArrayDeque<>();
    }

    private static int ceilPowerOfTwo(int n) {
        if (n <= 1) return 1;
        return Integer.highestOneBit(n - 1) << 1;
    }

    public synchronized Page fetchPage(int pageId) throws IOException {
        Integer slot = pageToSlot.get(pageId);
        if (slot != null) {
            BufferDescriptor cached = frames[slot];
            cached.setUsageCount(Math.min(cached.getUsageCount() + 1, MAX_USAGE));
            return cached.getPage();
        }

        Page page = diskManager.readPage(pageId);

        Integer free = freeSlots.pollFirst();
        int target = (free != null) ? free : selectVictimSlot();

        BufferDescriptor descriptor = new BufferDescriptor(pageId, page);
        descriptor.setUsageCount(1);
        frames[target] = descriptor;
        pageToSlot.put(pageId, target);

        return page;
    }

    public synchronized int allocatePage() throws IOException {
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

    public synchronized void freePage(int pageId) {
        Integer slot = pageToSlot.remove(pageId);
        if (slot != null) {
            frames[slot] = null;
            freeSlots.push(slot);
        }
        freePages.addLast(pageId);
    }

    private int selectVictimSlot() throws IOException {
        // sweep is only reached when every frame is occupied — freeSlots was empty in fetchPage — so frames[here] is never null inside the loop.
        int budget = frames.length * (MAX_USAGE + 1);

        while (budget-- > 0) {
            int here = clockHand;
            clockHand = (clockHand + 1) & mask;

            BufferDescriptor descriptor = frames[here];

            if (descriptor.getUsageCount() > 0) {
                descriptor.setUsageCount(descriptor.getUsageCount() - 1);
                continue;
            }

            if (descriptor.isDirty()) {
                diskManager.writePage(descriptor.getPage());
            }
            pageToSlot.remove(descriptor.getPageId());
            frames[here] = null;
            return here;
        }

        throw new IllegalStateException("Clock sweep could not find a victim within its budget");
    }

    public synchronized int freeListSize() {
        return freePages.size();
    }

    public synchronized int size() {
        return frames.length - freeSlots.size();
    }

    public int capacity() {
        return frames.length;
    }

    public synchronized boolean isCached(int pageId) {
        return pageToSlot.containsKey(pageId);
    }

    public synchronized void flushPage(int pageId) throws IOException {
        Integer slot = pageToSlot.get(pageId);
        if (slot == null) return;

        BufferDescriptor descriptor = frames[slot];
        if (!descriptor.isDirty()) return;

        diskManager.writePage(descriptor.getPage());
        descriptor.markUnDirty();
    }

    public synchronized void flushAll() throws IOException {
        for (BufferDescriptor descriptor : frames) {
            if (descriptor != null && descriptor.isDirty()) {
                diskManager.writePage(descriptor.getPage());
                descriptor.markUnDirty();
            }
        }
    }

    public synchronized void flushSomeDirtyPages(int maxSize) throws IOException {
        // resume from where the last cycle left off so we rotate through the whole array over successive calls, instead of always favouring the low-index slots.
        int cleaned = 0;
        int scanned = 0;

        while (cleaned < maxSize && scanned < frames.length) {
            int index = (writerCursor + scanned) & mask;
            scanned++;

            BufferDescriptor descriptor = frames[index];
            if (descriptor == null || !descriptor.isDirty()) {
                continue;
            }

            diskManager.writePage(descriptor.getPage());
            descriptor.markUnDirty();
            cleaned++;
        }

        writerCursor = (writerCursor + scanned) & mask;
    }

    public long getPageCount() throws IOException {
        return diskManager.getPageCount();
    }

    public synchronized void markDirty(int pageId) {
        frames[pageToSlot.get(pageId)].markDirty();
    }

    public synchronized void markNotDirty(int pageId) {
        frames[pageToSlot.get(pageId)].markUnDirty();
    }
}