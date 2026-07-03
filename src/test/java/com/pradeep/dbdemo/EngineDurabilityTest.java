package com.pradeep.dbdemo;

import com.archives.Engine;
import com.archives.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineDurabilityTest {

    @Test
    void dataSurvivesRestart(@TempDir Path dir) throws IOException {
        Path data = dir.resolve("durable.db");

        try (Engine engine = Engine.open(data)) {
            engine.insert(new Row(1, "alice"));
            engine.insert(new Row(2, "bob"));
            engine.insert(new Row(7, "carol"));
        }

        // re-open simulates restarting the process
        try (Engine engine = Engine.open(data)) {
            assertEquals(3, engine.rowCount());
            assertEquals("alice", engine.query(1).orElseThrow().name());
            assertEquals("bob",   engine.query(2).orElseThrow().name());
            assertEquals("carol", engine.query(7).orElseThrow().name());
            assertTrue(engine.query(999).isEmpty());
        }
    }

    @Test
    void rejectsDuplicatePrimaryKey(@TempDir Path dir) throws IOException {
        Path data = dir.resolve("pk.db");
        try (Engine engine = Engine.open(data)) {
            engine.insert(new Row(1, "alice"));
            Engine.PrimaryKeyViolation ex = assertThrows(
                Engine.PrimaryKeyViolation.class,
                () -> engine.insert(new Row(1, "alice-again"))
            );
            assertTrue(ex.getMessage().contains("1"));
            assertEquals("alice", engine.query(1).orElseThrow().name());
            assertEquals(1, engine.rowCount());
        }
    }

    @Test
    void primaryKeyEnforcedAcrossRestart(@TempDir Path dir) throws IOException {
        Path data = dir.resolve("pk-restart.db");
        try (Engine engine = Engine.open(data)) {
            engine.insert(new Row(42, "original"));
        }
        try (Engine engine = Engine.open(data)) {
            assertThrows(Engine.PrimaryKeyViolation.class,
                () -> engine.insert(new Row(42, "shadow")));
            assertEquals("original", engine.query(42).orElseThrow().name());
        }
    }

    @Test
    void torn_trailing_write_is_dropped_on_recovery(@TempDir Path dir) throws IOException {
        Path data = dir.resolve("torn.db");

        try (Engine engine = Engine.open(data)) {
            engine.insert(new Row(1, "alice"));
            engine.insert(new Row(2, "bob"));
        }

        // simulate a crash mid-write: append a partial row
        try (RandomAccessFile raw = new RandomAccessFile(data.toFile(), "rw")) {
            raw.seek(raw.length());
            raw.write(new byte[]{9, 9, 9, 9, 9});
        }

        try (Engine engine = Engine.open(data)) {
            assertEquals(2, engine.rowCount());
            assertEquals("alice", engine.query(1).orElseThrow().name());
            assertEquals("bob",   engine.query(2).orElseThrow().name());
            // engine is usable: next insert lands cleanly at row 3
            engine.insert(new Row(3, "carol"));
            assertEquals(3, engine.rowCount());
        }
    }
}