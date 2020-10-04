package jp.openstandia.keycloak.integration.midpoint;

import io.grpc.StatusRuntimeException;
import jp.openstandia.midpoint.grpc.SelfServiceResourceGrpc;
import org.keycloak.provider.Provider;

import java.util.Optional;

public interface MidPointGrpcClientProvider extends Provider {

    SelfServiceResourceGrpc.SelfServiceResourceBlockingStub getSelfServiceResource(String username);

    Optional<MidPointPolicyFormMessage> handlePolicyError(StatusRuntimeException e);

}
