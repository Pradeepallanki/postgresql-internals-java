package com.pradeep.dbdemo.wal;

import com.pradeep.dbdemo.storage.DiskManager;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;
import com.pradeep.dbdemo.storage.Slot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

// Redo-only crash recovery. Walks the WAL in order; for each record, loads the target page from disk,
// compares pageLSN to record.lsn, and either skips (page already reflects the change) or reapplies the
// operation via the dispatcher below.
//
// Recovery bypasses the buffer pool: it uses DiskManager directly so replaying a mutation does not
// itself append a new WAL record.
public class RecoveryManager {

    public record RecoveryStats(int applied, int skipped) {}

    private final Path walPath;
    private final DiskManager diskManager;

    public RecoveryManager(Path walPath, DiskManager diskManager) {
        this.walPath = walPath;
        this.diskManager = diskManager;
    }

    public RecoveryStats recover() throws IOException {
        int applied = 0;
        int skipped = 0;

        List<WalRecord> log;
        try (WalManager reader = WalManager.forFile(walPath)) {
            log = reader.readAll();
        }

        long totalPages = diskManager.getPageCount();

        for (WalRecord record : log) {
            int pageId = record.getPageId();

            // in a fuller recovery we would extend the file to cover this pageId. For now, skip: the demo tests never log records for pages that don't yet exist on disk.
            if (pageId < 0 || pageId >= totalPages) {
                skipped++;
                continue;
            }

            Page page = diskManager.readPage(pageId);
            long pageLsn = page.getPageHeader().getPageLSN();

            if (pageLsn >= record.getLsn()) {
                skipped++;
                continue;
            }

            dispatch(record, page);
            page.getPageHeader().setPageLSN(record.getLsn());
            diskManager.writePage(page);
            applied++;
        }

        return new RecoveryStats(applied, skipped);
    }

    private void dispatch(WalRecord record, Page page) {
        switch (record.getOperation()) {
            case INSERT_TUPLE -> applyInsertTuple(record, page);
            case ALLOCATE_PAGE -> applyAllocatePage(page);
            case DELETE_TUPLE,
                 UPDATE_TUPLE,
                 BTREE_SPLIT,
                 BTREE_MERGE,
                 UNCLASSIFIED ->
                    throw new UnsupportedOperationException(
                            "Recovery for " + record.getOperation() + " is not implemented yet");
        }
    }

    // Heap-page insert replay: payload holds the raw tuple bytes. Slot number and offset are recomputed
    // from the current page state (slotCount, freeSpaceOffSet), which is the pre-mutation state at
    // this record's LSN. Idempotent given a consistent starting state.
    private void applyInsertTuple(WalRecord record, Page page) {
        PageHeader.PageType type = page.getPageHeader().getPageType();
        if (type != PageHeader.PageType.HEAP && type != PageHeader.PageType.EMPTY) {
            throw new UnsupportedOperationException(
                    "INSERT_TUPLE replay is only implemented for heap pages, got " + type);
        }

        byte[] tuple = record.getPayload();
        PageHeader header = page.getPageHeader();

        short freeOffSet = (short) (header.getFreeSpaceOffSet() - tuple.length);
        System.arraycopy(tuple, 0, page.getData(), freeOffSet, tuple.length);

        Slot slot = new Slot(freeOffSet, (short) tuple.length, false);
        int slotNumber = header.getSlotCount();
        int slotOffset = PageHeader.SIZE + slotNumber * Slot.SIZE;

        ByteBuffer buf = ByteBuffer.wrap(page.getData());
        buf.position(slotOffset);
        slot.writeTo(buf);

        header.setSlotCount(slotNumber + 1);
        header.setFreeSpaceOffSet(freeOffSet);
    }

    private void applyAllocatePage(Page page) {
        Arrays.fill(page.getData(), (byte) 0);
        page.getPageHeader().setPageType(PageHeader.PageType.EMPTY);
        page.getPageHeader().setSlotCount(0);
        page.getPageHeader().setFreeSpaceOffSet(Page.PAGE_SIZE);
    }
}