package com.pradeep.dbdemo.jobs;

import com.pradeep.dbdemo.bufferpool.BufferPool;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScheduledBackgroundWriter implements BackgroundWriter {
    private static final Logger LOGGER = Logger.getLogger(ScheduledBackgroundWriter.class.getName());

    public static final int DEFAULT_BATCH_SIZE = 100;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "background-writer");
        t.setDaemon(true);
        return t;
    });

    private final BufferPool bufferPool;
    private final int batchSize;

    public ScheduledBackgroundWriter(BufferPool bufferPool) {
        this(bufferPool, DEFAULT_BATCH_SIZE);
    }

    public ScheduledBackgroundWriter(BufferPool bufferPool, int batchSize) {
        this.bufferPool = bufferPool;
        this.batchSize = batchSize;
    }

    @Override
    public void start(long initialDelayMillis, long periodMillis) {
        executor.scheduleWithFixedDelay(
                this::runCycle,
                initialDelayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private void runCycle() {
        // scheduleWithFixedDelay silently stops rescheduling if the task throws anything unchecked, so nothing may escape this method.
        try {
            bufferPool.flushSomeDirtyPages(batchSize);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "background flush cycle failed", t);
        }
    }

    @Override
    public void close() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}