import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestSearchUserClient {

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
                .setQuery(
                        QueryMessage.newBuilder()
                                .setFilter(
                                        ObjectFilterMessage.newBuilder()
//                                                .setEqPolyString(
//                                                        FilterEntryMessage.newBuilder()
//                                                        .setFullPath("name")
//                                                        .setValue("test")
//                                                )
//                                                .setNot(
//                                                        NotFilterMessage.newBuilder()
//                                                                .setFilter(
//                                                                        ObjectFilterMessage.newBuilder()
//                                                                                .setContains(
//                                                                                        FilterEntryMessage.newBuilder()
//                                                                                                .setFullPath("name")
//                                                                                                .setValue("foo")
//                                                                                )
//                                                                )
//                                                )
                                                .setContains(
                                                        FilterEntryMessage.newBuilder()
                                                                .setFullPath("extension/singleString")
                                                                .setValue("foobar")
                                                )
                                )
                )
                .build();

        SearchUsersResponse res = stub.searchUsers(req);

        System.out.println(res);
    }
}
