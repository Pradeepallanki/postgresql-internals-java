package com.pradeep.dbdemo.bufferpool;

import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.wal.WalManager;
import com.pradeep.dbdemo.wal.WalOperation;
import com.pradeep.dbdemo.wal.WalRecord;

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
    private final WalManager walManager;
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

    // convenience constructors use an in-memory WAL so unit tests that don't care about crash recovery keep working.
    // production callers should use the file-backed constructor with WalManager.forFile(path).
    public BufferPool(DiskManager diskManager) {
        this(diskManager, DEFAULT_CACHE_SIZE, WalManager.inMemory());
    }

    public BufferPool(DiskManager diskManager, int requestedSize) {
        this(diskManager, requestedSize, WalManager.inMemory());
    }

    public BufferPool(DiskManager diskManager, int requestedSize, WalManager walManager) {
        int size = ceilPowerOfTwo(requestedSize);
        this.diskManager = diskManager;
        this.walManager = walManager;
        this.frames = new BufferDescriptor[size];
        this.mask = size - 1;
        this.pageToSlot = new HashMap<>(size);
        this.freeSlots = new ArrayDeque<>(size);
        for (int i = size - 1; i >= 0; i--) {
            freeSlots.push(i);
        }
        this.freePages = new ArrayDeque<>();
    }

    public WalManager getWalManager() {
        return walManager;
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
            long lsn = log(reused, WalOperation.ALLOCATE_PAGE, new byte[0]);
            Arrays.fill(page.getData(), (byte) 0);
            page.getPageHeader().setPageType(PageHeader.PageType.EMPTY);
            page.getPageHeader().setSlotCount(0);
            page.getPageHeader().setFreeSpaceOffSet(Page.PAGE_SIZE);
            markDirtyAtLsn(reused, lsn);
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
                // WAL-first: flush pending records so the victim's protecting log entry is durable before the page is written back.
                walManager.flush();
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

        // write-ahead: every WAL record that protects this page must be durable before the page is.
        walManager.flush();

        diskManager.writePage(descriptor.getPage());
        descriptor.markUnDirty();
    }

    public synchronized void flushAll() throws IOException {
        walManager.flush();

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

        // WAL first: batch-write any pending records so no page we're about to write out-races its log record.
        walManager.flush();

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

    // WAL-first sequence for a page mutation:
    //   1. long lsn = bufferPool.log(pageId, op, payload);
    //   2. <perform the mutation on page bytes>
    //   3. bufferPool.markDirtyAtLsn(pageId, lsn);
    // step 1 stages the record in the WAL buffer and mints the LSN (no disk I/O); step 3 stamps
    // pageLSN and flips the dirty flag. The buffered log is drained by walManager.flush(), which
    // BufferPool triggers before writing any page back to disk (see flushPage / flushAll / eviction).

    public synchronized long log(int pageId, WalOperation operation, byte[] payload) {
        return walManager.append(new WalRecord(operation, pageId, payload));
    }

    public synchronized void markDirtyAtLsn(int pageId, long lsn) {
        BufferDescriptor descriptor = frames[pageToSlot.get(pageId)];
        descriptor.getPage().getPageHeader().setPageLSN(lsn);
        descriptor.markDirty();
    }

    // bare dirty-flip for tests / callers that don't need to append a WAL record (e.g., simulating a dirty buffer).
    // production mutation sites must use the log() + markDirtyAtLsn() pair.
    public synchronized void markDirty(int pageId) {
        frames[pageToSlot.get(pageId)].markDirty();
    }

    public synchronized void markNotDirty(int pageId) {
        frames[pageToSlot.get(pageId)].markUnDirty();
    }
}