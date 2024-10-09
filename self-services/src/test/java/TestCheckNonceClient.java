import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestCheckNonceClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:Test5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.RunPrivilegedMetadataKey, "true");
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "abc");

        stub = MetadataUtils.attachHeaders(stub, headers);

        String nonce = "123456";

        CheckNonceRequest checkNonceRequest = CheckNonceRequest.newBuilder()
                .setNonce(nonce)
                .build();

        CheckNonceResponse checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        // Set nonce
        ModifyUserRequest modifyUserRequest = ModifyUserRequest.newBuilder()
                .setName("abc")
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("credentials/nonce/value")
                                .addValuesToReplace(nonce)
                )
                .build();

        ModifyUserResponse modifyUserResponse = stub.modifyUser(modifyUserRequest);

        System.out.println(modifyUserResponse);

        // Validate nonce again
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        // Set new password
        stub.forceUpdateCredential(ForceUpdateCredentialRequest.newBuilder()
                .setNew("password1")
                .build());

        // Set new password with clearing nonce and active
        stub.forceUpdateCredential(ForceUpdateCredentialRequest.newBuilder()
                .setNew("password2")
                .setClearNonce(true)
                .setActive(true)
                .build());
    }
}
