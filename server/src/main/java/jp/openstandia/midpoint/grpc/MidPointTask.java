package jp.openstandia.midpoint.grpc;

public interface MidPointTask<T> {
    T run(MidPointTaskContext task) throws Exception;
}
