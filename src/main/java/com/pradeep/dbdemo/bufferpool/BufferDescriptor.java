package com.pradeep.dbdemo.bufferpool;

import com.pradeep.dbdemo.storage.Page;

public class BufferDescriptor {
    private int bufferId;
    private final int pageId;
    private boolean isDirty;
    private int pinCount;
    private int usageCount;
    private final Page page;

    public BufferDescriptor(int pageId, Page page) {
        this.pageId = pageId;
        this.page = page;
    }

    public void unPin() {
        if (this.pinCount > 0) {
            this.pinCount--;
        }
    }

    public void pin() {
        pinCount++;
    }

    public int getPageId() {
        return pageId;
    }

    public int getBufferId() {
        return bufferId;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public int getPinCount() {
        return pinCount;
    }

    public Page getPage() {
        return page;
    }

    public void setBufferId(int bufferId) {
        this.bufferId = bufferId;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public void setPinCount(int pinCount) {
        this.pinCount = pinCount;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public void markUnDirty() {
        this.isDirty = false;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }
}
