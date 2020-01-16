package com.evolveum.midpoint.web.boot;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
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

    private static final Trace LOGGER = TraceManager.getTrace(GrpcServerConfig.class);

    static {
        System.out.println("GrpcServerConfig loaded");
        LOGGER.info("GrpcServerConfig loaded");
    }

    @Bean
    @GRpcGlobalInterceptor
    public ServerInterceptor exceptionInterceptor() {
        return TransmitStatusRuntimeExceptionInterceptor.instance();
    }
}
