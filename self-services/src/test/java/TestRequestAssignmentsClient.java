import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

public class TestRequestAssignmentsClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:Test5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        RequestAssignmentsRequest request = RequestAssignmentsRequest.newBuilder()
                .addOids("04425606-5089-4b9f-8f19-95c7408a61b9")
                .addOids("430b7dd0-e9f8-4ee3-9642-46ab0f7860a6")
                .setComment("Request assignments from grpc")
                .addAssignments(
                        AssignmentMessage.newBuilder()
                                .setTargetRef(
                                        ReferenceMessage.newBuilder()
                                                .setObjectType(DefaultObjectType.ROLE_TYPE)
                                                .setName(PolyStringMessage.newBuilder().setOrig("ProjUser"))
                                )
                )
                .build();

        try {
            RequestAssignmentsResponse response = stub.requestAssignments(request);
            System.out.println(response);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() != Status.INVALID_ARGUMENT.getCode()) {
                throw e;
            }
            Metadata metadata = e.getTrailers();
            if (metadata != null) {
                PolicyError policyError = metadata.get(SelfServiceResource.PolicyErrorMetadataKey);

                System.out.println(policyError);
            }
        }
    }
}
