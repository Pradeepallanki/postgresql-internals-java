package com.pradeep.dbdemo.jobs;

public interface BackgroundWriter extends AutoCloseable {
    void start(long initialDelayMillis, long periodMillis);
}