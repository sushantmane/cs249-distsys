package edu.sjsu.cs249.abd.client;

import edu.sjsu.cs249.adb.ABDServiceGrpc;
import edu.sjsu.cs249.adb.Read1Request;
import edu.sjsu.cs249.adb.Read1Response;
import edu.sjsu.cs249.adb.Read2Request;
import edu.sjsu.cs249.adb.WriteRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class ABDClient {

    private enum Operation {
        READ1,
        READ2,
        WRITE
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbdClientMain.class);
    // timeout in millis for latch
    private static int timeout = 120 * 1000;
    // timeout for each rpc
    private static int rpcDeadline = 60 * 1000;

    private Map<String, ManagedChannel> channels = new HashMap<>();
    private int majority;

    private String reg;
    private String val;
    private long ts = System.currentTimeMillis();


    private ExecutorService executor;
    // can be replaced with thread-safe list
    private ConcurrentMap<String, Read1Response> read1Responses = new ConcurrentHashMap<>();
    // latch keeps track of number of acknowledgements received
    private CountDownLatch latch;

    private void setRspLatch(int n) {
        latch = new CountDownLatch(n);
    }

    // creates fixed thread pool of size equal to number of channels
    private void initThreadPool() {
        LOG.info("Creating thread pool of size: " + channels.size());
        executor = Executors.newFixedThreadPool(channels.size());
    }

    private void shutdownThreadPool() {
        LOG.info("Shutting down the thread pool...");
        executor.shutdownNow();
    }

    // for register writes use this constructor
    public ABDClient(List<String> servers, String reg, String val) {
        this(servers, reg);
        this.val = val;
    }

    // for register reads use this constructor
    public ABDClient(List<String> servers, String reg) {
        this.reg = reg;
        this.majority = (int) (Math.floor(servers.size() / 2.0) + 1);
        // create the channels for all the servers
        for (String server : servers) {
            channels.put(server, ManagedChannelBuilder.forTarget(server).usePlaintext().build());
        }
    }

    // close the channels
    private void closeChannels() {
        for (ManagedChannel ch : channels.values()) {
            ch.shutdownNow();
        }
    }

    private class Message implements Runnable {

        private String reg = ABDClient.this.reg;
        private String val;
        private long ts;
        private String server;
        private ManagedChannel ch;
        private Operation op;

        private Message(String server, Operation op) {
            this.server = server;
            this.ch = ABDClient.this.channels.get(server);
            this.op = op;
            if (this.op == Operation.WRITE || this.op == Operation.READ2) {
                this.val = ABDClient.this.val;
                this.ts = ABDClient.this.ts;
            }
        }

        @Override
        public void run() {
            try {
                ABDServiceGrpc.ABDServiceBlockingStub stub = ABDServiceGrpc.newBlockingStub(ch);
                if (op == Operation.READ1) {
                    Read1Request req = Read1Request.newBuilder().setRegister(reg).build();
                    LOG.debug("Sending read1 msg -" + server + " reg:" + reg);
                    Read1Response rsp = stub.withDeadlineAfter(ABDClient.rpcDeadline,
                            TimeUnit.MILLISECONDS).read1(req);
                    LOG.debug("Received response for read1 from " + server + " reg:" + reg
                            + " val:" + rsp.getValue() + " ts:" + rsp.getTimestamp());
                    ABDClient.this.read1Responses.put(server, rsp);
                } else if (op == Operation.READ2){
                    LOG.info("Sending read2 msg -" + server + " reg:" + reg
                            + " val:" + val + " ts:" + ts);
                    Read2Request req = Read2Request.newBuilder().setRegister(reg)
                            .setValue(val).setTimestamp(ts).build();
                    stub.withDeadlineAfter(ABDClient.rpcDeadline, TimeUnit.MILLISECONDS).read2(req);
                    LOG.debug("Received read2-ack from " + server + " reg:" + reg);
                } else {
                    WriteRequest req = WriteRequest.newBuilder().setTimestampe(ts).setRegister(reg)
                            .setValue(val).build();
                    LOG.debug("Sending write msg -" + server + " reg:" + reg + " val:" + val + " ts:" + ts);
                    stub.withDeadlineAfter(ABDClient.rpcDeadline, TimeUnit.MILLISECONDS).write(req);
                    LOG.debug("Received write-ack from " + server + " reg:" + reg);
                }
                latch.countDown();
            } catch (StatusRuntimeException e) {
                LOG.error("Request failed - status: " + e.getStatus().getCode() + " server: " + server);
            } catch (Exception e) {
                LOG.error("Request failed - msg: " + e + " server: " + server);
            }
        }
    }

    private boolean communicate(Operation op) {
        setRspLatch(majority);
        // send read1-msg to all servers
        for (String server : channels.keySet()) {
            Message r1Msg = new Message(server, op);
            executor.submit(r1Msg);
        }
        // wait for majority acks
        boolean latchOpen = false;
        try {
            latchOpen = latch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOG.error("Failed to set latch. Msg:" + e.getMessage());
            // TODO: Use fallback method
        }
        return latchOpen;
    }

    private boolean readRegister() {
        // PHASE-I
        LOG.debug("Running read1 - reg:" + reg);
        if (!communicate(Operation.READ1)) {
            LOG.error("read reg:" + reg + " - FAILED");
            return false;
        }
        LOG.debug("Successfully completed read1 - reg:" + reg);
        // find latest value of the register
        long maxTs = -1;
        String latestVal = "";
        for (Read1Response rsp : read1Responses.values()) {
            if (rsp.getTimestamp() >= maxTs) {
                maxTs = rsp.getTimestamp();
                latestVal = rsp.getValue();
            }
        }
        // PHASE-II
        ts = maxTs;
        val = latestVal;
        LOG.debug("Running read2 - reg:" + reg + " val:" + val + " ts:" + ts);
        if (!communicate(Operation.READ2)) {
            LOG.error("Failed to propagate value of register. reg:" + reg
                    + " val:" + val + " ts:" + ts);
            LOG.error("read reg:" + reg + " - FAILED");
            return false;
        }
        LOG.debug("Successfully completed read2 - reg:" + reg);
        LOG.info("read reg:" + reg + " value:" + val + "{" + ts + "} - OK");
        return true;
    }

    private boolean writeRegister() {
        if (communicate(Operation.WRITE)) {
            LOG.info("write reg:" + reg + " value:" + val + "{" + ts + "} - OK");
            return true;
        }
        LOG.error("write reg:" + reg + " value:" + val + "{" + ts + "} - FAILED");
        return false;
    }

    private static void usage() {
        System.out.println("Usage: java ABDClient host1:port1,host2:port2,..." +
                "<write|read> reg_name value");
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            usage();
            return;
        }
        String[] addresses = args[0].trim().split(",");
        List<String> servers = new ArrayList<>();
        for (String addr : addresses) {
            try {
                // verify whether port is numeric - replace with better approach
                Integer.parseInt(addr.split(":")[1]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.err.println("Error: Invalid hostname/port input");
                usage();
                return;
            }
            servers.add(addr);
        }
        String op = args[1].trim();
        if (!(op.equals("write") || op.equals("read"))) {
            System.err.println("Error: Invalid operation");
            usage();
            return;
        }
        String regName = args[2].trim();
        ABDClient client;
        if (op.equals("write")) {
            if (args.length < 4) {
                System.err.println("Error: Insufficient arguments");
                usage();
                return;
            }
            String val = args[3].trim();
            client = new ABDClient(servers, regName, val); // write
        } else {
            client = new ABDClient(servers, regName); // read
        }
        client.initThreadPool();
        if (op.equals("write")) {
            if (client.writeRegister()) {
                System.out.println("success");
            } else {
                System.out.println("failure");
            }
        } else {
            if (client.readRegister()) {
                System.out.println(client.val + "{" + client.ts + "}");
            } else {
                System.out.println("Failed");
            }
        }
        client.closeChannels();
        client.shutdownThreadPool();
    }
}