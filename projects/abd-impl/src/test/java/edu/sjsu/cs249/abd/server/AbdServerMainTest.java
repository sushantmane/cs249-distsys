package edu.sjsu.cs249.abd.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbdServerMainTest {

    @Test
    void main() {
        String port = "65501";
        String[] args = {port};
        AbdServerMain.main(args);
    }
}