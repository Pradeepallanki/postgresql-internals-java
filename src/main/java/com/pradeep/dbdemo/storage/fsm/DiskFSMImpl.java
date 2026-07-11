package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;

import java.io.IOException;

public class DiskFSMImpl implements FreeSpaceMap {
    private final FSMFile fsmFile;
    private final BufferPool bufferPool;

    public DiskFSMImpl(FSMFile fsmFile, BufferPool bufferPool) {
        this.fsmFile = fsmFile;
        this.bufferPool = bufferPool;
    }

    @Override
    public void updateFreeSpace(int pageId, int freeBytes) {
        try {
            FSMMetaPage metaPage = fsmFile.getMetaPage();
            int rootPageId = metaPage.getRootPageId();

            if (rootPageId == FSMMetaPage.INVALID_PAGE_ID) {
                FSMLeafPage rootLeaf = fsmFile.createLeafPage();
                rootLeaf.updateFreeSpace(pageId, freeBytes);

                metaPage.setRootPageId(rootLeaf.getPage().getPageId());
                metaPage.setTreeHeight(1);
                return;
            }

            applyUpdate(rootPageId, pageId, freeBytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int applyUpdate(int nodePageId, int heapPageId, int freeBytes)
            throws IOException {

        Page page = bufferPool.fetchPage(nodePageId);

        if (page.getPageHeader().getPageType() == PageHeader.PageType.FSM_LEAF) {
            FSMLeafPage leaf = fsmFile.getLeafPage(nodePageId);
            leaf.updateFreeSpace(heapPageId, freeBytes);
            return leaf.getMaxFreeSpace();
        }

        FSMInternalPage internal = fsmFile.getInternalPage(nodePageId);

        int childPageId = pickChildForUpdate(internal, heapPageId);

        int childMax = applyUpdate(childPageId, heapPageId, freeBytes);

        internal.updateChildSummary(childPageId, childMax);

        return internal.getMaxSummary();
    }

    private int pickChildForUpdate(FSMInternalPage internal, int heapPageId) {
        int lastChildIndex = internal.getEntryCount() - 1;

        if (lastChildIndex < 0) {
            throw new IllegalStateException(
                    "FSM internal page has no children.");
        }

        for (int i = 0; i < internal.getEntryCount(); i++) {
            int candidate = internal.getChildPageId(i);
            try {
                Page candidatePage = bufferPool.fetchPage(candidate);
                if (candidatePage.getPageHeader().getPageType()
                        == PageHeader.PageType.FSM_LEAF
                        && fsmFile.getLeafPage(candidate).containsHeapPage(heapPageId)) {
                    return candidate;
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return internal.getChildPageId(lastChildIndex);
    }

    @Override
    public int findPageWithAtLeast(int requiredBytes) {
        int rootPageId = fsmFile.getMetaPage().getRootPageId();

        if (rootPageId == FSMMetaPage.INVALID_PAGE_ID) {
            return FSMMetaPage.INVALID_PAGE_ID;
        }

        try {
            return descend(requiredBytes, rootPageId);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int descend(int requiredBytes, int pageId) throws IOException {
        Page page = bufferPool.fetchPage(pageId);

        if (page.getPageHeader().getPageType() == PageHeader.PageType.FSM_LEAF) {
            return fsmFile.getLeafPage(pageId).getPageIdWithAtLeast(requiredBytes);
        }

        FSMInternalPage internal = fsmFile.getInternalPage(pageId);

        int childPageId = internal.findChildIdWithAtLeast(requiredBytes);

        if (childPageId == FSMMetaPage.INVALID_PAGE_ID) {
            return FSMMetaPage.INVALID_PAGE_ID;
        }

        return descend(requiredBytes, childPageId);
    }

    @Override
    public void removePage(int pageId) {
        try {
            int rootPageId = fsmFile.getMetaPage().getRootPageId();

            if (rootPageId == FSMMetaPage.INVALID_PAGE_ID) {
                return;
            }

            if (!isTracked(rootPageId, pageId)) {
                return;
            }

            applyUpdate(rootPageId, pageId, 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getFreeSpace(int pageId) {
        int rootPageId = fsmFile.getMetaPage().getRootPageId();

        if (rootPageId == FSMMetaPage.INVALID_PAGE_ID) {
            throw new IllegalArgumentException(
                    "FSM is empty; heap page " + pageId + " is not tracked.");
        }

        try {
            return lookupFreeSpace(rootPageId, pageId);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int lookupFreeSpace(int nodePageId, int heapPageId)
            throws IOException {

        Page page = bufferPool.fetchPage(nodePageId);

        if (page.getPageHeader().getPageType() == PageHeader.PageType.FSM_LEAF) {
            FSMLeafPage leaf = fsmFile.getLeafPage(nodePageId);
            return leaf.getFreeSpace(heapPageId);
        }

        FSMInternalPage internal = fsmFile.getInternalPage(nodePageId);

        int owner = findOwningChild(internal, heapPageId);

        if (owner == FSMMetaPage.INVALID_PAGE_ID) {
            throw new IllegalArgumentException(
                    "Heap page " + heapPageId + " is not tracked in FSM.");
        }

        return lookupFreeSpace(owner, heapPageId);
    }

    private boolean isTracked(int nodePageId, int heapPageId) throws IOException {
        Page page = bufferPool.fetchPage(nodePageId);

        if (page.getPageHeader().getPageType() == PageHeader.PageType.FSM_LEAF) {
            return fsmFile.getLeafPage(nodePageId).containsHeapPage(heapPageId);
        }

        FSMInternalPage internal = fsmFile.getInternalPage(nodePageId);

        int owner = findOwningChild(internal, heapPageId);

        return owner != FSMMetaPage.INVALID_PAGE_ID;
    }

    private int findOwningChild(FSMInternalPage internal, int heapPageId)
            throws IOException {

        for (int i = 0; i < internal.getEntryCount(); i++) {
            int candidate = internal.getChildPageId(i);
            if (isTracked(candidate, heapPageId)) {
                return candidate;
            }
        }

        return FSMMetaPage.INVALID_PAGE_ID;
    }
}