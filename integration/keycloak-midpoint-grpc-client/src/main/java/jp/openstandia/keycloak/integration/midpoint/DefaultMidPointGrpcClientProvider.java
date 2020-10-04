package jp.openstandia.keycloak.integration.midpoint;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;
import org.keycloak.models.utils.FormMessage;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultMidPointGrpcClientProvider implements MidPointGrpcClientProvider {

    private static final Metadata.Key<PolicyError> PolicyErrorMetadataKey = ProtoUtils.keyForProto(PolicyError.getDefaultInstance());

    private final ManagedChannel channel;
    private final String clientId;
    private final String clientSecret;

    public DefaultMidPointGrpcClientProvider(ManagedChannel channel, String clientId, String clientSecret) {
        this.channel = channel;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public SelfServiceResourceGrpc.SelfServiceResourceBlockingStub getSelfServiceResource(String username) {
        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        Metadata headers = new Metadata();
        // TODO Change authentication method with token-base
        // Need "Basic ..."

        final StringBuilder tmp = new StringBuilder();
        tmp.append(clientId);
        tmp.append(":");
        tmp.append(clientSecret);

        headers.put(Constant.AuthorizationMetadataKey, "Basic " +
                Base64.getEncoder().encodeToString(tmp.toString().getBytes(Charset.forName("UTF-8"))));
        headers.put(Constant.SwitchToPrincipalByNameMetadataKey, username);
        headers.put(Constant.RunPrivilegedMetadataKey, "true");

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub authStub = MetadataUtils.attachHeaders(stub, headers);

        return authStub;
    }

    @Override
    public Optional<MidPointPolicyFormMessage> handlePolicyError(StatusRuntimeException e) {
        List<FormMessage> messages = new ArrayList<>();

        PolicyError policyError = e.getTrailers().get(PolicyErrorMetadataKey);
        if (policyError == null) {
            return Optional.empty();
        }

        Message error = policyError.getMessage();
        SingleMessage top = error.getSingle();
        String errorMessageKey = top.getKey();

        for (Message sub : top.getArgsList()) {
            if (sub.hasSingle()) {
                SingleMessage subMsg = sub.getSingle();
                String subKey = subMsg.getKey();
                List<Message> args = subMsg.getArgsList();

                Object[] objects = args.stream()
                        .map(m -> m.getString())
                        .collect(Collectors.toList())
                        .toArray();

                messages.add(new FormMessage(null, subKey, objects));

            } else if (sub.hasList()) {
                for (Message sub2 : sub.getList().getMessageList()) {
                    SingleMessage subMsg = sub2.getSingle();
                    String subKey = subMsg.getKey();
                    List<Message> args = subMsg.getArgsList();

                    Object[] objects = args.stream()
                            .map(m -> m.getString())
                            .collect(Collectors.toList())
                            .toArray();

                    messages.add(new FormMessage(null, subKey, objects));
                }
            }
        }
        return Optional.of(new MidPointPolicyFormMessage(errorMessageKey, messages));
    }

    @Override
    public void close() {
    }
}
