package com.pradeep.dbdemo.storage.fsm;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.Page;
import com.pradeep.dbdemo.storage.PageHeader;

import java.io.IOException;

public class FSMFile {

    private final BufferPool bufferPool;
    private final int metaPageId;
    private final FSMMetaPage metaPage;

    public FSMFile(BufferPool bufferPool) throws IOException {
        this.bufferPool = bufferPool;
        this.metaPageId = bufferPool.allocatePage();

        Page page = bufferPool.fetchPage(metaPageId);
        page.getPageHeader().setPageType(PageHeader.PageType.FSM_META);

        this.metaPage = FSMMetaPage.createFresh(page, metaPageId + 1, bufferPool);
    }

    public FSMFile(BufferPool bufferPool, int metaPageId) throws IOException {
        this.bufferPool = bufferPool;
        this.metaPageId = metaPageId;

        Page page = bufferPool.fetchPage(metaPageId);

        this.metaPage = new FSMMetaPage(page, bufferPool);
    }

    public FSMMetaPage getMetaPage() {
        return metaPage;
    }

    public int getMetaPageId() {
        return metaPageId;
    }

    public FSMLeafPage getLeafPage(int pageId) throws IOException {
        return new FSMLeafPage(bufferPool.fetchPage(pageId), bufferPool);
    }

    public FSMInternalPage getInternalPage(int pageId) throws IOException {
        return new FSMInternalPage(bufferPool.fetchPage(pageId), bufferPool);
    }

    public FSMLeafPage createLeafPage() throws IOException {
        int pageId = allocatePage();

        Page page = bufferPool.fetchPage(pageId);
        page.getPageHeader().setPageType(PageHeader.PageType.FSM_LEAF);

        return FSMLeafPage.createFresh(page, bufferPool);
    }

    public FSMInternalPage createInternalPage() throws IOException {
        int pageId = allocatePage();

        Page page = bufferPool.fetchPage(pageId);
        page.getPageHeader().setPageType(PageHeader.PageType.FSM_INTERNAL);

        return FSMInternalPage.createFresh(page, bufferPool);
    }

    public int allocatePage() throws IOException {
        return bufferPool.allocatePage();
    }
}