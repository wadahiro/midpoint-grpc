package com.evolveum.midpoint.web.boot;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.lognet.springboot.grpc.autoconfigure.GRpcAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@ImportAutoConfiguration(classes = {
        GRpcAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
})
@Configuration
public class GrpcServerConfig {

    private static final Trace LOGGER = TraceManager.getTrace(GrpcServerConfig.class);

    static {
        System.out.println("GrpcServerConfig loaded");
        LOGGER.info("GrpcServerConfig loaded");
    }

    private ApplicationContext applicationContext;

}
