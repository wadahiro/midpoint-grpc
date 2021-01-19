import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestModifyObjectClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
//        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");

        stub = MetadataUtils.attachHeaders(stub, headers);

        ModifyObjectRequest request = ModifyObjectRequest.newBuilder()
                .setObjectType(DefaultObjectType.SERVICE_TYPE)
                .setName("testService")
                .addModifications(
                        ItemDeltaMessage.newBuilder()
                                .setPath("displayName")
//                                .setItemPath(ItemPathMessage.newBuilder()
//                                        .addPath(QNameMessage.newBuilder()
//                                                .setLocalPart("displayName")
//                                        )
//                                )
                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
                                        .setProperty(
                                                PrismPropertyValueMessage.newBuilder()
                                                        .setPolyString(PolyStringMessage.newBuilder()
                                                                .setOrig("foobar")
                                                        )
                                        )
                                )
                )
                .build();

        ModifyObjectResponse response = stub.modifyObject(request);

        System.out.println(response);

    }
}
