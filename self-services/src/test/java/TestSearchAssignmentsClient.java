import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestSearchAssignmentsClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:Test5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        SearchAssignmentsRequest req = SearchAssignmentsRequest.newBuilder()
                .setOid("8a1335af-f89d-4840-9c2d-0016f48dcc91")
//                .setResolveRefNames(true)
                .setQuery(
                        QueryMessage.newBuilder()
                                .setFilter(ObjectFilterMessage.newBuilder()
                                        .setRef(FilterReferenceMessage.newBuilder()
                                                .setFullPath("archetypeRef")
                                                .setValue(ReferenceMessage.newBuilder()
                                                        .setObjectType(DefaultObjectType.ARCHETYPE_TYPE)
                                                        .setOid("50d27f24-18c2-4d60-9004-19ae4884ebf8")))
                                )
                )
                .build();

        SearchAssignmentsResponse res = stub.searchAssignments(req);

        System.out.println(res);
    }
}
