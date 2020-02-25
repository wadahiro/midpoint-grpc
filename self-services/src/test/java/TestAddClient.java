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
                                .setName(PolyStringMessage.newBuilder().setOrig("foo"))
                                .addAssignment(
                                        AssignmentMessage.newBuilder()
                                                .setTargetRef(
                                                        ReferenceMessage.newBuilder()
                                                                .setObjectType(DefaultObjectType.ROLE_TYPE)
                                                                .setName(PolyStringMessage.newBuilder().setOrig("ProjUser"))
//                                                                .setRelationType(DefaultRelationType.ORG_MANAGER)
                                                )
                                                .putExtension("manager",
                                                        ExtensionMessage.newBuilder()
                                                                .setIsSingleValue(true)
                                                                .addValue(
                                                                        ExtensionValue.newBuilder().setRef(
                                                                                ReferenceMessage.newBuilder()
                                                                                        .setObjectType(DefaultObjectType.USER_TYPE)
//                                                                                        .setName(PolyStringMessage.newBuilder().setOrig("test"))
                                                                                        .setEmailAddress("TEST@example.com")
                                                                        ).build()
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
                                        ExtensionMessage.newBuilder()
                                                .setIsSingleValue(true)
                                                .addValue(
                                                        ExtensionValue.newBuilder().setString("ext1").build()
                                                )
                                                .build()
                                )
                )
                .build();

        AddUserResponse response = stub.addUser(request);

        System.out.println(response);

    }
}
