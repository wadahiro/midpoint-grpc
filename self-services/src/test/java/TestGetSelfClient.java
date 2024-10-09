import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestGetSelfClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:Test5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
//        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSelfRequest req = GetSelfRequest.newBuilder()
                .build();

        GetSelfResponse self = stub.getSelf(req);

        UserTypeMessage profile = self.getProfile();
        System.out.println(profile);
    }
}
