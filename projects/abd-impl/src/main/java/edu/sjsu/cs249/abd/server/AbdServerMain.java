package edu.sjsu.cs249.abd.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AbdServerMain {

    private static final Logger LOG = LoggerFactory.getLogger(AbdServerMain.class);
    Server server;
    int port;

    public AbdServerMain(int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port).addService(new AbdServiceImpl()).build();

    }

    void run() {
        try {
            LOG.info("Starting server on port - " + port);
            server.start();
            LOG.info("Server started successfully");
            server.awaitTermination();
            throw new InterruptedException();
        } catch (IOException e) {
            LOG.error("Unable to start the server: " + e.getMessage());
        } catch (InterruptedException e) {
            LOG.error("Server failed to wait for termination. Please restart the server.");
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Err: Missing port number");
            return;
        }
        int port = Integer.parseInt(args[0]);
        AbdServerMain server = new AbdServerMain(port);
        server.run();
    }
}
