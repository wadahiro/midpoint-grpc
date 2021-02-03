import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestModifyProfileProtectedStringClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");
        headers.put(Constant.RunPrivilegedMetadataKey, "true");

        stub = MetadataUtils.attachHeaders(stub, headers);

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("extension/singleProtectedString")
                                .addValuesToReplace("foo")
                )
//                .addModifications(
//                        UserItemDeltaMessage.newBuilder()
//                                .setPath("extension/multipleProtectedString")
//                                .addValuesToAdd("bar")
//                )
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("extension/multipleProtectedString")
                                .addPrismValuesToAdd(
                                        PrismValueMessage.newBuilder()
                                                .setProperty(PrismPropertyValueMessage.newBuilder()
                                                        .setString("bar")
                                                )
                                )
                )
                .build();

        ModifyProfileResponse response = stub.modifyProfile(request);

        System.out.println(response);

        GetUserRequest req = GetUserRequest.newBuilder()
                .setOid("4031e50f-97a5-43a9-b026-e96d9e80974a")
                .build();

        GetUserResponse self = stub.getUser(req);

        UserTypeMessage user = self.getResult();
        System.out.println(user);
    }
}
