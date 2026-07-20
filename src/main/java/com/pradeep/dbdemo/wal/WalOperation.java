package com.pradeep.dbdemo.wal;

// starts small; grows as the engine adds new page-mutation shapes.
public enum WalOperation {
    INSERT_TUPLE,
    DELETE_TUPLE,
    UPDATE_TUPLE,
    ALLOCATE_PAGE,
    BTREE_SPLIT,
    BTREE_MERGE,
    UNCLASSIFIED
}