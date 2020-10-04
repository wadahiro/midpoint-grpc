package jp.openstandia.keycloak.integration.midpoint;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class MidPointGrpcClientSPI implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "midpoint-grpc-client";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return MidPointGrpcClientProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return MidPointGrpcClientProviderFactory.class;
    }
}