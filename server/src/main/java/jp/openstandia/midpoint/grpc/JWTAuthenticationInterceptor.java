package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.IssuerUriCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Conditional(IssuerUriCondition.class)
public class JWTAuthenticationInterceptor extends AbstractGrpcAuthenticationInterceptor {

    private static final Trace LOGGER = TraceManager.getTrace(MidPointApplication.class);

    private static final String TYPE = "Bearer";


    @Autowired
    JwtDecoder jwtDecoder;

    @Value("${spring.security.oauth2.resourceserver.validIssuer}")
    String validIssuer;

    @Value("${spring.security.oauth2.resourceserver.validAudience}")
    String validAudience;

    public String getType() {
        return TYPE;
    }

    @Override
    public Authentication authenticate(ConnectionEnvironment connEnv, Task task, String header) {
        String token = extractHeader(header, TYPE);

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtValidationException e) {
            StatusRuntimeException error = Status.UNAUTHENTICATED
                    .withDescription(INVALID_TOKEN)
                    .withCause(e)
                    .asRuntimeException();
            throw error;
        } catch (RuntimeException e) {
            StatusRuntimeException error = Status.INTERNAL
                    .withDescription(INTERNAL_ERROR)
                    .withCause(e)
                    .asRuntimeException();
            throw error;
        }

        // Validate issuer
        if (!jwt.getIssuer().toString().equalsIgnoreCase(validIssuer)) {
            StatusRuntimeException e = Status.UNAUTHENTICATED
                    .withDescription(INVALID_TOKEN)
                    .asRuntimeException();
            throw e;
        }

        // Validate audience
        if (jwt.getAudience().stream().noneMatch(x -> x.equals(validAudience))) {
            StatusRuntimeException e = Status.UNAUTHENTICATED
                    .withDescription(INVALID_TOKEN)
                    .asRuntimeException();
            throw e;
        }

        // Audit clientId or sub of JWT
        NodeType client = new NodeType();
        if (jwt.getClaims().containsKey("clientId")) {
            client.setName(PolyStringType.fromOrig(jwt.getClaimAsString("clientId")));
        } else {
            client.setName(PolyStringType.fromOrig(jwt.getSubject()));
        }
        securityHelper.auditLoginSuccess(client, connEnv);

        return new BearerTokenAuthenticationToken(token);
    }

    @Override
    protected void authorizeClient(Authentication auth, ConnectionEnvironment connEnv, Task task) {
        // Do nothing.
        // Currently it supports client credentials flow only.
        // It means the bearer token doesn't bound to an user in midpoint.
        // TODO should check scope of JWT?
    }

    @Override
    protected Authentication switchToUser(Authentication auth, Metadata headers, boolean runPrivileged, ConnectionEnvironment connEnv, Task task) {
        String switchUser = headers.get(Constant.SwitchToPrincipalMetadataKey);
        String switchUserByName = headers.get(Constant.SwitchToPrincipalByNameMetadataKey);

        // Find proxy user
        PrismObject<FocusType> authorizedUser;
        if (StringUtils.isNotBlank(switchUser)) {
            authorizedUser = findByOid(switchUser, task);
        } else if (StringUtils.isNotBlank(switchUserByName)) {
            authorizedUser = findByUsername(switchUserByName, task);
        } else {
            // JWT authentication needs proxy user always!
            StatusRuntimeException e = Status.UNAUTHENTICATED
                    .withDescription(INVALID_REQUEST)
                    .asRuntimeException();
            throw e;
        }

        // Don't check authorization of client user because the bearer token doesn't bound to an user in midpoint.

        return authenticateSwitchUser(authorizedUser, runPrivileged, connEnv, task);
    }
}
