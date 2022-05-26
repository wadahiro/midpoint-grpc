package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.model.api.AuthenticationEvaluator;
import com.evolveum.midpoint.model.api.context.PasswordAuthenticationContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import io.grpc.Metadata;
import io.grpc.Status;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(JWTAuthenticationInterceptor.class)
public class BasicAuthenticationInterceptor extends AbstractGrpcAuthenticationInterceptor {

    private static final Trace LOGGER = TraceManager.getTrace(BasicAuthenticationInterceptor.class);
    private static final String TYPE = "Basic";

    @Autowired
    transient AuthenticationEvaluator<PasswordAuthenticationContext> passwordAuthenticationEvaluator;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Authentication authenticate(ConnectionEnvironment connEnv, Task task, String header) {
        String[] tokens = extractAndDecodeBasicAuthzHeader(header);

        Authentication authToken = authenticateUser(connEnv, tokens[0], tokens[1]);

        return authToken;
    }

    @Override
    protected void authorizeClient(Authentication auth, ConnectionEnvironment connEnv, Task task) {
        MidPointPrincipal p = (MidPointPrincipal) auth.getPrincipal();
        UserType user = p.getUser();
        authorizeUser(auth, AuthorizationConstants.AUTZ_REST_ALL_URL, user, null, connEnv);
    }

    @Override
    protected Authentication switchToUser(Authentication auth, Metadata headers, ConnectionEnvironment connEnv, Task task) {
        String switchUser = headers.get(Constant.SwitchToPrincipalMetadataKey);
        String switchUserByName = headers.get(Constant.SwitchToPrincipalByNameMetadataKey);

        // Find proxy user
        PrismObject<UserType> authorizedUser;
        if (StringUtils.isNotBlank(switchUser)) {
            authorizedUser = findByOid(switchUser, task);
        } else if (StringUtils.isNotBlank(switchUserByName)) {
            authorizedUser = findByUsername(switchUserByName, task);
        } else {
            // No switching
            return auth;
        }

        // Authorization proxy user
        UserType client = ((MidPointPrincipal) auth.getPrincipal()).getUser();
        authorizeUser(auth, AuthorizationConstants.AUTZ_REST_PROXY_URL, client, authorizedUser, connEnv);

        return authenticateUser(authorizedUser, connEnv, task);
    }

    protected String[] extractAndDecodeBasicAuthzHeader(String header) {
        String token = extractAndDecodeHeader(header, "basic");

        int delim = token.indexOf(":");

        if (delim == -1) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Invalid basic authentication token")
                    .asRuntimeException();
        }
        return new String[]{token.substring(0, delim), token.substring(delim + 1)};
    }

    private UsernamePasswordAuthenticationToken authenticateUser(ConnectionEnvironment connEnv, String username, String password) {
        LOGGER.debug("Start authenticateUser: {}", username);
        try {
            // login session is recorded here
            // TODO Use custom evaluator here because it takes several tens of ms
            UsernamePasswordAuthenticationToken token = passwordAuthenticationEvaluator.authenticate(connEnv, new PasswordAuthenticationContext(username, password));
            return token;
        } catch (AuthenticationException ex) {
            LOGGER.info("Not authenticated. user: {}, reason: {}", username, ex.getMessage());
            throw Status.UNAUTHENTICATED
                    .withDescription("invalid_token")
                    .withCause(ex)
                    .asRuntimeException();
        } finally {
            LOGGER.debug("End authenticateUser: {}", username);
        }
    }
}
