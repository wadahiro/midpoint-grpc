import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestAddClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        AddUserRequest request = AddUserRequest.newBuilder()
                .setProfile(
                        UserTypeMessage.newBuilder()
                                .setName(PolyStringMessage.newBuilder().setOrig("foo9"))
                                .addOrganization(PolyStringMessage.newBuilder().setOrig("org1"))
                                .addOrganization(PolyStringMessage.newBuilder().setOrig("org2"))
                                .addAssignment(
                                        AssignmentMessage.newBuilder()
                                                .setTargetRef(
                                                        ReferenceMessage.newBuilder()
                                                                .setObjectType(DefaultObjectType.ROLE_TYPE)
                                                                .setName(PolyStringMessage.newBuilder().setOrig("ProjGuest"))
                                                )
                                                .putExtension("manager",
                                                        ItemMessage.newBuilder()
                                                                .setRef(
                                                                        PrismReferenceMessage.newBuilder()
                                                                                .addValues(
                                                                                        ReferenceMessage.newBuilder()
                                                                                                .setObjectType(DefaultObjectType.USER_TYPE)
                                                                                                .setEmailAddress("TEST@example.com")
                                                                                )

                                                                )
                                                                .build()
                                                )
                                )
                                .addAssignment(
                                        AssignmentMessage.newBuilder()
                                                .setTargetRef(
                                                        ReferenceMessage.newBuilder()
                                                                .setObjectType(DefaultObjectType.ROLE_TYPE)
                                                                .setOid("00000000-0000-0000-0000-00000000000c")
                                                )
                                )
                                .putExtension("singleString",
                                        ItemMessage.newBuilder()
                                                .setProperty(
                                                        PrismPropertyMessage.newBuilder()
                                                                .addValues(
                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                .setString("ext1")
                                                                )
                                                )
                                                .build()
                                )
                                .putExtension("multipleString",
                                        ItemMessage.newBuilder()
                                                .setProperty(
                                                        PrismPropertyMessage.newBuilder()
                                                                .addValues(
                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                .setString("ext1")
                                                                )
                                                                .addValues(
                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                .setString("ext2")
                                                                )
                                                )
                                                .build()
                                )
                                .putExtension("singleComplex",
                                        ItemMessage.newBuilder()
                                                .setContainer(
                                                        PrismContainerMessage.newBuilder()
                                                                .addValues(
                                                                        PrismContainerValueMessage.newBuilder()
                                                                                .putValue("name",
                                                                                        ItemMessage.newBuilder()
                                                                                                .setProperty(
                                                                                                        PrismPropertyMessage.newBuilder()
                                                                                                                .addValues(
                                                                                                                        PrismPropertyValueMessage.newBuilder()
                                                                                                                                .setString("singleComplexName")
                                                                                                                )
                                                                                                )
                                                                                                .build()
                                                                                )
                                                                )
                                                )
                                                .build()
                                )
                )
                .build();


        AddUserResponse response = stub.addUser(request);

        System.out.println(response);

    }
}
