import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import static org.springframework.http.HttpMethod.POST;

public class TestJWTAuthClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = clientAuthenticate("midpoint-grpc-client", "31cc0ded-3155-4a63-8ee3-415698789672",
                "http://localhost:18080/auth/realms/master/protocol/openid-connect/token");

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Bearer " + token);
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, "test");

        stub = MetadataUtils.attachHeaders(stub, headers);

        ModifyProfileRequest request = ModifyProfileRequest.newBuilder()
                .addModifications(
                        UserItemDeltaMessage.newBuilder()
                                .setUserTypePath(DefaultUserTypePath.F_FAMILY_NAME)
                                .addValuesToReplace("Bar")
                                .build()
                )
                .build();

        stub.modifyProfile(request);
    }

    public static String clientAuthenticate(String clientId, String clientSecret, String tokenEndpoint) {
        // TODO Use client_jwt authentication for security
        RestTemplate tokenTemplate = new RestTemplate();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "client_credentials");

        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/x-www-form-urlencoded");

        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> res = tokenTemplate.exchange(
                tokenEndpoint, POST, entity, String.class);

        Map<String, Object> map = new BasicJsonParser().parseMap(res.getBody());

        System.out.println("Got access token for midpoint-grpc.");

        return (String) map.get("access_token");
    }
}
