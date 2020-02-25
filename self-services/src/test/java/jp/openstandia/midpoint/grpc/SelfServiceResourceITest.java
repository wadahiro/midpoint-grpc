package jp.openstandia.midpoint.grpc;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
    void getSelf() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        GetSelfResponse response = stub.getSelf(request);
        UserTypeMessage user  = response.getProfile();

        assertEquals("Administrator", user.getFamilyName().getOrig());
        assertEquals("administrator", user.getFamilyName().getNorm());
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
                                .setUserTypePath(DefaultUserTypePath.F_ADDITIONAL_NAME)
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

            PolicyError policyError = e.getTrailers().get(SelfServiceResource.PolicyErrorMetadataKey);

            Message error = policyError.getMessage();
            assertTrue(error.hasSingle());

            SingleMessage msg = error.getSingle();
            String errKey = msg.getKey();
            assertEquals("PolicyViolationException.message.credentials.password", errKey);

            List<Message> argsList = msg.getArgsList();
            assertEquals(1, argsList.size());

            Message argsWrapper = argsList.get(0);
            assertTrue(argsWrapper.hasSingle());

            SingleMessage subMsgArgs = argsWrapper.getSingle();
            String subErrKey = subMsgArgs.getKey();
            assertEquals("ValuePolicy.minimalSizeNotMet", subErrKey);

            List<Message> subMsgArgsList = subMsgArgs.getArgsList();
            assertEquals(2, subMsgArgsList.size());

            Message subMsgArg1 = subMsgArgsList.get(0);
            assertFalse(subMsgArg1.hasSingle());
            assertFalse(subMsgArg1.hasList());
            assertEquals("5", subMsgArg1.getString());

            Message subMsgArg2= subMsgArgsList.get(1);
            assertFalse(subMsgArg2.hasSingle());
            assertFalse(subMsgArg2.hasList());
            assertEquals("3", subMsgArg2.getString());
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

            PolicyError policyError = e.getTrailers().get(SelfServiceResource.PolicyErrorMetadataKey);

            Message message = policyError.getMessage();
            assertTrue(message.hasSingle());

            SingleMessage msg = message.getSingle();
            String errKey = msg.getKey();
            assertEquals("PolicyViolationException.message.credentials.password", errKey);

            List<Message> argsList = msg.getArgsList();
            assertEquals(1, argsList.size());

            Message argsWrapper = argsList.get(0);
            assertFalse(argsWrapper.hasSingle(), "Should have multiple errors");
            assertTrue(argsWrapper.hasList(), "Should have multiple errors");

            MessageList msgListArg = argsWrapper.getList();
            List<Message> subMsgList = msgListArg.getMessageList();
            assertEquals(2, subMsgList.size(), "Should have multiple errors");

            Message argsWrapper1 = subMsgList.get(0);
            assertTrue(argsWrapper1.hasSingle());

            SingleMessage subMsg1Args = argsWrapper1.getSingle();
            String subErr1Key = subMsg1Args.getKey();
            assertEquals("ValuePolicy.minimalSizeNotMet", subErr1Key);

            Message argsWrapper2 = subMsgList.get(1);
            assertTrue(argsWrapper2.hasSingle());

            SingleMessage subMsg2Args = argsWrapper2.getSingle();
            String subErr2Key = subMsg2Args.getKey();
            assertEquals("ValuePolicy.minimalUniqueCharactersNotMet", subErr2Key);
        }
    }

    @Test
    void requestRole() {
    }

    @Test
    void addUser() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        AddUserRequest request = AddUserRequest.newBuilder()
                .setProfile(UserTypeMessage.newBuilder()
                .setName(PolyStringMessage.newBuilder().setOrig("foo")))
                .build();

        AddUserResponse response = stub.addUser(request);

        assertNotNull(response.getOid());
    }
}