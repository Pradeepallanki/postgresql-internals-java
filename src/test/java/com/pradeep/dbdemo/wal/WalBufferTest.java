package com.pradeep.dbdemo.wal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WalBufferTest {

    @Test
    void newBufferIsEmpty() {
        WalBuffer buffer = new WalBuffer();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
    }

    @Test
    void appendGrowsTheBuffer() {
        WalBuffer buffer = new WalBuffer();
        buffer.append(new byte[]{1, 2, 3});
        buffer.append(new byte[]{4, 5});

        assertFalse(buffer.isEmpty());
        assertEquals(5, buffer.size());
    }

    @Test
    void snapshotIsNonDestructive() {
        WalBuffer buffer = new WalBuffer();
        buffer.append(new byte[]{7, 8, 9});

        byte[] snap = buffer.snapshot();
        assertArrayEquals(new byte[]{7, 8, 9}, snap);
        assertEquals(3, buffer.size(), "snapshot() must not drain the buffer");
    }

    @Test
    void clearEmptiesTheBuffer() {
        WalBuffer buffer = new WalBuffer();
        buffer.append(new byte[]{1, 2, 3});
        buffer.clear();

        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
    }

    @Test
    void snapshotReflectsAppendOrder() {
        WalBuffer buffer = new WalBuffer();
        buffer.append(new byte[]{1});
        buffer.append(new byte[]{2, 3});
        buffer.append(new byte[]{4});

        assertArrayEquals(new byte[]{1, 2, 3, 4}, buffer.snapshot());
    }
}