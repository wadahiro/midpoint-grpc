import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.Constant;
import jp.openstandia.midpoint.grpc.SearchOrgsResponse;
import jp.openstandia.midpoint.grpc.SearchRequest;
import jp.openstandia.midpoint.grpc.SelfServiceResourceGrpc;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestSearchOrgClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        SearchRequest req = SearchRequest.newBuilder()
                .build();

        SearchOrgsResponse res = stub.searchOrgs(req);

        System.out.println(res);
    }
}
