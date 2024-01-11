import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.Constant;
import jp.openstandia.midpoint.grpc.GetSequenceCounterRequest;
import jp.openstandia.midpoint.grpc.GetSequenceCounterResponse;
import jp.openstandia.midpoint.grpc.SelfServiceResourceGrpc;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestGetSequenceCounterClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSequenceCounterRequest req = GetSequenceCounterRequest.newBuilder()
                .setName("Unix UID numbers")
                .build();

        GetSequenceCounterResponse res = stub.getSequenceCounter(req);

        System.out.println(res);
    }
}
