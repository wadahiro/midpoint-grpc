package com.evolveum.midpoint.web.boot;

import com.evolveum.midpoint.gui.api.util.MidPointApplicationConfiguration;
import com.evolveum.midpoint.model.common.ConstantsManager;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.web.util.validation.MidpointFormValidatorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@Component
@ComponentScan(basePackages = {"jp.openstandia.midpoint.grpc"})
public class GrpcServerConfiguration implements MidPointApplicationConfiguration {
    static {
        System.out.println("GrpcServerConfiguration loaded");
    }

    private static final Trace LOGGER = TraceManager.getTrace(MidPointApplication.class);

    @Autowired
    MidpointFormValidatorRegistry midpointFormValidatorRegistry;

    @Autowired
    ConstantsManager constantsManager;

    @Autowired
    Protector protector;

    @Override
    public void init(MidPointApplication application) {
        LOGGER.info("GrpcServerConfiguration start");

        LOGGER.info("GrpcServerConfiguration end");
    }
}
