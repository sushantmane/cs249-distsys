package edu.sjsu.cs249.abd.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbdClientMainTest {

    private AbdClientMain client;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void write() {
    }


    @Test
    void mainTest_Write_InClass() {
        String[] args = {
                "10.10.10.21:2222,10.10.10.18:2525,10.10.10.13:2323,10.10.10.12:2020,10.10.10.7:9000",
                "write",
                "x",
                "1099"
        };
        AbdClientMain.main(args);
    }


    @Test
    void mainTest_Write() {
        String[] args = {
                //"localhost:2222,localhost:2223,localhost:2224,127.0.0.1:2225,localhost:2226",
                "localhost:65500",
                "write",
                "x",
                "1099"
        };
        AbdClientMain.main(args);
    }

    @Test
    void mainTest_Read() {
        String[] args = {
//                "localhost:2222,localhost:2223,localhost:2224,127.0.0.1:2225,localhost:2226",
//                "10.10.10.15:8000",
                "10.10.10.21:2222,10.10.10.18:2525,10.10.10.13:2323,10.10.10.12:2020,10.10.10.7:9000",
                "read",
                "x",
        };
        AbdClientMain.main(args);
    }
}