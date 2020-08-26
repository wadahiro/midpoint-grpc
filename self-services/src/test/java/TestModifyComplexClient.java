import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestModifyComplexClient {

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

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDelta.newBuilder()
                                .setPath("extension/singleComplex")
                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
                                        .setContainer(PrismContainerValueMessage.newBuilder()
                                                .putValue("name", PrismValueMessage.newBuilder()
                                                        .setNamespaceURI("http://test.example.com/my")
                                                        .setProperty(
                                                                PrismPropertyMessage.newBuilder()
                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
                                                                                .setString("test1")
                                                                        )
                                                        )
                                                        .build()
                                                )
                                                .putValue("description", PrismValueMessage.newBuilder()
                                                        .setNamespaceURI("http://test.example.com/my")
                                                        .setProperty(
                                                                PrismPropertyMessage.newBuilder()
                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
                                                                                .setString("test1-single")
                                                                        )
                                                        )
                                                        .build()
                                                )
                                                .putValue("multipleString", PrismValueMessage.newBuilder()
                                                        .setNamespaceURI("http://test.example.com/my")
                                                        .setProperty(
                                                                PrismPropertyMessage.newBuilder()
                                                                        .setMultiple(PrismPropertyValueMessageList.newBuilder()
                                                                                .addValues(
                                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                                .setString("test1-multiple11")
                                                                                )
                                                                                .addValues(
                                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                                .setString("test1-multiple22")
                                                                                )
                                                                        )
                                                        )
                                                        .build()
                                                )
                                                .putValue("singleNestedComplex", PrismValueMessage.newBuilder()
                                                        .setNamespaceURI("http://test.example.com/my")
                                                        .setContainer(PrismContainerValueMessage.newBuilder()
                                                                .putValue("singleString", PrismValueMessage.newBuilder()
                                                                        .setNamespaceURI("http://test.example.com/my")
                                                                        .setProperty(
                                                                                PrismPropertyMessage.newBuilder()
                                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
                                                                                                .setString("nested-test1-single")
                                                                                        )
                                                                        )
                                                                        .build()
                                                                )
                                                                .putValue("multipleString", PrismValueMessage.newBuilder()
                                                                        .setNamespaceURI("http://test.example.com/my")
                                                                        .setProperty(
                                                                                PrismPropertyMessage.newBuilder()
                                                                                        .setMultiple(PrismPropertyValueMessageList.newBuilder()
                                                                                                .addValues(
                                                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                                                .setString("nested-test1-multiple3")
                                                                                                )
                                                                                                .addValues(
                                                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                                                .setString("nested-test1-multiple4")
                                                                                                )
                                                                                        )
                                                                        )
                                                                        .build()
                                                                )
                                                        )
                                                        .build()
                                                )
                                        )
                                )
//                                .setPath("extension/singleComplex/singleNestedComplex")
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                                .setContainer(PrismContainerValueMessage.newBuilder()
//                                                        .putValue("singleString", PrismValueMessage.newBuilder()
//                                                                .setNamespaceURI("http://test.example.com/my")
//                                                                .setProperty(
//                                                                        PrismPropertyMessage.newBuilder()
//                                                                                .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                        .setString("wooo")
//                                                                                )
//                                                                )
//                                                                .build()
//                                                        )
//                                                        .putValue("multipleString", PrismValueMessage.newBuilder()
//                                                                .setNamespaceURI("http://test.example.com/my")
//                                                                .setProperty(
//                                                                        PrismPropertyMessage.newBuilder()
//                                                                                .setMultiple(PrismPropertyValueMessageList.newBuilder()
//                                                                                        .addValues(
//                                                                                                PrismPropertyValueMessage.newBuilder()
//                                                                                                        .setString("hayaya")
//                                                                                        )
//                                                                                )
//                                                                )
//                                                                .build()
//                                                        )
//                                                )
//                                )
//                                .setPath("extension/singleComplex")
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                        .setContainer(PrismContainerValueMessage.newBuilder()
//                                                .putValue("name", PrismValueMessage.newBuilder()
//                                                        .setNamespaceURI("http://test.example.com/my")
//                                                        .setProperty(
//                                                                PrismPropertyMessage.newBuilder()
//                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                .setString("mySingleComplex2")
//                                                                        )
//                                                        )
//                                                        .build()
//                                                )
//                                                .putValue("description", PrismValueMessage.newBuilder()
//                                                        .setNamespaceURI("http://test.example.com/my")
//                                                        .setProperty(
//                                                                PrismPropertyMessage.newBuilder()
//                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                .setString("descdesc")
//                                                                        )
//                                                        )
//                                                        .build()
//                                                )
//                                        )
//                                )
//                                .setPath("extension/singleComplex/description")
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                        .setProperty(PrismPropertyMessage.newBuilder()
//                                                .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                        .setPolyString(
//                                                                PolyStringMessage.newBuilder()
//                                                                        .setOrig("barbar")
//                                                        )
//                                                )
//                                        )
//                                )
//                                .addPrismValuesToAdd(PrismValueMessage.newBuilder()
//                                        .setProperty(PrismPropertyMessage.newBuilder()
//                                                .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                        .setPolyString(
//                                                                PolyStringMessage.newBuilder()
//                                                                        .setOrig("bar")
//                                                        )
//                                                )
//                                        )
//                                )
                )
                .build();

        ModifyProfileResponse response = stub.modifyProfile(request);

        System.out.println(response);

    }
}
