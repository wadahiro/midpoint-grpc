package jp.openstandia.keycloak.integration.midpoint;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import jp.openstandia.midpoint.grpc.SelfServiceResourceGrpc;
import org.keycloak.provider.Provider;

import java.util.Optional;
import java.util.function.Consumer;

public interface MidPointGrpcClientProvider extends Provider {

    SelfServiceResourceGrpc.SelfServiceResourceBlockingStub getSelfServiceResource();

    SelfServiceResourceGrpc.SelfServiceResourceBlockingStub getSelfServiceResource(String username);

    SelfServiceResourceGrpc.SelfServiceResourceBlockingStub getSelfServiceResourceByOid(String oid);

    SelfServiceResourceGrpc.SelfServiceResourceBlockingStub getSelfServiceResourceWithMetadataCustomizer(Consumer<Metadata> metadataCustomizer);

    Optional<MidPointPolicyFormMessage> handlePolicyError(StatusRuntimeException e);

}
