package jp.openstandia.midpoint.grpc;

import com.google.protobuf.Descriptors;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MidPointGrpcTestRunner.class)
class SelfServiceResourceITest {
    static ManagedChannel channel;

    @BeforeAll
    static void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();
    }

    @AfterAll
    static void cleanup() {
        channel.shutdownNow();
    }

    @Test
    void modifyProfile() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
//        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");

        stub = MetadataUtils.attachHeaders(stub, headers);

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDelta.newBuilder()
                                .setName(UserItemPath.F_FAMILY_NAME)
                                .setValuesToReplace("Foo")
                                .build()
                )
                .build();

        stub.modifyProfile(request);
    }

    @Test
    void updateCredential() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        UpdateCredentialRequest request = UpdateCredentialRequest.newBuilder()
                .setOld("5ecr3t")
                .setNew("password")
                .build();

        stub.updateCredential(request);

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:password".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Back to original password by forceUpdateCredential
        ForceUpdateCredentialRequest forceReq = ForceUpdateCredentialRequest.newBuilder()
                .setNew("5ecr3t")
                .build();

        stub.forceUpdateCredential(forceReq);
    }

    @Test
    void passwordPolicyErrorWithSingleError() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        UpdateCredentialRequest request = UpdateCredentialRequest.newBuilder()
                .setOld("5ecr3t")
                .setNew("123")
                .build();

        try {
            stub.updateCredential(request);
            fail("Should be thrown Exception of password policy error");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());

            Metadata.Key<PolicyError> POLICY_ERROR =
                    ProtoUtils.keyForProto(PolicyError.getDefaultInstance());

            PolicyError policyError = e.getTrailers().get(POLICY_ERROR);

            List<MessageWrapper> errorsList = policyError.getErrorsList();
            assertEquals(1, errorsList.size());

            MessageWrapper wrapper = errorsList.get(0);
            assertTrue(wrapper.hasMsgArg());

            Message msg = wrapper.getMsgArg();
            String errKey = msg.getKey();
            assertEquals("PolicyViolationException.message.credentials.password", errKey);

            List<MessageWrapper> argsList = msg.getArgsList();
            assertEquals(1, argsList.size());

            MessageWrapper argsWrapper = argsList.get(0);
            assertTrue(argsWrapper.hasMsgArg());

            Message subMsgArgs = argsWrapper.getMsgArg();
            String subErrKey = subMsgArgs.getKey();
            assertEquals("ValuePolicy.minimalSizeNotMet", subErrKey);

            List<MessageWrapper> subMsgArgsList = subMsgArgs.getArgsList();
            assertEquals(2, subMsgArgsList.size());

            MessageWrapper subMsgArg1 = subMsgArgsList.get(0);
            assertFalse(subMsgArg1.hasMsgArg());
            assertFalse(subMsgArg1.hasMsgListArg());
            assertEquals("5", subMsgArg1.getStringArg());

            MessageWrapper subMsgArg2= subMsgArgsList.get(1);
            assertFalse(subMsgArg2.hasMsgArg());
            assertFalse(subMsgArg2.hasMsgListArg());
            assertEquals("3", subMsgArg2.getStringArg());
        }
    }

    @Test
    void passwordPolicyErrorWithMultipleError() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        UpdateCredentialRequest request = UpdateCredentialRequest.newBuilder()
                .setOld("5ecr3t")
                .setNew("1111")
                .build();

        try {
            stub.updateCredential(request);
            fail("Should be thrown Exception of password policy error");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());

            Metadata.Key<PolicyError> POLICY_ERROR =
                    ProtoUtils.keyForProto(PolicyError.getDefaultInstance());

            PolicyError policyError = e.getTrailers().get(POLICY_ERROR);

            List<MessageWrapper> errorsList = policyError.getErrorsList();
            assertEquals(1, errorsList.size());

            MessageWrapper wrapper = errorsList.get(0);
            assertTrue(wrapper.hasMsgArg());

            Message msg = wrapper.getMsgArg();
            String errKey = msg.getKey();
            assertEquals("PolicyViolationException.message.credentials.password", errKey);

            List<MessageWrapper> argsList = msg.getArgsList();
            assertEquals(1, argsList.size());

            MessageWrapper argsWrapper = argsList.get(0);
            assertFalse(argsWrapper.hasMsgArg(), "Should have multiple errors");
            assertTrue(argsWrapper.hasMsgListArg(), "Should have multiple errors");

            MessageList msgListArg = argsWrapper.getMsgListArg();
            List<MessageWrapper> subMsgList = msgListArg.getArgsList();
            assertEquals(2, subMsgList.size(), "Should have multiple errors");

            MessageWrapper argsWrapper1 = subMsgList.get(0);

            Message subMsg1Args = argsWrapper1.getMsgArg();
            String subErr1Key = subMsg1Args.getKey();
            assertEquals("ValuePolicy.minimalSizeNotMet", subErr1Key);

            MessageWrapper argsWrapper2 = subMsgList.get(1);

            Message subMsg2Args = argsWrapper2.getMsgArg();
            String subErr2Key = subMsg2Args.getKey();
            assertEquals("ValuePolicy.minimalUniqueCharactersNotMet", subErr2Key);
        }
    }

    @Test
    void requestRole() {
    }
}