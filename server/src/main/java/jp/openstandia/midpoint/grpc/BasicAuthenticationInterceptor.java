package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.model.api.AuthenticationEvaluator;
import com.evolveum.midpoint.model.api.context.PasswordAuthenticationContext;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.HttpConnectionInformation;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.boot.GrpcServerConfiguration;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import io.grpc.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;

import static jp.openstandia.midpoint.grpc.MidPointGrpcService.CHANNEL_GRPC_SERVICE_URI;
import static jp.openstandia.midpoint.grpc.MidPointGrpcService.OPERATION_GRPC_SERVICE;

@Component
public class BasicAuthenticationInterceptor implements ServerInterceptor {

    private static final Trace LOGGER = TraceManager.getTrace(MidPointApplication.class);

    @Autowired
    private PrismContext prismContext;

    @Autowired
    private transient AuthenticationEvaluator<PasswordAuthenticationContext> passwordAuthenticationEvaluator;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        // How to get remote address/port
        // https://github.com/grpc/grpc-java/blob/30b59885b7496b53eb17f64ba1d822c2d9a6c69a/interop-testing/src/main/java/io/grpc/testing/integration/AbstractInteropTest.java#L1627-L1639

        final String remoteInetSocketString = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
        final String localInetSocketString = call.getAttributes().get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR).toString();

        HttpConnectionInformation connection = new HttpConnectionInformation();
        connection.setRemoteHostAddress(remoteInetSocketString);
        connection.setLocalHostName(localInetSocketString);

        LOGGER.trace("Authenticating to gRPC service");

        // We need to create task before attempting authentication. Task ID is also a session ID.
        final Task task = GrpcServerConfiguration.getApplication().getTaskManager().createTaskInstance(OPERATION_GRPC_SERVICE);
        task.setChannel(CHANNEL_GRPC_SERVICE_URI);

        connection.setSessionId(task.getTaskIdentifier());
        ConnectionEnvironment connEnv = new ConnectionEnvironment(CHANNEL_GRPC_SERVICE_URI, connection);

        // Client authentication
        String token = headers.get(Constant.AuthorizationMetadataKey);
        Authentication auth = authenticate(connEnv, token);

        UserType user = ((MidPointPrincipal) auth.getPrincipal()).getUser();
        task.setOwner(user.asPrismObject());

        // Switch user authentication
        String switchUser = headers.get(Constant.SwitchToPrincipalMetadataKey);
        OperationResult result = task.getResult();
        if (StringUtils.isNotBlank(switchUser)) {
            auth = authenticateSwitchUser(switchUser, result);
            task.setOwner(((MidPointPrincipal) auth.getPrincipal()).getUser().asPrismObject());
            // TODO Authorize
//                if (!authorizeUser(AuthorizationConstants.AUTZ_REST_PROXY_URL, user, authorizedUser, enteredUsername, connEnv, requestCtx)){
//                    return;
//                }
        }

        Context ctx = Context.current()
                .withValue(ServerConstant.ConnectionContextKey, connection)
                .withValue(ServerConstant.ConnectionEnvironmentContextKey, connEnv)
                .withValue(ServerConstant.TaskContextKey, task)
                .withValue(ServerConstant.AuthenticationContextKey, auth)
                .withValue(ServerConstant.AuthorizationHeaderContextKey, token);

        ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> serverCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (!status.isOk()) {
                    LOGGER.error("Error in calling gRPC service. status={}, metadata={}", status, trailers);
                }
                // TODO Check REST API implementation
//                task.setOwner(user.asPrismObject());
                finishRequest(task, connEnv);
                super.close(status, trailers);
            }
        };

        return Contexts.interceptCall(ctx, serverCall, headers, next);
    }

    private PrismObject<UserType> findByUsername(String username, OperationResult result) {
        try {
            PolyString usernamePoly = new PolyString(username);
            ObjectQuery query = ObjectQueryUtil.createNormNameQuery(usernamePoly, prismContext);
            LOGGER.trace("Looking for user, query:\n" + query.debugDump());

            List<PrismObject<UserType>> list = GrpcServerConfiguration.getApplication().getRepositoryService().searchObjects(UserType.class, query, null, result);
            LOGGER.trace("Users found: {}.", list.size());
            if (list.size() != 1) {
                throw Status.NOT_FOUND
                        .withDescription("Not found user")
                        .asRuntimeException();
            }
            return list.get(0);
        } catch (SchemaException e) {
            LOGGER.trace("Exception while authenticating user identified with '{}' to gRPC service: {}", username, e.getMessage(), e);
            throw Status.UNAUTHENTICATED
                    .withDescription(e.getMessage())
                    .asRuntimeException();
        }
    }

    protected Authentication authenticate(ConnectionEnvironment connEnv, String header) {
        if (header == null || !header.toLowerCase().startsWith("basic ")) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Not authorization header")
                    .asRuntimeException();
        }
        try {
            String[] tokens = extractAndDecodeHeader(header);

            UsernamePasswordAuthenticationToken authToken = authenticateUser(connEnv, tokens[0], tokens[1]);
            return authToken;

        } catch (UnsupportedEncodingException e) {
            StatusRuntimeException exception = Status.INTERNAL
                    .withDescription("internal_error")
                    .withCause(e)
                    .asRuntimeException();
            throw exception;
        }
    }

    private String[] extractAndDecodeHeader(String header) throws UnsupportedEncodingException {
        byte[] base64Token = header.substring(6).getBytes("UTF-8");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Token);
        } catch (IllegalArgumentException e) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Failed to decode basic authentication token")
                    .asRuntimeException();
        }

        String token = new String(decoded, "UTF-8");

        int delim = token.indexOf(":");

        if (delim == -1) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Invalid basic authentication token")
                    .asRuntimeException();
        }
        return new String[]{token.substring(0, delim), token.substring(delim + 1)};
    }

    private UsernamePasswordAuthenticationToken authenticateUser(ConnectionEnvironment connEnv, String username, String password) {
        try {
            UsernamePasswordAuthenticationToken token = passwordAuthenticationEvaluator.authenticate(connEnv, new PasswordAuthenticationContext(username, password));
            return token;
        } catch (AuthenticationException ex) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Unauthorized")
                    .asRuntimeException();
        }
    }

    protected Authentication authenticateSwitchUser(String username, OperationResult result) {
        try {
            PrismObject<UserType> user = findByUsername(username, result);
            MidPointPrincipal principal = GrpcServerConfiguration.getApplication().getSecurityContextManager().getUserProfileService().getPrincipal(user, null, result);
            PreAuthenticatedAuthenticationToken token = new PreAuthenticatedAuthenticationToken(principal, null, principal.getAuthorities());
            return token;
        } catch (Exception e) {
            StatusRuntimeException exception = Status.INTERNAL
                    .withDescription("internal_error")
                    .asRuntimeException();
            throw exception;
        }
    }

    protected void finishRequest(Task task, ConnectionEnvironment connEnv) {
        task.getResult().computeStatus();
        connEnv.setSessionIdOverride(task.getTaskIdentifier());
        GrpcServerConfiguration.getSecurityHelper().auditLogout(connEnv, task);
    }
}
