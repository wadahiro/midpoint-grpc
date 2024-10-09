import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestModifyProfileComplexClient {

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

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("extension/multipleComplex/[1]/name")
                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
                                        .setProperty(
                                                PrismPropertyValueMessage.newBuilder()
                                                        .setString("aiueo")
                                        )
                                )

//                                .setPath("extension/multipleComplex")
//                                .addPrismValuesToAdd(PrismValueMessage.newBuilder()
//                                                .setContainer(PrismContainerValueMessage.newBuilder()
//                                                                .putValue("name", PrismValueMessage.newBuilder()
//                                                                        .setNamespaceURI("http://test.example.com/my")
//                                                                        .setProperty(
//                                                                                PrismPropertyMessage.newBuilder()
//                                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                                .setString("hogee")
//                                                                                        )
//                                                                        )
//                                                                        .build()
//                                                                )

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
                        // Need when updating reference without oid
//                .addOptions("reevaluateSearchFilters")
//                .addModifications(
//                        UserItemDelta.newBuilder()
//                                .setPath("extension/singleRef")
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                                .setRef(ReferenceMessage.newBuilder()
//                                                                .setObjectType(DefaultObjectType.ORG_TYPE)
////                                                                .setOid("eaf1451f-465f-4b46-b0f8-74b925a4082b")
//                                                                .setName(PolyStringMessage.newBuilder()
//                                                                        .setOrig("Tenant1"))
//                                                )
//                                )
////                )
//                .addModifications(
//                        UserItemDelta.newBuilder()
//                                .setPath("familyName")
//                                .addPrismValuesToDelete(PrismValueMessage.newBuilder()
//                                        .setProperty(PrismPropertyMessage.newBuilder()
//                                                .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                        .setPolyString(
//                                                                PolyStringMessage.newBuilder()
//                                                                        .setOrig("foo")
//                                                        )
//                                                )
//                                        )
//                                )
//                )
//                .addModifications(
//                        UserItemDelta.newBuilder()
//                                .setPath("extension/multipleRef")
//                                .addPrismValuesToDelete(PrismValueMessage.newBuilder()
//                                                .setRef(ReferenceMessage.newBuilder()
//                                                                .setObjectType(DefaultObjectType.ORG_TYPE)
//                                                                .setOid("eaf1451f-465f-4b46-b0f8-74b925a4082b")
//                                                        // Can't delete reference by name
////                                                .setName(PolyStringMessage.newBuilder()
////                                                        .setOrig("Tenant1"))
//                                                )
//                                )
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                                .setRef(ReferenceMessage.newBuilder()
//                                                                .setObjectType(DefaultObjectType.ORG_TYPE)
////                                                .setOid("eaf1451f-465f-4b46-b0f8-74b925a4082b")
//                                                                .setName(PolyStringMessage.newBuilder()
//                                                                        .setOrig("Proj1"))
//                                                )
//                                )
//                                .setPath("familyName")
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                        .setProperty(PrismPropertyMessage.newBuilder()
//                                                .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                        .setPolyString(
//                                                                PolyStringMessage.newBuilder()
//                                                                        .setOrig("foobar")
//                                                        )
//                                                )
//                                        )
//                                )
//                                .setPath("extension/singleComplex")
//                                .addPrismValuesToReplace(PrismValueMessage.newBuilder()
//                                        .setContainer(PrismContainerValueMessage.newBuilder()
//                                                .putValue("name", PrismValueMessage.newBuilder()
//                                                        .setNamespaceURI("http://test.example.com/my")
//                                                        .setProperty(
//                                                                PrismPropertyMessage.newBuilder()
//                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                .setString("test1")
//                                                                        )
//                                                        )
//                                                        .build()
//                                                )
//                                                .putValue("description", PrismValueMessage.newBuilder()
//                                                        .setNamespaceURI("http://test.example.com/my")
//                                                        .setProperty(
//                                                                PrismPropertyMessage.newBuilder()
//                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                .setString("test1-single")
//                                                                        )
//                                                        )
//                                                        .build()
//                                                )
//                                                .putValue("multipleString", PrismValueMessage.newBuilder()
//                                                        .setNamespaceURI("http://test.example.com/my")
//                                                        .setProperty(
//                                                                PrismPropertyMessage.newBuilder()
//                                                                        .setMultiple(PrismPropertyValueMessageList.newBuilder()
//                                                                                .addValues(
//                                                                                        PrismPropertyValueMessage.newBuilder()
//                                                                                                .setString("test1-multiple11")
//                                                                                )
//                                                                                .addValues(
//                                                                                        PrismPropertyValueMessage.newBuilder()
//                                                                                                .setString("test1-multiple22")
//                                                                                )
//                                                                        )
//                                                        )
//                                                        .build()
//                                                )
//                                                .putValue("singleNestedComplex", PrismValueMessage.newBuilder()
//                                                        .setNamespaceURI("http://test.example.com/my")
//                                                        .setContainer(PrismContainerValueMessage.newBuilder()
//                                                                .putValue("singleString", PrismValueMessage.newBuilder()
//                                                                        .setNamespaceURI("http://test.example.com/my")
//                                                                        .setProperty(
//                                                                                PrismPropertyMessage.newBuilder()
//                                                                                        .setSingle(PrismPropertyValueMessage.newBuilder()
//                                                                                                .setString("nested-test1-single")
//                                                                                        )
//                                                                        )
//                                                                        .build()
//                                                                )
//                                                                .putValue("multipleString", PrismValueMessage.newBuilder()
//                                                                        .setNamespaceURI("http://test.example.com/my")
//                                                                        .setProperty(
//                                                                                PrismPropertyMessage.newBuilder()
//                                                                                        .setMultiple(PrismPropertyValueMessageList.newBuilder()
//                                                                                                .addValues(
//                                                                                                        PrismPropertyValueMessage.newBuilder()
//                                                                                                                .setString("nested-test1-multiple3")
//                                                                                                )
//                                                                                                .addValues(
//                                                                                                        PrismPropertyValueMessage.newBuilder()
//                                                                                                                .setString("nested-test1-multiple4")
//                                                                                                )
//                                                                                        )
//                                                                        )
//                                                                        .build()
//                                                                )
//                                                        )
//                                                        .build()
//                                                )
//                                        )
//                                )
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
