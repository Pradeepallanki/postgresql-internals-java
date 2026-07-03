package com.pradeep.dbdemo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {
    @Test
    void runtimeIsJava25() {
        assertEquals(25, Runtime.version().feature());
    }
}
