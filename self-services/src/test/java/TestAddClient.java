import com.evolveum.midpoint.schema.constants.RelationTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import javax.management.relation.RelationType;
import javax.management.relation.Role;
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
                                                                .setName(PolyStringMessage.newBuilder().setOrig("ProjUser"))
                                                                .setType(
                                                                        QNameMessage.newBuilder()
//                                                                                .setNamespaceURI(RoleType.COMPLEX_TYPE.getNamespaceURI())
                                                                                .setLocalPart(RoleType.COMPLEX_TYPE.getLocalPart()))
                                                                .setRelation(QNameMessage.newBuilder()
//                                                                        .setNamespaceURI(SchemaConstants.ORG_MANAGER.getNamespaceURI())
                                                                        .setLocalPart(SchemaConstants.ORG_MANAGER.getLocalPart()))
                                                )
                                                .putExtension("manager",
                                                        ExtensionMessage.newBuilder()
//                                                                .setNamespaceURI("http://test.aidaas.cloud/my")
                                                                .setIsSingleValue(true)
                                                                .addValue(
                                                                        ExtensionValue.newBuilder().setRef(
                                                                                ReferenceMessage.newBuilder()
                                                                                        .setName(PolyStringMessage.newBuilder().setOrig("test"))
                                                                                        .setType(
                                                                                                QNameMessage.newBuilder()
//                                                                                                        .setNamespaceURI(UserType.COMPLEX_TYPE.getNamespaceURI())
                                                                                                        .setLocalPart(UserType.COMPLEX_TYPE.getLocalPart()))
                                                                        ).build()
                                                                )
                                                                .build()
                                                )
                                )
                                .putExtension("singleString",
                                        ExtensionMessage.newBuilder()
//                                                .setNamespaceURI("http://test.example.com/my")
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
