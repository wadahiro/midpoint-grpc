package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.HttpConnectionInformation;
import com.evolveum.midpoint.task.api.Task;
import io.grpc.Context;
import org.springframework.security.core.Authentication;

public class ServerConstant {
    public static final Context.Key<HttpConnectionInformation> ConnectionContextKey = Context.key("connection");
    public static final Context.Key<ConnectionEnvironment> ConnectionEnvironmentContextKey = Context.key("connectionEnvironment");
    public static final Context.Key<Task> TaskContextKey = Context.key("midpointTask");
    public static final Context.Key<Authentication> AuthenticationContextKey = Context.key("authentication");
    public static final Context.Key<String> AuthorizationHeaderContextKey = Context.key("authorization");
}
