package com.pradeep.dbdemo.storage.btree.leaf;

import com.pradeep.dbdemo.storage.RID;

public record BtreeLeafEntry(
        long key,
        RID rid
) {

    public static final int SIZE =
            Long.BYTES
                    + Integer.BYTES
                    + Short.BYTES;

}
