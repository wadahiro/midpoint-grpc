import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Iterator;

public class TestSearchObjectsAsStreamClient {

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
                .setObjectType(DefaultObjectType.USER_TYPE)
                .setQuery(
                        QueryMessage.newBuilder()
//                                .setFilter(
//                                        ObjectFilterMessage.newBuilder()
////                                                .setEqPolyString(
////                                                        FilterEntryMessage.newBuilder()
////                                                        .setFullPath("name")
////                                                        .setValue("test")
////                                                )
////                                                .setNot(
////                                                        NotFilterMessage.newBuilder()
////                                                                .setFilter(
////                                                                        ObjectFilterMessage.newBuilder()
////                                                                                .setContains(
////                                                                                        FilterEntryMessage.newBuilder()
////                                                                                                .setFullPath("name")
////                                                                                                .setValue("foo")
////                                                                                )
////                                                                )
////                                                )
//                                                .setContains(
//                                                        FilterEntryMessage.newBuilder()
//                                                                .setFullPath("extension/singleString")
//                                                                .setValue("foobar")
//                                                )
//                                )
                                .setPaging(
                                        PagingMessage.newBuilder()
                                                .addOrdering(ObjectOrderingMessage.newBuilder()
                                                        .setOrderBy("fullName")
                                                        .setOrderDirection(OrderDirectionType.DESCENDING)
                                                )
                                )
                )
                .build();

        Iterator<SearchObjectsResponse> res = stub.searchObjectsAsStream(req);

        while (res.hasNext()) {
            SearchObjectsResponse next = res.next();
            next.getResultsList().stream().forEach(x ->
                    System.out.println(x.getContainer().getValues(0)
                            .getValueMap().get("name")
                            .getProperty()
                    .getValues(0)
                    .getPolyString()
                    .getOrig()));
        }

        System.out.println(res);
    }
}
