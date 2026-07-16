package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.bufferpool.BufferPool;
import com.pradeep.dbdemo.storage.fsm.DiskFSMImpl;
import com.pradeep.dbdemo.storage.fsm.FSMFile;
import com.pradeep.dbdemo.storage.fsm.FreeSpaceMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeapFileTest {
    private Path dbFile;
    private DiskManager diskManager;
    private BufferPool bufferPool;
    private FreeSpaceMap freeSpaceMap;

    @BeforeEach
    void setup() throws Exception {
        dbFile = Files.createTempFile("mini", ".db");
        diskManager = new DiskManager(dbFile);
        bufferPool = new BufferPool(diskManager);

        FSMFile fsmFile = new FSMFile(bufferPool);
        freeSpaceMap = new DiskFSMImpl(fsmFile, bufferPool);
    }

    @AfterEach
    void cleanup() throws Exception {
        diskManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void shouldAllocateMultiplePages() throws Exception {

        HeapFile heapFile = new HeapFile(bufferPool, freeSpaceMap);

        byte[] tuple = new byte[500];

        List<RID> rids = new ArrayList<>();

        while (diskManager.getPageCount() < 3) {

            rids.add(heapFile.insert(tuple));

        }

        assertEquals(3, diskManager.getPageCount());

    }

    @Test
    void shouldReadEveryTuple() throws Exception {

        HeapFile heapFile = new HeapFile(bufferPool, freeSpaceMap);

        List<RID> rids = new ArrayList<>();

        List<byte[]> tuples = new ArrayList<>();

        for (int i = 0; i < 100; i++) {

            byte[] tuple =
                    ("Tuple-" + i).getBytes();

            tuples.add(tuple);

            rids.add(heapFile.insert(tuple));
        }

        for (int i = 0; i < tuples.size(); i++) {

            assertArrayEquals(
                    tuples.get(i),
                    heapFile.read(rids.get(i))
            );

        }

    }

    @Test
    void shouldDeleteAcrossPages() throws Exception {

        HeapFile heapFile = new HeapFile(bufferPool, freeSpaceMap);

        List<RID> rids = new ArrayList<>();

        for (int i = 0; i < 200; i++) {

            rids.add(
                    heapFile.insert(
                            ("Row-" + i).getBytes()
                    )
            );

        }

        for (int i = 0; i < rids.size(); i += 3) {

            heapFile.delete(rids.get(i));

        }

        for (int i = 0; i < rids.size(); i++) {

            if (i % 3 == 0) {

                assertNull(heapFile.read(rids.get(i)));

            } else {

                assertNotNull(heapFile.read(rids.get(i)));

            }

        }

    }

    @Test
    void shouldSurviveRestart() throws Exception {

        HeapFile heapFile =
                new HeapFile(bufferPool, freeSpaceMap);

        List<RID> rids = new ArrayList<>();

        List<byte[]> tuples = new ArrayList<>();

        for (int i = 0; i < 100; i++) {

            byte[] tuple =
                    ("Persist-" + i).getBytes();

            tuples.add(tuple);

            rids.add(heapFile.insert(tuple));

        }
        bufferPool.flushAll();
        diskManager.close();

        diskManager =
                new DiskManager(dbFile);

        bufferPool = new BufferPool(diskManager);

        heapFile =
                new HeapFile(bufferPool, freeSpaceMap);

        for (int i = 0; i < tuples.size(); i++) {

            assertArrayEquals(

                    tuples.get(i),

                    heapFile.read(rids.get(i))

            );

        }

    }

    @Test
    void shouldReuseSlotNumbersCorrectlyAfterDeleteAndReinsert() throws Exception {

        HeapFile heapFile = new HeapFile(bufferPool, freeSpaceMap);

        List<RID> rids = new ArrayList<>();

        List<byte[]> tuples = new ArrayList<>();

        for (int i = 0; i < 50; i++) {

            byte[] tuple =
                    ("Row-" + i).getBytes();

            tuples.add(tuple);

            rids.add(heapFile.insert(tuple));

        }

        for (int i = 0; i < rids.size(); i += 2) {

            heapFile.delete(rids.get(i));

        }

        // survivors must still be readable by their original RIDs after later inserts have re-used the file
        List<RID> moreRids = new ArrayList<>();

        for (int i = 0; i < 20; i++) {

            moreRids.add(heapFile.insert(("Extra-" + i).getBytes()));

        }

        for (int i = 0; i < rids.size(); i++) {

            if (i % 2 == 0) {

                assertNull(heapFile.read(rids.get(i)));

            } else {

                assertArrayEquals(tuples.get(i), heapFile.read(rids.get(i)));

            }

        }

        for (int i = 0; i < moreRids.size(); i++) {

            assertArrayEquals(("Extra-" + i).getBytes(), heapFile.read(moreRids.get(i)));

        }
    }

    @Test
    void shouldHandleThousandsOfTuples() throws Exception {

        HeapFile heapFile =
                new HeapFile(bufferPool, freeSpaceMap);

        List<RID> rids = new ArrayList<>();

        List<byte[]> tuples = new ArrayList<>();

        for (int i = 0; i < 5000; i++) {

            byte[] tuple =
                    ("User-" + i).getBytes();

            tuples.add(tuple);

            rids.add(heapFile.insert(tuple));

        }

        for (int i = 0; i < tuples.size(); i++) {

            assertArrayEquals(
                    tuples.get(i),
                    heapFile.read(rids.get(i))
            );

        }

    }
}