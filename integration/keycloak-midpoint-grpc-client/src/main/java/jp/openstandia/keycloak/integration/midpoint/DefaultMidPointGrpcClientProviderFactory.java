package jp.openstandia.keycloak.integration.midpoint;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static jp.openstandia.keycloak.integration.midpoint.MidPointGrpcClientConfigurator.*;

public class DefaultMidPointGrpcClientProviderFactory implements MidPointGrpcClientProviderFactory {

    private static final Logger logger = Logger.getLogger(DefaultMidPointGrpcClientProviderFactory.class);

    // channel is thread-safe
    // channel is established per a realm
    private static Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    protected Config.Scope scope;
    private String clientId;

    @Override
    public MidPointGrpcClientProvider create(KeycloakSession session) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new RuntimeException("Can't detect the current realm for midpoint gRPC");
        }

        ClientModel client = realm.getClientByClientId(clientId);
        if (client == null) {
            throw new RuntimeException("No client " + clientId + " for midpoint gRPC");
        }

        ManagedChannel channel = channels.get(realm.getName());

        if (channel == null) {
            String server = client.getAttribute(CONFIG_SERVER);
            if (server == null || server.isEmpty()) {
                throw new RuntimeException("No client " + clientId + " configuration for midpoint gRPC server");
            }

            String portStr = client.getAttribute(CONFIG_PORT);
            int port = 6565;
            try {
                if (portStr != null && !portStr.isEmpty()) {
                    port = Integer.parseInt(portStr);
                }
            } catch (NumberFormatException e) {
            }

            // TODO should use own thread pool? Default is unlimited threads.
            channel = ManagedChannelBuilder.forAddress(server, port)
                    .usePlaintext()
                    .build();

            channels.put(realm.getName(), channel);
        }

        String clientId = client.getAttribute(CONFIG_CLIENT_ID);
        String clientSecret = client.getAttribute(CONFIG_CLIENT_SECRET);

        return new DefaultMidPointGrpcClientProvider(channel, clientId, clientSecret);
    }

    @Override
    public void init(Config.Scope scope) {
        logger.info("Initializing midpoint-grpc-client");

        this.scope = scope;

        clientId = scope.get("client-id");

        logger.info("Initialized midpoint-grpc-client");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
        logger.info("Stopping midPoint gRPC client");

        channels.entrySet().stream().forEach(c -> {
            try {
                logger.infof("Stopping midPoint gRPC client for realm %s", c.getKey());
                c.getValue().shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Interrupted while shutdown process", e);
            }
        });

        logger.info("Stopped midPoint gRPC client");
    }

    @Override
    public String getId() {
        return "default";
    }
}
