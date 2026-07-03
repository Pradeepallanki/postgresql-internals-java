package com.pradeep.dbdemo.storage.btree.internal;

import com.pradeep.dbdemo.storage.btree.SplitResult;

public record InternalSplitResult(long separatorKey,
                                  int newPageId) implements SplitResult {
}
