package jp.openstandia.keycloak.integration.midpoint;

import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractClientAuthenticator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.*;

public class MidPointGrpcClientConfigurator extends AbstractClientAuthenticator {

    static String CONFIG_SERVER = "midpoint.grpc.server";
    static String CONFIG_PORT = "midpoint.grpc.port";
    static String CONFIG_CLIENT_ID = "midpoint.grpc.client.id";
    static String CONFIG_CLIENT_SECRET = "midpoint.grpc.client.secret";

    @Override
    public void authenticateClient(ClientAuthenticationFlowContext context) {
        context.attempted();
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "midpoint-grpc-client-configurator";
    }

    @Override
    public String getDisplayType() {
        return "MidPoint gRPC Client Configurator";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{ AuthenticationExecutionModel.Requirement.DISABLED};
    }

    @Override
    public List<ProviderConfigProperty> getConfigPropertiesPerClient() {
        List<ProviderConfigProperty> list = new ArrayList<>();

        ProviderConfigProperty prop1 = new ProviderConfigProperty();
        prop1.setName(CONFIG_SERVER);
        prop1.setType(ProviderConfigProperty.STRING_TYPE);
        prop1.setLabel("MidPoint gRPC server");
        prop1.setHelpText("Set server address for midPoint gRPC server.");

        ProviderConfigProperty prop2 = new ProviderConfigProperty();
        prop2.setName(CONFIG_PORT);
        prop2.setType(ProviderConfigProperty.STRING_TYPE);
        prop2.setLabel("MidPoint gRPC port");
        prop2.setHelpText("Set port number for midPoint gRPC server.");

        ProviderConfigProperty prop3 = new ProviderConfigProperty();
        prop3.setName(CONFIG_CLIENT_ID);
        prop3.setType(ProviderConfigProperty.STRING_TYPE);
        prop3.setLabel("MidPoint gRPC client id");
        prop3.setHelpText("Set client id for midPoint gRPC server.");

        ProviderConfigProperty prop4 = new ProviderConfigProperty();
        prop4.setName(CONFIG_CLIENT_SECRET);
        prop4.setType(ProviderConfigProperty.STRING_TYPE);
        prop4.setLabel("MidPoint gRPC client secret");
        prop4.setHelpText("Set client secret for midPoint gRPC server.");

        list.add(prop1);
        list.add(prop2);
        list.add(prop3);
        list.add(prop4);

        return list;
    }

    @Override
    public Map<String, Object> getAdapterConfiguration(ClientModel clientModel) {
        return Collections.emptyMap();
    }

    @Override
    public Set<String> getProtocolAuthenticatorMethods(String loginProtocol) {
            return Collections.emptySet();
    }

    @Override
    public String getHelpText() {
        return "Dummy client authenticator for midPoint gRPC client configuration";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return new LinkedList<>();
    }
}
