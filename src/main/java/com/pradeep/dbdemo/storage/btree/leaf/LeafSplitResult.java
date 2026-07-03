package com.pradeep.dbdemo.storage.btree.leaf;

import com.pradeep.dbdemo.storage.btree.SplitResult;

public record LeafSplitResult(
        long separatorKey,
        int newPageId
) implements SplitResult {
}
