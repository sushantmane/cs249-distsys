package edu.sjsu.cs249.abd.client;

import org.junit.jupiter.api.Test;

class ABDClientTest {

    @Test
    void read() {
    }

    @Test
    void write() {
    }

    @Test
    void main_WriteTest_SingleServer() {
        String[] args = {
                "127.0.0.1:65501",
                "write",
                "REG_1",
                "013736658"
        };
        ABDClient.main(args);
    }

    @Test
    void main_WriteTest() {
        String[] args = {
                "localhost:65501,localhost:65502,localhost:65503,127.0.0.1:65504,127.0.0.1:65505,127.0.0.1:65505",
                "write",
                "REG_1",
                "013736658"
        };
        ABDClient.main(args);
    }

    @Test
    void main_ReadTest() {
        String[] args = {
                "localhost:9999",
                "read",
                "REG_1"
        };
        ABDClient.main(args);
    }
}