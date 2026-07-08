package com.pradeep.dbdemo.storage;

import com.pradeep.dbdemo.cache.BufferPool;
import com.pradeep.dbdemo.storage.fsm.FreeSpaceMap;
import com.pradeep.dbdemo.storage.fsm.InMemoryFSMImpl;
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
        freeSpaceMap = new InMemoryFSMImpl();
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
    void shouldHandleThousandsOfTuples() throws Exception {

        HeapFile heapFile =
                new HeapFile(bufferPool, freeSpaceMap);

        List<RID> rids = new ArrayList<>();

        List<byte[]> tuples = new ArrayList<>();

        for (int i = 0; i < 5000; i++) {

            byte[] tuple =
                    ("User-" + i).getBytes();

            tuples.add(tuple);
            System.out.println("Itr" + i);
            if(i==637) {
                System.out.println("Hey");
            }
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