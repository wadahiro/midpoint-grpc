import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestModifyProfileClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:Test5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");
        headers.put(Constant.RunPrivilegedMetadataKey, "true");

        stub = MetadataUtils.attachHeaders(stub, headers);

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
//                                .setUserTypePath(DefaultUserTypePath.F_FAMILY_NAME)
                                .setPath("familyName")
                                .addValuesToAdd("hoge1")
                )
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("assignment")
                                .addPrismValuesToAdd(
                                        PrismValueMessage.newBuilder()
                                                .setContainer(
                                                        PrismContainerValueMessage.newBuilder()
                                                                .putValue("subtype",
                                                                        ItemMessage.newBuilder()
                                                                                .setProperty(
                                                                                        PrismPropertyMessage.newBuilder()
                                                                                                .addValues(
                                                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                                                .setString("client-certificate-binding")
                                                                                                )
                                                                                ).build()
                                                                )
                                                                .putValue("targetRef",
                                                                        ItemMessage.newBuilder()
                                                                                .setRef(
                                                                                        PrismReferenceMessage.newBuilder()
                                                                                                .addValues(
                                                                                                        ReferenceMessage.newBuilder()
                                                                                                                .setObjectType(DefaultObjectType.ROLE_TYPE)
                                                                                                                .setOid("fa14f888-b23e-4547-99a9-667b9095c9ca")
                                                                                                )

                                                                                ).build()
                                                                ))
                                )
                )
//                .addModifications(
//                        UserItemDeltaMessage.newBuilder()
////                                .setUserTypePath(DefaultUserTypePath.F_FAMILY_NAME)
//                                .setPath("extension/singleString")
////                                .setPath("familyName")
////                                .setItemPath(
////                                        ItemPathMessage.newBuilder()
////                                                .addPath(QNameMessage.newBuilder().setLocalPart("extension"))
////                                                .addPath(QNameMessage.newBuilder().setLocalPart("singleString"))
////                                )
//                                .addValuesToAdd("hoge2")
//                )
                .build();

        ModifyProfileResponse response = stub.modifyProfile(request);

        System.out.println(response);

    }
}
