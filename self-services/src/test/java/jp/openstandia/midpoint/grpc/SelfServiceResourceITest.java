package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.schema.constants.SchemaConstants;
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
    void unauthenticated() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:invalid".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        try {
            GetSelfResponse response = stub.getSelf(request);
            fail("No unauthenticated error");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
            assertEquals("invalid_token", e.getStatus().getDescription());
        }
    }

    @Test
    void switchUserWithDuplicateName() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "Administrator");

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add org with same user's name
        AddOrgRequest addRequest = AddOrgRequest.newBuilder()
                .setObject(OrgTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("Administrator"))
                )
                .build();

        AddObjectResponse addResponse = stub.addOrg(addRequest);
        assertNotNull(addResponse.getOid());

        // Test authentication again
        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        GetSelfResponse response = stub.getSelf(request);
        UserTypeMessage user = response.getProfile();

        assertEquals("Administrator", user.getFamilyName().getOrig());
        assertEquals("administrator", user.getFamilyName().getNorm());
    }

    @Test
    void switchUser() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "Administrator");

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        GetSelfResponse response = stub.getSelf(request);
        UserTypeMessage user = response.getProfile();

        assertEquals("Administrator", user.getFamilyName().getOrig());
        assertEquals("administrator", user.getFamilyName().getNorm());
    }

    @Test
    void switchUserWithNotFoundUser() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "foo");

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        try {
            GetSelfResponse response = stub.getSelf(request);
            fail("No unauthenticated error");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
            assertEquals("Not found user", e.getStatus().getDescription());
        }
    }

    @Test
    void switchUserWithRunPrivileged() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "Administrator");
        headers.put(Constant.RunPrivilegedMetadataKey, "true");

        stub = MetadataUtils.attachHeaders(stub, headers);

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        GetSelfResponse response = stub.getSelf(request);
        UserTypeMessage user = response.getProfile();

        assertEquals("Administrator", user.getFamilyName().getOrig());
        assertEquals("administrator", user.getFamilyName().getNorm());
    }

    @Test
    void switchUserWithUnAuthorizedUser() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add a user for switch
        AddUserRequest addRequest = AddUserRequest.newBuilder()
                .setProfile(UserTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("switch001"))
                )
                .build();

        AddUserResponse addResponse = stub.addUser(addRequest);
        assertNotNull(addResponse.getOid());

        // Test switch user
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "switch001");

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        try {
            GetSelfResponse response = stub.getSelf(request);
            fail("No unauthenticated error");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
            assertEquals("Not authorized", e.getStatus().getDescription());
        }

        // Test switch user with run privileged
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "switch001");
        headers.put(Constant.RunPrivilegedMetadataKey, "true");

        request = GetSelfRequest.newBuilder()
                .build();

        GetSelfResponse response = stub.getSelf(request);
        UserTypeMessage user = response.getProfile();

        assertEquals("switch001", user.getName().getOrig());
    }

    @Test
    void switchUserWithArchivedUser() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add a user for switch
        AddUserRequest addRequest = AddUserRequest.newBuilder()
                .setProfile(UserTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("switch002"))
                        .setLifecycleState("archived")
                )
                .build();

        AddUserResponse addResponse = stub.addUser(addRequest);
        assertNotNull(addResponse.getOid());

        // Test switch user
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "switch002");
        headers.put(Constant.RunPrivilegedMetadataKey, "true");

        GetSelfRequest request = GetSelfRequest.newBuilder()
                .build();

        // Currently, the authentication isn't rejected even if the switch user is archived
        GetSelfResponse response = stub.getSelf(request);
        UserTypeMessage user = response.getProfile();

        assertEquals("switch002", user.getName().getOrig());
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
        UserTypeMessage user = response.getProfile();

        assertEquals("Administrator", user.getFamilyName().getOrig());
        assertEquals("administrator", user.getFamilyName().getNorm());
    }

    @Test
    void modifyProfile() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setUserTypePath(DefaultUserTypePath.F_ADDITIONAL_NAME)
                                .addValuesToReplace("Foo")
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

            Message subMsgArg2 = subMsgArgsList.get(1);
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
    void forceUpdateCredential() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Force update password
        ForceUpdateCredentialRequest request = ForceUpdateCredentialRequest.newBuilder()
                .setNew("password")
                .build();

        stub.forceUpdateCredential(request);

        // Check the password was changed
        try {
            stub.getSelf(GetSelfRequest.newBuilder().build());
            fail("Password wasn't changed");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
        }

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:password".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Back to original password by forceUpdateCredential
        ForceUpdateCredentialRequest forceReq = ForceUpdateCredentialRequest.newBuilder()
                .setNew("5ecr3t")
                .build();

        stub.forceUpdateCredential(forceReq);

        // Check the password was changed
        try {
            stub.getSelf(GetSelfRequest.newBuilder().build());
            fail("Password wasn't changed");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
        }

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Check auth with new password
        stub.getSelf(GetSelfRequest.newBuilder().build());
    }

    @Test
    void forceUpdateCredentialWithNonceClear() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Save nonce to the user
        ModifyProfileRequest modifyProfileRequest = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("credentials/nonce/value")
                                .addValuesToReplace("123456")
                                .build()
                )
                .build();

        stub.modifyProfile(modifyProfileRequest);

        // Force update password
        ForceUpdateCredentialRequest request = ForceUpdateCredentialRequest.newBuilder()
                .setNew("password")
                .build();

        stub.forceUpdateCredential(request);

        // Check the password was changed
        try {
            stub.getSelf(GetSelfRequest.newBuilder().build());
            fail("Password wasn't changed");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
        }

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:password".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Check nonce isn't cleared yet
        CheckNonceRequest checkNonceRequest = CheckNonceRequest.newBuilder()
                .setNonce("123456")
                .build();

        CheckNonceResponse checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertTrue(checkNonceResponse.getValid());
        assertTrue(checkNonceResponse.getError().isEmpty());

        // Back to original password with clear nonce and active
        ForceUpdateCredentialRequest forceReq = ForceUpdateCredentialRequest.newBuilder()
                .setNew("5ecr3t")
                .setClearNonce(true)
                .build();

        stub.forceUpdateCredential(forceReq);

        // Check the password was changed
        try {
            stub.getSelf(GetSelfRequest.newBuilder().build());
            fail("Password wasn't changed");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
        }

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Check nonce was cleared
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertFalse(checkNonceResponse.getValid());
        assertEquals("not_found", checkNonceResponse.getError());
    }

    @Test
    void forceUpdateCredentialWithNonceClearForNoCredentialsUser() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add
        AddUserRequest addUserRequest = AddUserRequest.newBuilder()
                .setProfile(UserTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("user001"))
                )
                .build();

        AddUserResponse response = stub.addUser(addUserRequest);

        assertNotNull(response.getOid());

        // Switch to the created user
        headers.put(Constant.SwitchToPrincipalMetadataKey, response.getOid());
        headers.put(Constant.RunPrivilegedMetadataKey, "true");
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Force update password with nonce clear
        ForceUpdateCredentialRequest request = ForceUpdateCredentialRequest.newBuilder()
                .setNew("password")
                .setClearNonce(true)
                .build();

        stub.forceUpdateCredential(request);

        // Delete
        stub.deleteObject(DeleteObjectRequest.newBuilder()
                .setOid(response.getOid())
                .setObjectType(DefaultObjectType.USER_TYPE)
                .build());
    }

    @Test
    void forceUpdateCredentialWithNonceClearAndActive() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Save nonce to the user
        ModifyProfileRequest modifyProfileRequest = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("credentials/nonce/value")
                                .addValuesToReplace("123456")
                                .build()
                )
                .build();

        stub.modifyProfile(modifyProfileRequest);

        // Force update password
        ForceUpdateCredentialRequest request = ForceUpdateCredentialRequest.newBuilder()
                .setNew("password")
                .build();

        stub.forceUpdateCredential(request);

        // Check the password was changed
        try {
            stub.getSelf(GetSelfRequest.newBuilder().build());
            fail("Password wasn't changed");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
        }

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:password".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Check nonce isn't cleared yet
        CheckNonceRequest checkNonceRequest = CheckNonceRequest.newBuilder()
                .setNonce("123456")
                .build();

        CheckNonceResponse checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertTrue(checkNonceResponse.getValid());
        assertTrue(checkNonceResponse.getError().isEmpty());

        // Check lifecycleState isn't active yet
        assertEquals("", stub.getSelf(GetSelfRequest.newBuilder().build()).getProfile().getLifecycleState());

        // Back to original password with clear nonce and active
        ForceUpdateCredentialRequest forceReq = ForceUpdateCredentialRequest.newBuilder()
                .setNew("5ecr3t")
                .setClearNonce(true)
                .setActive(true)
                .build();

        stub.forceUpdateCredential(forceReq);

        // Check the password was changed
        try {
            stub.getSelf(GetSelfRequest.newBuilder().build());
            fail("Password wasn't changed");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.UNAUTHENTICATED.getCode(), e.getStatus().getCode());
        }

        // Update authorization header with new password
        token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Check nonce was cleared
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertFalse(checkNonceResponse.getValid());
        assertEquals("not_found", checkNonceResponse.getError());

        // Check lifecycleState was active
        assertEquals(SchemaConstants.LIFECYCLE_ACTIVE, stub.getSelf(GetSelfRequest.newBuilder().build()).getProfile().getLifecycleState());

        // Clear lifecycleState
        ModifyProfileRequest profileRequest = ModifyProfileRequest.newBuilder()
                .addModifications(UserItemDeltaMessage.newBuilder()
                        .setUserTypePath(DefaultUserTypePath.F_LIFECYCLE_STATE)
                        .addValuesToDelete(SchemaConstants.LIFECYCLE_ACTIVE))
                .build();
        stub.modifyProfile(profileRequest);
    }

    @Test
    void generateNonce() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        GenerateValueRequest request = GenerateValueRequest.newBuilder()
                .setValuePolicyOid("00000000-0000-0000-0000-000000000003")
                .build();

        GenerateValueResponse response = stub.generateValue(request);

        System.out.println("Generated nonce: " + response.getValue());

        assertNotNull(response.getValue());
        assertTrue(response.getValue().length() > 0);
    }

    @Test
    void checkNonce() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add
        AddUserRequest request = AddUserRequest.newBuilder()
                .setProfile(UserTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("user001"))
                        .setEmployeeNumber("emp001")
                        .setLifecycleState("proposed")
                )
                .build();

        AddUserResponse response = stub.addUser(request);

        assertNotNull(response.getOid());

        // Switch to the created user
        headers.put(Constant.SwitchToPrincipalMetadataKey, response.getOid());
        headers.put(Constant.RunPrivilegedMetadataKey, "true");
        stub = MetadataUtils.attachHeaders(stub, headers);

        // Check nonce when the user doesn't have
        CheckNonceRequest checkNonceRequest = CheckNonceRequest.newBuilder()
                .setNonce("123456")
                .build();

        CheckNonceResponse checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertFalse(checkNonceResponse.getValid());
        assertEquals("not_found", checkNonceResponse.getError());

        // Save nonce to the user
        ModifyProfileRequest modifyProfileRequest = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setPath("credentials/nonce/value")
                                .addValuesToReplace("123456")
                                .build()
                )
                .build();

        stub.modifyProfile(modifyProfileRequest);

        // Check nonce again
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertTrue(checkNonceResponse.getValid());
        assertTrue(checkNonceResponse.getError().isEmpty());

        // Check nonce with invalid value
        CheckNonceRequest invalidCheckNonceRequest = CheckNonceRequest.newBuilder()
                .setNonce("invalid")
                .build();
        checkNonceResponse = stub.checkNonce(invalidCheckNonceRequest);

        System.out.println(checkNonceResponse);

        assertFalse(checkNonceResponse.getValid());
        assertEquals("invalid", checkNonceResponse.getError());

        // Update credential
        ForceUpdateCredentialRequest forceUpdateCredentialRequest = ForceUpdateCredentialRequest.newBuilder()
                .setNew("5ecr3t")
                .build();

        stub.forceUpdateCredential(forceUpdateCredentialRequest);

        // Check nonce isn't cleared yet
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertTrue(checkNonceResponse.getValid());
        assertTrue(checkNonceResponse.getError().isEmpty());

        // Update credential again
        UpdateCredentialRequest updateCredentialRequest = UpdateCredentialRequest.newBuilder()
                .setOld("5ecr3t")
                .setNew("P@ssw0rd")
                .build();

        stub.updateCredential(updateCredentialRequest);

        // Check nonce isn't cleared yet
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertTrue(checkNonceResponse.getValid());
        assertTrue(checkNonceResponse.getError().isEmpty());

        // Check lifecycleState isn't active yet
        assertEquals(SchemaConstants.LIFECYCLE_PROPOSED, stub.getSelf(GetSelfRequest.newBuilder().build()).getProfile().getLifecycleState());

        // Update credential again with nonce clearing
        ForceUpdateCredentialRequest forceUpdateCredentialRequestWithClearNonce = ForceUpdateCredentialRequest.newBuilder()
                .setNew("5ecr3t")
                .setClearNonce(true)
                .setActive(true)
                .build();

        stub.forceUpdateCredential(forceUpdateCredentialRequestWithClearNonce);

        // Check nonce was cleared
        checkNonceResponse = stub.checkNonce(checkNonceRequest);

        System.out.println(checkNonceResponse);

        assertFalse(checkNonceResponse.getValid());
        assertEquals("not_found", checkNonceResponse.getError());

        // Check lifecycleState was active
        assertEquals(SchemaConstants.LIFECYCLE_ACTIVE, stub.getSelf(GetSelfRequest.newBuilder().build()).getProfile().getLifecycleState());

        // Delete
        stub.deleteObject(DeleteObjectRequest.newBuilder()
                .setOid(response.getOid())
                .setObjectType(DefaultObjectType.USER_TYPE)
                .build());
    }

    @Test
    void requestRole() {
    }

    @Test
    void user() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add
        AddUserRequest request = AddUserRequest.newBuilder()
                .setProfile(UserTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("user001"))
                        .setEmployeeNumber("emp001")
                )
                .build();

        AddUserResponse response = stub.addUser(request);

        assertNotNull(response.getOid());

        // Get
        GetUserRequest req2 = GetUserRequest.newBuilder()
                .setOid(response.getOid())
                .build();

        GetUserResponse res2 = stub.getUser(req2);

        assertEquals("user001", res2.getResult().getName().getOrig());
        assertEquals("emp001", res2.getResult().getEmployeeNumber());

        // Search
        SearchUsersResponse res3 = stub.searchUsers(SearchRequest.newBuilder()
                .setQuery(QueryMessage.newBuilder()
                        .setFilter(ObjectFilterMessage.newBuilder()
                                .setEq(FilterEntryMessage.newBuilder()
                                        .setFullPath("employeeNumber")
                                        .setValue("emp001"))))
                .build());

        assertEquals(1, res3.getNumberOfAllResults());
        assertEquals("emp001", res3.getResults(0).getEmployeeNumber());

        // Delete
        DeleteObjectResponse res4 = stub.deleteObject(DeleteObjectRequest.newBuilder()
                .setOid(response.getOid())
                .setObjectType(DefaultObjectType.USER_TYPE)
                .build());

        assertNotNull(res4);
    }

    @Test
    void role() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add
        AddRoleRequest request = AddRoleRequest.newBuilder()
                .setObject(RoleTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("role001"))
                        .addSubtype("testRole")
                )
                .build();

        AddObjectResponse response = stub.addRole(request);

        assertNotNull(response.getOid());

        // Get
        GetRoleRequest req2 = GetRoleRequest.newBuilder()
                .setOid(response.getOid())
                .build();

        GetRoleResponse res2 = stub.getRole(req2);

        assertEquals("role001", res2.getResult().getName().getOrig());
        assertEquals("testRole", res2.getResult().getSubtype(0));

        // Search
        SearchRolesResponse res3 = stub.searchRoles(SearchRequest.newBuilder()
                .setQuery(QueryMessage.newBuilder()
                        .setFilter(ObjectFilterMessage.newBuilder()
                                .setEq(FilterEntryMessage.newBuilder()
                                        .setFullPath("subtype")
                                        .setValue("testRole"))))
                .build());

        assertEquals(1, res3.getNumberOfAllResults());
        assertEquals("testRole", res3.getResults(0).getSubtype(0));

        // Delete
        DeleteObjectResponse res4 = stub.deleteObject(DeleteObjectRequest.newBuilder()
                .setOid(response.getOid())
                .setObjectType(DefaultObjectType.ROLE_TYPE)
                .build());

        assertNotNull(res4);
    }

    @Test
    void org() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add
        AddOrgRequest request = AddOrgRequest.newBuilder()
                .setObject(OrgTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("org001"))
                        .addSubtype("testOrg")
                        .setDisplayOrder(1)
                )
                .build();

        AddObjectResponse response = stub.addOrg(request);

        assertNotNull(response.getOid());

        // Get
        GetOrgRequest req2 = GetOrgRequest.newBuilder()
                .setOid(response.getOid())
                .build();

        GetOrgResponse res2 = stub.getOrg(req2);

        assertEquals("org001", res2.getResult().getName().getOrig());
        assertEquals(1, res2.getResult().getDisplayOrder());

        // Search
        SearchOrgsResponse res3 = stub.searchOrgs(SearchRequest.newBuilder()
                .setQuery(QueryMessage.newBuilder()
                        .setFilter(ObjectFilterMessage.newBuilder()
                                .setEq(FilterEntryMessage.newBuilder()
                                        .setFullPath("subtype")
                                        .setValue("testOrg"))))
                .build());

        assertEquals(1, res3.getNumberOfAllResults());
        assertEquals(1, res3.getResults(0).getDisplayOrder());

        // Delete
        DeleteObjectResponse res4 = stub.deleteObject(DeleteObjectRequest.newBuilder()
                .setOid(response.getOid())
                .setObjectType(DefaultObjectType.ORG_TYPE)
                .build());

        assertNotNull(res4);
    }

    @Test
    void service() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Add
        AddServiceRequest request = AddServiceRequest.newBuilder()
                .setObject(ServiceTypeMessage.newBuilder()
                        .setName(PolyStringMessage.newBuilder().setOrig("service001"))
                        .addSubtype("testService")
                        .setUrl("https://example.com")
                )
                .build();

        AddObjectResponse response = stub.addService(request);

        assertNotNull(response.getOid());

        // Get
        GetServiceRequest req2 = GetServiceRequest.newBuilder()
                .setOid(response.getOid())
                .build();

        GetServiceResponse res2 = stub.getService(req2);

        assertEquals("service001", res2.getResult().getName().getOrig());
        assertEquals("https://example.com", res2.getResult().getUrl());

        // Search
        SearchServicesResponse res3 = stub.searchServices(SearchRequest.newBuilder()
                .setQuery(QueryMessage.newBuilder()
                        .setFilter(ObjectFilterMessage.newBuilder()
                                .setEq(FilterEntryMessage.newBuilder()
                                        .setFullPath("subtype")
                                        .setValue("testService"))))
                .build());

        assertEquals(1, res3.getNumberOfAllResults());
        assertEquals("https://example.com", res3.getResults(0).getUrl());

        // Delete
        DeleteObjectResponse res4 = stub.deleteObject(DeleteObjectRequest.newBuilder()
                .setOid(response.getOid())
                .setObjectType(DefaultObjectType.SERVICE_TYPE)
                .build());

        assertNotNull(res4);
    }

    @Test
    void lookupTable() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // Get with default options
        GetLookupTableRequest req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .build();

        GetLookupTableResponse res = stub.getLookupTable(req);

        assertEquals("00000000-0000-0000-0000-000000000200", res.getResult().getOid());
        assertEquals("1", res.getResult().getVersion());
        assertEquals("Languages", res.getResult().getName().getOrig());
        assertEquals("\n        Lookup table for languages directly supported by midPoint.\n        This lookup table contains language codes that are supported by\n        midPoint product localizations. The idea is that only the list of\n        supported languages will be presented to midPoint user.\n        For full list of language code please see lookup-languages-all.xml\n        in midPoint samples.\n    ",
                res.getResult().getDescription());
        assertEquals(0, res.getResult().getRowCount());

        // Get with all rows
        req = GetLookupTableRequest.newBuilder()
                .setName("Timezones")
                .addInclude("row")
                .build();

        res = stub.getLookupTable(req);

        assertEquals("00000000-0000-0000-0000-000000000220", res.getResult().getOid());
        assertEquals("1", res.getResult().getVersion());
        assertEquals("Timezones", res.getResult().getName().getOrig());
        assertEquals("", res.getResult().getDescription());
        assertNotNull(res.getResult().getRowList());
        List<LookupTableRowMessage> rows = res.getResult().getRowList();
        assertEquals(419, rows.size());

        assertEquals("Africa/Abidjan", rows.get(0).getKey());
        assertEquals("Africa/Abidjan", rows.get(0).getLabel().getOrig());
        assertEquals("117", rows.get(0).getValue());
        assertEquals(118, rows.get(0).getId());
        assertTrue(rows.get(0).hasLastChangeTimestamp());

        // with query by key
        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .setRelationalValueSearchQuery(RelationalValueSearchQueryMessage.newBuilder()
                        .setColumn(QNameMessage.newBuilder()
                                .setLocalPart("key"))
                        .setSearchType(RelationalValueSearch.EXACT)
                        .setSearchValue("en")
                )
                .build();

        res = stub.getLookupTable(req);

        assertEquals("00000000-0000-0000-0000-000000000200", res.getResult().getOid());
        assertEquals("1", res.getResult().getVersion());
        assertEquals("Languages", res.getResult().getName().getOrig());
        assertEquals("", res.getResult().getDescription());
        rows = res.getResult().getRowList();
        assertEquals(1, rows.size());

        assertEquals("en", rows.get(0).getKey());
        assertEquals("English", rows.get(0).getLabel().getOrig());
        assertEquals("", rows.get(0).getValue());
        assertEquals(4, rows.get(0).getId());
        assertTrue(rows.get(0).hasLastChangeTimestamp());

        // with query and paging
        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .addInclude("description")
                .setRelationalValueSearchQuery(RelationalValueSearchQueryMessage.newBuilder()
                        .setPaging(PagingMessage.newBuilder()
                                .addOrdering(ObjectOrderingMessage.newBuilder()
                                        .setOrderBy("key")
                                        .setOrderDirection(OrderDirectionType.ASCENDING)
                                )
                                .setOffset(0)
                                .setMaxSize(2)
                        )
                )
                .build();

        res = stub.getLookupTable(req);

        assertEquals("00000000-0000-0000-0000-000000000200", res.getResult().getOid());
        assertEquals("1", res.getResult().getVersion());
        assertEquals("Languages", res.getResult().getName().getOrig());
        assertEquals("\n        Lookup table for languages directly supported by midPoint.\n        This lookup table contains language codes that are supported by\n        midPoint product localizations. The idea is that only the list of\n        supported languages will be presented to midPoint user.\n        For full list of language code please see lookup-languages-all.xml\n        in midPoint samples.\n    ",
                res.getResult().getDescription());
        rows = res.getResult().getRowList();
        assertEquals(2, rows.size());

        assertEquals("cs", rows.get(0).getKey());
        assertEquals("Čeština", rows.get(0).getLabel().getOrig());
        assertEquals("", rows.get(0).getValue());
        assertEquals(1, rows.get(0).getId());
        assertTrue(rows.get(0).hasLastChangeTimestamp());

        assertEquals("de", rows.get(1).getKey());
        assertEquals("Deutsch", rows.get(1).getLabel().getOrig());
        assertEquals("", rows.get(1).getValue());
        assertEquals(2, rows.get(1).getId());
        assertTrue(rows.get(1).hasLastChangeTimestamp());

        // add new row
        ModifyObjectRequest addRowReq = ModifyObjectRequest.newBuilder()
                .setOid("00000000-0000-0000-0000-000000000200")
                .setObjectType(DefaultObjectType.LOOKUP_TABLE_TYPE)
                .addModifications(ItemDeltaMessage.newBuilder()
                        .setPath("row")
                        .addPrismValuesToAdd(PrismValueMessage.newBuilder()
                                .setContainer(PrismContainerValueMessage.newBuilder()
                                        .putValue("key", ItemMessage.newBuilder()
                                                .setProperty(PrismPropertyMessage.newBuilder()
                                                        .addValues(PrismPropertyValueMessage.newBuilder()
                                                                .setString("newKey")
                                                        )
                                                )
                                                .build()
                                        )
                                        .putValue("label", ItemMessage.newBuilder()
                                                .setProperty(PrismPropertyMessage.newBuilder()
                                                        .addValues(PrismPropertyValueMessage.newBuilder()
                                                                .setPolyString(PolyStringMessage.newBuilder()
                                                                        .setOrig("newLabel")
                                                                )
                                                        )
                                                )
                                                .build()
                                        )
                                        .putValue("value", ItemMessage.newBuilder()
                                                .setProperty(PrismPropertyMessage.newBuilder()
                                                        .addValues(PrismPropertyValueMessage.newBuilder()
                                                                .setString("newValue")
                                                        )
                                                )
                                                .build()
                                        )
                                )
                        )
                )
                .build();

        ModifyObjectResponse addRowRes = stub.modifyObject(addRowReq);

        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .setRelationalValueSearchQuery(RelationalValueSearchQueryMessage.newBuilder()
                        .setColumn(QNameMessage.newBuilder()
                                .setLocalPart("key"))
                        .setSearchType(RelationalValueSearch.EXACT)
                        .setSearchValue("newKey")
                )
                .build();

        res = stub.getLookupTable(req);

        rows = res.getResult().getRowList();
        assertEquals(1, rows.size());

        assertEquals("newKey", rows.get(0).getKey());
        assertEquals("newLabel", rows.get(0).getLabel().getOrig());
        assertEquals("newValue", rows.get(0).getValue());
        assertTrue(rows.get(0).getId() != 0);
        assertTrue(rows.get(0).hasLastChangeTimestamp());

        // update row
        long id = rows.get(0).getId();
        ModifyObjectRequest updateRowReq = ModifyObjectRequest.newBuilder()
                .setOid("00000000-0000-0000-0000-000000000200")
                .setObjectType(DefaultObjectType.LOOKUP_TABLE_TYPE)
                .addModifications(ItemDeltaMessage.newBuilder()
                        .setPath("row/[" + id + "]/label")
                        .addPrismValuesToReplace(PrismValueMessage.newBuilder()
                                .setProperty(PrismPropertyValueMessage.newBuilder()
                                        .setPolyString(PolyStringMessage.newBuilder()
                                                .setOrig("modified")
                                        )
                                )
                        )
                )
                .addOptions("raw")
                .build();

        ModifyObjectResponse updateRowRes = stub.modifyObject(updateRowReq);

        res = stub.getLookupTable(req);

        rows = res.getResult().getRowList();
        assertEquals(1, rows.size());

        assertEquals("newKey", rows.get(0).getKey());
        assertEquals("modified", rows.get(0).getLabel().getOrig());
        assertEquals("newValue", rows.get(0).getValue());
        assertTrue(rows.get(0).getId() != 0);
        assertTrue(rows.get(0).hasLastChangeTimestamp());

        // delete row by id
        ModifyObjectRequest deleteRowReq = ModifyObjectRequest.newBuilder()
                .setOid("00000000-0000-0000-0000-000000000200")
                .setObjectType(DefaultObjectType.LOOKUP_TABLE_TYPE)
                .addModifications(ItemDeltaMessage.newBuilder()
                        .setPath("row")
                        .addPrismValuesToDelete(PrismValueMessage.newBuilder()
                                .setContainer(PrismContainerValueMessage.newBuilder()
                                        .setId(id)
                                )
                        )
                )
                .addOptions("raw")
                .build();

        ModifyObjectResponse deleteRowRes = stub.modifyObject(deleteRowReq);

        res = stub.getLookupTable(req);

        rows = res.getResult().getRowList();
        assertEquals(0, rows.size());
    }

    @Test
    void getSequenceCounter() throws Exception {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // create new SequenceType
        AddObjectRequest newSeqReq = AddObjectRequest.newBuilder()
                .setType(QNameMessage.newBuilder().setLocalPart("SequenceType"))
                .setObject(PrismContainerMessage.newBuilder()
                        .addValues(PrismContainerValueMessage.newBuilder()
                                .putValue("name", ItemMessage.newBuilder()
                                        .setProperty(PrismPropertyMessage.newBuilder()
                                                .addValues(PrismPropertyValueMessage.newBuilder()
                                                        .setPolyString(PolyStringMessage.newBuilder()
                                                                .setOrig("Unix UID numbers")
                                                        )
                                                )
                                        ).build()
                                )
                                .putValue("counter", ItemMessage.newBuilder()
                                        .setProperty(PrismPropertyMessage.newBuilder()
                                                .addValues(PrismPropertyValueMessage.newBuilder()
                                                        .setLong(LongMessage.newBuilder()
                                                                .setValue(1001)
                                                        )
                                                )
                                        )
                                        .build()
                                )
                                .putValue("maxUnusedValues", ItemMessage.newBuilder()
                                        .setProperty(PrismPropertyMessage.newBuilder()
                                                .addValues(PrismPropertyValueMessage.newBuilder()
                                                        .setLong(LongMessage.newBuilder()
                                                                .setValue(10)
                                                        )
                                                )
                                        )
                                        .build()
                                )
                        )
                )
                .build();

        AddObjectResponse addSeqRes = stub.addObject(newSeqReq);

        // increment
        GetSequenceCounterRequest req = GetSequenceCounterRequest.newBuilder()
                .setName("Unix UID numbers")
                .build();

        GetSequenceCounterResponse res = stub.getSequenceCounter(req);

        assertEquals(1001, res.getResult());

        // increment
        res = stub.getSequenceCounter(req);

        assertEquals(1002, res.getResult());
    }
}