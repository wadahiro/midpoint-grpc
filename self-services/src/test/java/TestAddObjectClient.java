import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestAddObjectClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:Test5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        AddObjectRequest request = AddObjectRequest.newBuilder()
                .setObjectType(DefaultObjectType.SERVICE_TYPE)
                .setObject(
                        PrismContainerMessage.newBuilder()
                                .addValues(PrismContainerValueMessage.newBuilder()
                                        .putValue("name", ItemMessage.newBuilder()
                                                .setProperty(PrismPropertyMessage.newBuilder()
                                                        .addValues(PrismPropertyValueMessage.newBuilder()
                                                                .setPolyString(PolyStringMessage.newBuilder()
                                                                        .setOrig("testService")
                                                                )
                                                                .build()
                                                        )
                                                        .build()
                                                )
                                                .build()
                                        )
                                )
                ).build();


        AddObjectResponse response = stub.addObject(request);

        System.out.println(response);

    }
}
