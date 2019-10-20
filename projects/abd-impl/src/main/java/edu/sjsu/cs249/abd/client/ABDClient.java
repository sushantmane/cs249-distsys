package edu.sjsu.cs249.abd.client;

import edu.sjsu.cs249.adb.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ABDClient {

    private enum Operation {
        READ1,
        READ2,
        WRITE
    };

    private static final Logger LOG = LoggerFactory.getLogger(AbdClientMain.class);
    private AtomicInteger numOfAcks = new AtomicInteger();
    private Map<String, ManagedChannel> channels = new HashMap<>();
    private long ts = System.currentTimeMillis();
    private int majority;
    private String reg;
    private String val;
    private ExecutorService executor;
    // can be replaced with thread-safe list
    private ConcurrentMap<String, Read1Response> read1Responses = new ConcurrentHashMap<>();
    // countdown latch
    private CountDownLatch latch;
    // set timeout in seconds for latch
    private int to = 10 * 60; // 10 mins


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

    // for writes use this constructor
    public ABDClient(List<String> servers, String reg, String val) {
        this(servers, reg);
        this.val = val;
    }

    // for reads use this constructor
    public ABDClient(List<String> servers, String reg) {
        this.reg = reg;
        this.majority = (int) (Math.floor(servers.size() / 2.0) + 1);
        // create the channels for all servers
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

        private int deadline  = 50 * 1000; // default = 50 secs, it's a long time! :)
        private String reg;
        private String val;
        private long ts;
        private String server;
        private ManagedChannel ch;
        private Operation op;

        private Message(String server, Operation op) {
            this.reg = ABDClient.this.reg;
            this.server = server;
            this.ch = ABDClient.this.channels.get(server);
            this.op = op;
            if (this.op == Operation.WRITE) {
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
                    Read1Response rsp = stub.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS)
                            .read1(req);
                    LOG.debug("Received response for read1 from " + server + " reg:" + reg
                            + " val:" + rsp.getValue() + " ts:" + rsp.getTimestamp());
                    ABDClient.this.read1Responses.put(server, rsp);
                } else if (op == Operation.READ2){
                    LOG.info("Sending read2 msg -" + server + " reg:" + reg
                            + " val:" + val + " ts:" + ts);
                    Read2Request req = Read2Request.newBuilder().setRegister(reg)
                            .setValue(val).setTimestamp(ts).build();
                    stub.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS).read2(req);
                    LOG.debug("Received read2-ack from " + server + " reg:" + reg);
                } else {
                    WriteRequest req = WriteRequest.newBuilder().setTimestampe(ts).setRegister(reg)
                            .setValue(val).build();
                    LOG.debug("Sending write msg -" + server + " reg:" + reg + " val:" + val + " ts:" + ts);
                    stub.withDeadlineAfter(deadline, TimeUnit.MILLISECONDS).write(req);
                    LOG.debug("Received write-ack from " + server + " reg:" + reg);
                }
                numOfAcks.getAndIncrement();
                latch.countDown();
            } catch (StatusRuntimeException e) {
                LOG.error("Request failed - status: " + e.getStatus().getCode() + " server: " + server);
            } catch (Exception e) {
                LOG.error("Request failed - msg: " + e.getMessage() + " server: " + server);
            }
        }
    }

    public void readRegister() {
        // set latch
        setRspLatch(majority);
        // send read1-msg to all servers
        for (String server : channels.keySet()) {
            Message r1Msg = new Message(server, Operation.READ1);
            executor.submit(r1Msg);
        }
        // wait for majority acks
        boolean isLatchOpen = false;
        try {
            isLatchOpen = latch.await(to, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Failed to set latch. Msg:" + e.getMessage());
            // TODO: Use fallback method
        }
        if (!isLatchOpen) {
            // this means we failed to get response from majority
            System.out.println("Failure");
            return;
        }

        // find latest value of the register
        long maxTs = 0;
        String latestVal = null;
        for (Read1Response rsp : read1Responses.values()) {
            if (rsp.getTimestamp() >= maxTs) {
                maxTs = rsp.getTimestamp();
                latestVal = rsp.getValue();
            }
        }

        // prepare for phase-2
        ts = maxTs;
        val = latestVal;
        numOfAcks.set(0);
        // re-set latch
        setRspLatch(majority);
        // send read2-msg to all servers
        for (String server : channels.keySet()) {
            Message r2Msg = new Message(server, Operation.READ1);
            executor.submit(r2Msg);
        }
        // wait for majority acks
        isLatchOpen = false;
        try {
            isLatchOpen = latch.await(to, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Failed to set latch. Msg:" + e.getMessage());
            // TODO: Use fallback method
        }
        if (!isLatchOpen) {
            // this means we failed to get response from majority
            System.out.println("Failure");
            return;
        }

        System.out.println(val + "{" + ts + "}");
    }

    public boolean writeRegister() {
        // set latch
        setRspLatch(majority);

        // send write-msg to all servers
        for (String server : channels.keySet()) {
            Message wMsg = new Message(server, Operation.WRITE);
            executor.submit(wMsg);
        }

        // wait for majority acks
        boolean isLatchOpen = false;
        try {
            isLatchOpen = latch.await(to, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Failed to set latch. Msg:" + e.getMessage());
            // TODO: Use fallback method
        }
        if (isLatchOpen) {
            // this means we go ack from majority
            return true;
        }

        // not sure --> many possibilities?
        return false;
    }

    private static void usage() {
        System.out.println("Usage: java ABDClient host1:port1,host2:port2,... <write|read> reg_name value");
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
                // verify whether port is numeric
                // TODO: use regex
                int port = Integer.parseInt(addr.split(":")[1]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.err.println("Error: Invalid hostname/port input");
                usage();
                return;
            }
            servers.add(addr);
        }

        Operation op;
        try {
            op = Operation.valueOf(args[1].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid operation");
            usage();
            return;
        }
        String regName = args[2].trim();

        ABDClient client;
        if (op == ABDClient.Operation.WRITE) {
            if (args.length < 4) {
                System.err.println("Error: Insufficient arguments");
                usage();
                return;
            }
            String val = args[3].trim();
            // write operation
            client = new ABDClient(servers, regName, val);
        } else {
            // read operation
            client = new ABDClient(servers, regName);
        }
        client.initThreadPool();
        if (op == Operation.WRITE) {
            boolean res = client.writeRegister();
            if (res) {
                System.out.println("success");
            } else {
                System.out.println("failure");
            }
        }
        else {
            client.readRegister();
        }
        client.closeChannels();
        client.shutdownThreadPool();
    }


}