package edu.sjsu.cs249.abd.server;

import com.google.protobuf.Empty;
import edu.sjsu.cs249.adb.ABDServiceGrpc;
import edu.sjsu.cs249.adb.AckResponse;
import edu.sjsu.cs249.adb.NameResponse;
import edu.sjsu.cs249.adb.Read1Request;
import edu.sjsu.cs249.adb.Read1Response;
import edu.sjsu.cs249.adb.Read2Request;
import edu.sjsu.cs249.adb.WriteRequest;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class AbdServiceImpl extends ABDServiceGrpc.ABDServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(AbdServerMain.class);

    @Override
    public void name(Empty request, StreamObserver<NameResponse> responseObserver) {
        LOG.info("received name request");
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "AbdServer"; // use default name
        }
        NameResponse rsp = NameResponse.newBuilder().setName(hostname).build();
        responseObserver.onNext(rsp);
        responseObserver.onCompleted();
        LOG.info("name request completed");
    }

    @Override
    public void read1(Read1Request request, StreamObserver<Read1Response> responseObserver) {
        String reg = request.getRegister();
        String val = "XYZ";
        long ts = 12345;

        LOG.info("received read1 request for register: " + reg);

        // TODO: get recent value for reg and then send

        Read1Response rsp = Read1Response.newBuilder().setTimestamp(ts).setValue(val).build();
        responseObserver.onNext(rsp);
        responseObserver.onCompleted();
        LOG.info("read1 request completed - reg: " + reg + " val: " + val + " ts: " + ts);
    }

    @Override
    public void read2(Read2Request request, StreamObserver<AckResponse> responseObserver) {
        String reg = request.getRegister();
        String val = request.getValue();
        long ts = request.getTimestamp();
        LOG.info("received read2 request - reg: " + reg + " val: " + val + " ts: " + ts);
        // TODO: call update method
        AckResponse rsp = AckResponse.newBuilder().build();
        responseObserver.onNext(rsp);
        responseObserver.onCompleted();
        LOG.info("read2 request completed");
    }

    @Override
    public void write(WriteRequest request, StreamObserver<AckResponse> responseObserver) {
        String reg = request.getRegister();
        String val = request.getValue();
        long ts = request.getTimestampe();
        LOG.info("received read2 request - reg:" + reg + " val:" + val + " ts:" + ts);
        // TODO: call update method
        AckResponse rsp = AckResponse.newBuilder().build();
        responseObserver.onNext(rsp);
        responseObserver.onCompleted();
        LOG.info("write request completed");
    }
}
