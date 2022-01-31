package jp.openstandia.midpoint.grpc;

public class GrpcServiceException extends RuntimeException {
    public GrpcServiceException(Exception e) {
        super(e);
    }
}
