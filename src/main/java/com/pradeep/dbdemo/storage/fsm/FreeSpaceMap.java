package com.pradeep.dbdemo.storage.fsm;

public interface FreeSpaceMap {
    void updateFreeSpace(int pageId, int freeBytes);

    int findPageWithAtLeast(int requiredBytes);

    void removePage(int pageId);

    int getFreeSpace(int pageId);
}
