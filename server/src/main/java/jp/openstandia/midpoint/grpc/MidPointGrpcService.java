package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.HttpConnectionInformation;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.lowagie.text.Meta;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;

import static com.evolveum.midpoint.schema.constants.SchemaConstants.NS_MODEL_CHANNEL;

public interface MidPointGrpcService {
    static final Trace LOGGER = TraceManager.getTrace(MidPointGrpcService.class);

    public static final String CLASS_DOT = MidPointGrpcService.class.getName() + ".";
    public static final String OPERATION_GRPC_SERVICE = CLASS_DOT + "grpcService";

    public static final QName CHANNEL_GRPC_SERVICE_QNAME = new QName(NS_MODEL_CHANNEL, "grpc");
    public static final String CHANNEL_GRPC_SERVICE_URI = QNameUtil.qNameToUri(CHANNEL_GRPC_SERVICE_QNAME);

    default <T> T runTask(MidPointTask<T> task) {
        Authentication auth = ServerConstant.AuthenticationContextKey.get();
        SecurityContextHolder.getContext().setAuthentication(auth);

        HttpConnectionInformation connection = ServerConstant.ConnectionContextKey.get();
        ConnectionEnvironment connEnv = ServerConstant.ConnectionEnvironmentContextKey.get();
        Task t = ServerConstant.TaskContextKey.get();

        MidPointPrincipal principal = (MidPointPrincipal) auth.getPrincipal();

        try {
            T result = task.run(new MidPointTaskContext(connection, connEnv, t, auth, principal));
            return result;
        } catch (ObjectNotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription(e.getErrorTypeMessage())
                    .withCause(e)
                    .asRuntimeException();
        } catch (PolicyViolationException e) {
            Metadata metadata = handlePolicyViolationException(e);

            throw Status.INVALID_ARGUMENT
                    .withDescription(e.getErrorTypeMessage())
                    .withCause(e)
                    .asRuntimeException(metadata);
        } catch (SchemaException | ExpressionEvaluationException e) {
            throw Status.INVALID_ARGUMENT
                    .withDescription(e.getErrorTypeMessage())
                    .withCause(e)
                    .asRuntimeException();
        } catch (AuthorizationException e) {
            throw Status.PERMISSION_DENIED
                    .withDescription(e.getErrorTypeMessage())
                    .withCause(e)
                    .asRuntimeException();
        } catch (SecurityViolationException e) {
            throw Status.PERMISSION_DENIED
                    .withDescription(e.getErrorTypeMessage())
                    .withCause(e)
                    .asRuntimeException();
        } catch (ObjectAlreadyExistsException e) {
            throw Status.ALREADY_EXISTS
                    .withDescription(e.getErrorTypeMessage())
                    .withCause(e)
                    .asRuntimeException();
        } catch (StatusRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(null);
        }
    }

    default Metadata handlePolicyViolationException(PolicyViolationException e) {
        return new Metadata();
    }
}

