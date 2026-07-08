package com.pradeep.dbdemo.storage.fsm;

public interface FreeSpaceMap {
    void updateFreeSpace(int pageId, int freeBytes);

    Integer findPageWithAtLeast(int requiredBytes);

    void removePage(int pageId);

    int getFreeSpace(int pageId);
}
