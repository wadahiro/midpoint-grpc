import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Iterator;

public class TestSearchObjectsClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        SearchObjectsRequest req = SearchObjectsRequest.newBuilder()
                .setObjectType(DefaultObjectType.OBJECT_TYPE)
                .addInclude("activation/effectiveStatus")
                .setQuery(
                        QueryMessage.newBuilder()
                                .setFilter(ObjectFilterMessage.newBuilder()
//                                                .setOrgRoot(FilterOrgRootMessage.newBuilder()
//                                                        .setIsRoot(true))
                                                .setOrgRef(FilterOrgReferenceMessage.newBuilder()
                                                        .setValue(ReferenceMessage.newBuilder()
                                                                .setOid("21c3ba43-83b5-49bb-b6e3-dc415a7e3d24")
                                                        )
                                                        .setScope(OrgFilterScope.SUBTREE)
                                                )
                                )
                )
                .build();

        SearchObjectsResponse res = stub.searchObjects(req);

        System.out.println(res);
    }
}
