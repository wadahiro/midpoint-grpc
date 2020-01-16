package jp.openstandia.midpoint.grpc;

import io.grpc.Metadata;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class Constant {
    public static final Metadata.Key<String> AuthorizationMetadataKey =
            Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SwitchToPrincipalMetadataKey =
            Metadata.Key.of("Switch-To-Principal", ASCII_STRING_MARSHALLER);
}
