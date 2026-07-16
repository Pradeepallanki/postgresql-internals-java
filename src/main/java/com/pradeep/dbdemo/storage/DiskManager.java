package com.pradeep.dbdemo.storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiskManager implements AutoCloseable {
    private final RandomAccessFile file;

    public DiskManager(Path dataDir) throws IOException {
        Path parentDir = dataDir.getParent();
        if (null != parentDir) Files.createDirectories(parentDir);

        file = new RandomAccessFile(dataDir.toFile(), "rw");
    }

    public int allocatePage() throws IOException {
        long fileLength = file.length();
        /*
         * each page size is of 8kb, so how many pages a file contains? it should be filelength/pageSize(i.e,,8Kb).
         * We will allocate the new page at the end of file (append only). So new pageId would be filelength/pageSize.
         * we simply seek to the end of the file, write a new page of 8Kb in size and return new PageId
         * */
        int pageId = (int) fileLength / Page.PAGE_SIZE;

        Page page = new Page(pageId);
        writePage(page);
        return pageId;
    }

    public void writePage(Page page) throws IOException {
        /*
         * Whenever we want to write to a new file, we want to know which is the offset, from which we can start writing.
         * A disk is contains tracks, and sector. Imagine a round shaped object, and draw 5 circles in it like a running track.
         * Now divide the circle in 5 equals triangle shape. Imagine you are cutting a water melon.
         * The circles you drew are called tracks and divide bars you added are called sectors.
         * A disk has a spindle moving, a block can be identified using it's track number and sector number.
         * Let's say a block is of size 512 bytes, means it has 512 offsets. If you want to know where your data resides, you need to look up that offset and read from there.
         * Seeking offset in our case is made easy because we always allocate pages in 8Kb size. So starting offset from which you want to write, is simply pageId*pageSize.
         * Once a block is brought into the memory, simply add the data into it and flush.
         * */
        long offSet = (long) page.getPageId() * Page.PAGE_SIZE;
        file.seek(offSet);

        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        page.getPageHeader().writeTo(buffer); // This is actually an anti pattern. Ideally page.getData() itself should have everything including the header, diskManager should never treat data as separate and header as separate. But for the sake of clarity, keeping the header separately inside the page.
        file.write(page.getData());
    }

    public Page readPage(int pageId) throws IOException {
        long offSet = (long) pageId * Page.PAGE_SIZE;
        file.seek(offSet);

        byte[] data = new byte[Page.PAGE_SIZE];
        file.readFully(data);

        ByteBuffer buffer = ByteBuffer.wrap(data);

        PageHeader pageHeader = PageHeader.readFrom(buffer);
        return new Page(pageId, data, pageHeader);
    }

    public long getPageCount() throws IOException {
        return file.length() / Page.PAGE_SIZE;
    }

    @Override
    public void close() throws Exception {
        file.close();
    }
}
