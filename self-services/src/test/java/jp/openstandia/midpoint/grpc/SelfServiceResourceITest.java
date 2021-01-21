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
//        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");

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
                        .setRoleType("testRole")
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
        assertEquals("testRole", res2.getResult().getRoleType());

        // Search
        SearchRolesResponse res3 = stub.searchRoles(SearchRequest.newBuilder()
                .setQuery(QueryMessage.newBuilder()
                        .setFilter(ObjectFilterMessage.newBuilder()
                                .setEq(FilterEntryMessage.newBuilder()
                                        .setFullPath("roleType")
                                        .setValue("testRole"))))
                .build());

        assertEquals(1, res3.getNumberOfAllResults());
        assertEquals("testRole", res3.getResults(0).getRoleType());

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
                        .addOrgType("testOrg")
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
                                        .setFullPath("orgType")
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
                        .addServiceType("testService")
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
                                        .setFullPath("serviceType")
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
}