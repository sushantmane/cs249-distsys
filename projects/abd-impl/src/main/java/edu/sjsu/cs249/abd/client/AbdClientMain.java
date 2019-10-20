package edu.sjsu.cs249.abd.client;

import edu.sjsu.cs249.adb.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AbdClientMain {

    private static final Logger LOG = LoggerFactory.getLogger(AbdClientMain.class);
    private enum Operation {
        READ,
        WRITE
    };
    private Operation op;
    private String regName;
    private String val;
    private long ts;
    private int maj;
    private static ExecutorService exec;
    private Map<String, ManagedChannel> serverChannels = new HashMap<>();
    private List<String> servers = new ArrayList<>();
    private CountDownLatch latch;
    private AtomicInteger okResps = new AtomicInteger();
    private ConcurrentMap<String, Read1Response> read1Rsp = new ConcurrentHashMap<>();

    private void initThreadPool(int numOfThreads) {
        exec = Executors.newFixedThreadPool(numOfThreads);
    }

    public AbdClientMain() {
        this.ts = System.currentTimeMillis();
    }

    private class BroadCast implements Runnable {
        private ManagedChannel ch;
        private String op;
        private long ts;
        private String reg;
        private String val;
        private String server;

        BroadCast(ManagedChannel ch, String server, String op, long ts, String reg, String val) {
            this.ch = ch;
            this.op = op;
            this.ts = ts;
            this.reg = reg;
            this.val = val;
            this.server = server;
        }

        BroadCast(ManagedChannel ch, String server, String op, String reg) {
            this.ch = ch;
            this.op = op;
            this.reg = reg;
            this.server = server;
        }

        public void run() {
            if (this.op.equals("write")) {
                write();
                return;
            }
            if (this.op.equals("read1")) {
                readPhase1();
                return;
            }
            if (this.op.equals("read2")) {
                readPhase2();
                return;
            }
        }

        void readPhase1() {
            // phase 1
            try {
                ABDServiceGrpc.ABDServiceBlockingStub stub = ABDServiceGrpc.newBlockingStub(ch);
                Read1Request req = Read1Request.newBuilder().setRegister(reg).build();
                Read1Response rsp = stub.withDeadlineAfter(10000, TimeUnit.MILLISECONDS).read1(req);
                System.out.println(Thread.currentThread().getName() + ": " + rsp.getTimestamp());
                read1Rsp.put(server, rsp);
                okResps.getAndIncrement();
            } catch (StatusRuntimeException e) {
                System.out.println(Thread.currentThread().getName() + " Status: " + e.getStatus().getCode() + " : " + server);
            } catch (Exception e) {
                System.out.println(e);
            }
            latch.countDown();
        }

        void readPhase2() {
            // phase 2
            try {
                ABDServiceGrpc.ABDServiceBlockingStub stub = ABDServiceGrpc.newBlockingStub(ch);
                Read2Request req = Read2Request.newBuilder().setRegister(reg).setValue(val).setTimestamp(ts).build();
                stub.withDeadlineAfter(10000, TimeUnit.MILLISECONDS).read2(req);
                okResps.getAndIncrement();
            } catch (StatusRuntimeException e) {
                System.out.println(Thread.currentThread().getName() + " Status: " + e.getStatus().getCode() + " : " + server);
            } catch (Exception e) {
                System.out.println(e);
            }
            latch.countDown();
        }

        void write() {
            try {
                ABDServiceGrpc.ABDServiceBlockingStub stub = ABDServiceGrpc.newBlockingStub(ch);
                WriteRequest req = WriteRequest.newBuilder().setTimestampe(ts)
                        .setRegister(regName).setValue(val).build();
                AckResponse rsp = stub.withDeadlineAfter(10000, TimeUnit.MILLISECONDS).write(req);
                okResps.getAndIncrement();
            } catch (StatusRuntimeException e) {
                System.out.println(Thread.currentThread().getName() + " Status: " + e.getStatus().getCode() + " : " + server);
            } catch (Exception e) {
                System.out.println(e);
            }
            latch.countDown();
        }
    }

    private void setMajorityReq() {
        int n = serverChannels.size();
        maj = (int) Math.ceil((n + 1) / 2.0);
    }

    private void broadCastWrite() {
        for (String server : serverChannels.keySet()) {
            BroadCast bc = new BroadCast(serverChannels.get(server), server, "write", ts, regName, val);
            exec.submit(bc);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (okResps.get() >= maj) {
            System.out.println("success");
        } else {
            System.out.println("failure");
        }
    }



    class Message {
        String reg;
        String val;
        long ts;

        Message(String reg, String val, long ts) {
            this.reg = reg;
            this.val = val;
            this.ts = ts;
        }

        Message(String reg) {
            this.reg = reg;
        }
    }

    private void communicate(Message msg) {

    }

    private void pRead(String reg) {
        Message msg = new Message(reg);
        communicate(msg);
        String val = "largest-val";
        long ts = 99999999;
        Message msg2 = new Message(reg, val, ts);
        communicate(msg2);
    }

    private void broadCastRead() {
        for (String server : serverChannels.keySet()) {
            BroadCast bc = new BroadCast(serverChannels.get(server), server, "read1", regName);
            exec.submit(bc);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!(okResps.get() >= maj)) {
            System.out.println("Failure");
            return; // service not available? (as did not receive rsp from majority
        }

        long newTs = 0;
        String newVal = null;
        Read1Response rsp;
        for (String server : read1Rsp.keySet()) {
            rsp = read1Rsp.get(server);
            System.out.println(rsp);
            if (rsp.getTimestamp() >= newTs) {
                newTs = rsp.getTimestamp();
                newVal = rsp.getValue();
            }
        }

        latch = new CountDownLatch(serverChannels.size());
        okResps.set(0);

        System.out.println("Read phase 2");
        for (String server : serverChannels.keySet()) {
            BroadCast bc = new BroadCast(serverChannels.get(server), server, "read2", newTs,regName, newVal);
            exec.submit(bc);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!(okResps.get() >= maj)) {
            System.out.println("Failure");
            return; // service not available? (as did not receive rsp from majority
        }

        System.out.println(newVal + "{" + newTs + "}");
     }

    private void createChannels() {
        for (String server : servers) {
            ManagedChannel ch = ManagedChannelBuilder.forTarget(server).usePlaintext().build();
            serverChannels.put(server, ch);
        }
    }

    private void closeChannels() {
        for (ManagedChannel ch : serverChannels.values()) {
            ch.shutdownNow();
        }
    }

    private void stopThreadPool() {
        exec.shutdownNow();
    }

    private void initLatch() {
        // latch = new CountDownLatch(maj);
        latch = new CountDownLatch(serverChannels.size());
    }

    public static void main(String[] args) {
        AbdClientMain client = new AbdClientMain();
        if (args.length < 3 || !client.parseOptions(args)) {
            System.err.println("Usage: ./program host1:port1,host2:port2,... <write|read> reg_name value");
            return;
        }

        client.initThreadPool(client.servers.size());
        client.createChannels();
        client.setMajorityReq();
        client.initLatch();

        if (client.op == Operation.WRITE) {
            client.broadCastWrite();
        } else {
            client.broadCastRead();
        }
        client.closeChannels();
        client.stopThreadPool();
    }

    private boolean parseOptions(String[] args) {
        String[] addresses = args[0].trim().split(",");
        servers.addAll(Arrays.asList(addresses)); // add address verification
        String operation = args[1].trim();
        if (!(operation.equals("write") || operation.equals("read"))) {
            return false;
        }
        op = Operation.valueOf(operation.toUpperCase());
        regName = args[2].trim();
        if (op == Operation.WRITE) {
            if (args.length < 4) {
                return false;
            }
            val = args[3].trim();
        }
        return true;
    }
}
