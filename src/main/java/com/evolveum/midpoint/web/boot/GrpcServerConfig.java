package com.evolveum.midpoint.web.boot;

import io.grpc.ServerInterceptor;
import io.grpc.util.TransmitStatusRuntimeExceptionInterceptor;
import org.lognet.springboot.grpc.GRpcGlobalInterceptor;
import org.lognet.springboot.grpc.autoconfigure.GRpcAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ImportAutoConfiguration(classes = {
        GRpcAutoConfiguration.class
})
@Configuration
public class GrpcServerConfig {
    static {
        System.out.println("CustomConfig loaded");
    }

    @Bean
    @GRpcGlobalInterceptor
    public ServerInterceptor globalInterceptor() {
        return TransmitStatusRuntimeExceptionInterceptor.instance();
    }
}
