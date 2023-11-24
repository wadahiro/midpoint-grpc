package jp.openstandia.keycloak.integration.midpoint;

import io.grpc.*;
import io.grpc.stub.AbstractStub;
import jp.openstandia.midpoint.grpc.Constant;
import jp.openstandia.midpoint.grpc.SelfServiceResourceGrpc;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMidPointGrpcClientProviderTest {

    @Test
    void getSelfServiceResource() throws NoSuchFieldException, IllegalAccessException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();
        DefaultMidPointGrpcClientProvider clientProvider = new DefaultMidPointGrpcClientProvider(channel, "test", "secret");

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = clientProvider.getSelfServiceResource();

        Metadata metadata = getMetadata(stub);

        System.out.println(metadata);

        assertEquals("Basic dGVzdDpzZWNyZXQ=", metadata.get(Constant.AuthorizationMetadataKey));
        assertEquals("true", metadata.get(Constant.RunPrivilegedMetadataKey));
        assertFalse(metadata.containsKey(Constant.SwitchToPrincipalMetadataKey));
        assertFalse(metadata.containsKey(Constant.SwitchToPrincipalByNameMetadataKey));
    }

    @Test
    void getSelfServiceResourceByUserName() throws NoSuchFieldException, IllegalAccessException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();
        DefaultMidPointGrpcClientProvider clientProvider = new DefaultMidPointGrpcClientProvider(channel, "test", "secret");

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = clientProvider.getSelfServiceResource("foo");

        Metadata metadata = getMetadata(stub);

        System.out.println(metadata);

        assertEquals("Basic dGVzdDpzZWNyZXQ=", metadata.get(Constant.AuthorizationMetadataKey));
        assertEquals("true", metadata.get(Constant.RunPrivilegedMetadataKey));
        assertEquals("foo", metadata.get(Constant.SwitchToPrincipalByNameMetadataKey));
        assertFalse(metadata.containsKey(Constant.SwitchToPrincipalMetadataKey));
    }

    @Test
    void getSelfServiceResourceByOid() throws NoSuchFieldException, IllegalAccessException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();
        DefaultMidPointGrpcClientProvider clientProvider = new DefaultMidPointGrpcClientProvider(channel, "test", "secret");

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = clientProvider.getSelfServiceResourceByOid("c9e302b8-0805-4831-96f3-57b13b10d01b");

        Metadata metadata = getMetadata(stub);

        System.out.println(metadata);

        assertEquals("Basic dGVzdDpzZWNyZXQ=", metadata.get(Constant.AuthorizationMetadataKey));
        assertEquals("true", metadata.get(Constant.RunPrivilegedMetadataKey));
        assertEquals("c9e302b8-0805-4831-96f3-57b13b10d01b", metadata.get(Constant.SwitchToPrincipalMetadataKey));
        assertFalse(metadata.containsKey(Constant.SwitchToPrincipalByNameMetadataKey));
    }

    protected static Metadata getMetadata(AbstractStub stub) throws NoSuchFieldException, IllegalAccessException {
        Channel interceptorChannel = stub.getChannel();
        ClientInterceptor clientInterceptor = getField(interceptorChannel, "interceptor", ClientInterceptor.class);
        Metadata metadata = getField(clientInterceptor, "extraHeaders", Metadata.class);

        return metadata;
    }

    protected static <T> T getField(Object obj, String field, Class<T> clazz) throws NoSuchFieldException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        T value = (T) f.get(obj);

        return value;
    }
}