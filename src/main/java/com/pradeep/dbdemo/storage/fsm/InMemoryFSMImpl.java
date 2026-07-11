package com.pradeep.dbdemo.storage.fsm;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public class InMemoryFSMImpl implements FreeSpaceMap {
    private final TreeMap<Integer, Set<Integer>> treeMap = new TreeMap<>(Comparator.reverseOrder());
    private final Map<Integer, Integer> freeSpaceMap = new HashMap<>();

    @Override
    public void updateFreeSpace(int pageId, int freeBytes) {
        Integer old = freeSpaceMap.get(pageId);

        if (old != null) {
            Set<Integer> oldSet = treeMap.get(old);
            oldSet.remove(pageId);

            if (oldSet.isEmpty())
                treeMap.remove(old);
        }

        Set<Integer> newSet =
                treeMap.computeIfAbsent(freeBytes, k -> new HashSet<>());

        newSet.add(pageId);

        freeSpaceMap.put(pageId, freeBytes);
    }

    @Override
    public int findPageWithAtLeast(int requiredBytes) {
        Map.Entry<Integer, Set<Integer>> entry = treeMap.firstEntry();

        if (entry != null && entry.getKey() >= requiredBytes) {
            Optional<Integer> value = entry.getValue().stream().findFirst();
            if (value.isPresent()) return value.get();
        }

        return -1;
    }

    @Override
    public void removePage(int pageId) {
        Integer freeBytes = freeSpaceMap.get(pageId);
        freeSpaceMap.remove(pageId);
        treeMap.get(freeBytes).remove(pageId);
    }

    @Override
    public int getFreeSpace(int pageId) {
        return freeSpaceMap.get(pageId);
    }
}
